"""
weather.py — Open-Meteo client for SolarPredict (15-minute granularity).

ONE Open-Meteo request returns the full 14-day forecast at 15-minute resolution.
The fields the model needs at high time resolution come from `minutely_15`;
slow-moving fields (pressure, clouds, precipitation) come from `hourly` and are
broadcast across the 4 quarter-hour slots inside each hour. `daily` provides
sunrise/sunset for the dayLength feature.

Mapping (CLAUDE.md is authoritative — keep this in sync):
    GHI         <- minutely_15.shortwave_radiation     (W/m²)
    temp        <- minutely_15.temperature_2m           (°C)
    humidity    <- minutely_15.relative_humidity_2m     (%)
    wind_speed  <- minutely_15.wind_speed_10m           (m/s)
    pressure    <- hourly.surface_pressure              (hPa)
    clouds_all  <- hourly.cloud_cover                   (%)
    rain_1h     <- hourly.rain                          (mm of last hour)
    snow_1h     <- hourly.snowfall                      (cm × 10 -> mm)
    dayLength   <- (sunset - sunrise), in MINUTES       (see note below)
    hour        <- hour-of-day extracted from each 15-min timestamp (0-23)

"""

from datetime import date, datetime, timedelta
import time
from typing import Any

import requests


OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"

# 10s connect / 60s read. Single request pulls 14 days × 96 quarter-hours of
# data, and Open-Meteo's free tier can be slow during peak hours - the prior
# 15s read was tripping intermittently for 7- and 14-day requests.
HTTP_TIMEOUT = (10, 60)

# Retry transient failures (network blip, 5xx, slow upstream). Three attempts
# with 0.5s/1s/2s backoff covers the typical Open-Meteo flake window without
# blowing past the function timeout.
MAX_RETRIES = 3
RETRY_BACKOFF_S = (0.5, 1.0, 2.0)

# Hard cap mirroring validators.MAX_FORECAST_DAYS so even a buggy direct
# caller (tests, scripts) cannot accidentally request 30 days.
MAX_FORECAST_DAYS = 14


class WeatherFetchError(Exception):
    """Raised when Open-Meteo is unreachable or returns malformed data."""


# ===========================================================================
# In-process forecast cache
# ===========================================================================
#
# Open-Meteo's free tier is generous but easy to exhaust during testing
# (e.g. when the user runs predictions back-to-back, or experiments with
# the same panel + several time ranges). One Open-Meteo response covers
# the full 14-day forecast at 15-minute resolution, so we cache the
# parsed rows by rounded (lat, lng) for a few minutes and serve repeat
# calls from memory. This dramatically reduces upstream traffic without
# changing any user-visible behaviour — the forecast genuinely doesn't
# change minute-to-minute at the same coordinates.
#
# The cache lives at module scope, so it persists across invocations on
# the same warm Cloud Function instance but is wiped on cold start. That
# is the right trade-off for this app: it gives most of the benefit
# without needing a Firestore-backed cache (which would cost reads).
#
# Coordinate precision: rounding to 4 decimal places (~11 m at the
# equator) means tiny GPS jitter doesn't bypass the cache. Solar
# forecasts at that resolution are effectively identical.

_FORECAST_CACHE: dict[tuple[float, float], tuple[list[dict[str, Any]], float]] = {}
# 1 hour. Open-Meteo refreshes their forecast model every 1-6 hours
# upstream, so caching for under that window was throwing away calls
# for a "freshness" we never actually had. An hour gives a ~6x
# reduction in upstream traffic vs 10 minutes with no user-visible
# difference (the chart still shows the right 15-min slot via
# _pick_current_row, regardless of how old the cached rows are).
_FORECAST_CACHE_TTL_S = 3600.0


def _cache_key(lat: float, lng: float) -> tuple[float, float]:
    return (round(lat, 4), round(lng, 4))


def clear_forecast_cache() -> None:
    """Test helper — wipe the cache so each unit test starts clean."""
    _FORECAST_CACHE.clear()


# ===========================================================================
# Public: ONE request, 14-day 15-minute forecast
# ===========================================================================

def fetch_forecast(lat: float, lng: float) -> list[dict[str, Any]]:
    """
    Fetch the full 14-day forecast at 15-minute resolution. Cached in
    memory for `_FORECAST_CACHE_TTL_S` seconds per (lat, lng) so back-to-
    back predictions don't hammer Open-Meteo.

    Returns a flat list of ~96 × 14 = ~1344 rows, each a dict with all 10
    model features plus 'timestamp' (ISO 8601 local) and 'date'
    (YYYY-MM-DD local).

    Both modes of predict_energy (current-now and date-range) are served
    from this one function — there is no separate "current weather"
    endpoint call.
    """
    now = time.time()
    key = _cache_key(lat, lng)
    cached = _FORECAST_CACHE.get(key)
    if cached is not None:
        rows, expires_at = cached
        if expires_at > now and rows:
            return rows
        # Expired — drop the entry so we don't keep dead data around.
        _FORECAST_CACHE.pop(key, None)

    rows = _fetch_forecast_uncached(lat, lng)
    _FORECAST_CACHE[key] = (rows, now + _FORECAST_CACHE_TTL_S)
    return rows


def _fetch_forecast_uncached(lat: float, lng: float) -> list[dict[str, Any]]:
    """The actual Open-Meteo HTTP fetch + retry loop. No caching here."""
    params = {
        "latitude": lat,
        "longitude": lng,
        # High-resolution variables: change every 15 minutes.
        "minutely_15": ",".join([
            "shortwave_radiation",
            "temperature_2m",
            "relative_humidity_2m",
            "wind_speed_10m",
        ]),
        # Slower-moving variables: hourly resolution is plenty.
        "hourly": ",".join([
            "surface_pressure",
            "cloud_cover",
            "rain",
            "snowfall",
        ]),
        # Daily sunrise/sunset for the dayLength feature.
        "daily": "sunrise,sunset",
        "wind_speed_unit": "ms",
        # ISO 8601 strings make per-slot date/hour parsing trivial below.
        "timeformat": "iso8601",
        # `auto` aligns the day boundaries (and sunrise/sunset) to the local
        # solar day at the panel — what users mean when they say "today".
        "timezone": "auto",
        "forecast_days": MAX_FORECAST_DAYS,
    }

    # Retry on transient network/5xx errors. We want to look like a healthy
    # caller to Open-Meteo, not hammer them - hence the 0.5s/1s/2s backoff.
    last_exc: Exception | None = None
    for attempt in range(MAX_RETRIES):
        try:
            response = requests.get(
                OPEN_METEO_URL, params=params, timeout=HTTP_TIMEOUT,
            )
            response.raise_for_status()
            payload = response.json()
            return _payload_to_rows(payload)
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response is not None else 0
            # 429 (rate limit) is a special case — retrying with our short
            # backoff won't help (Open-Meteo's limits reset per minute or
            # per day). Surface a clearer message immediately so the user
            # knows it's a quota issue, not a network outage.
            if status == 429:
                raise WeatherFetchError(
                    "Weather service rate limit reached. "
                    "Please wait a minute and try again."
                ) from exc
            # Other 4xx is permanent - bail immediately.
            if 400 <= status < 500:
                raise WeatherFetchError(
                    f"Open-Meteo rejected request ({status}): {exc}"
                ) from exc
            last_exc = exc
        except requests.RequestException as exc:
            # Connect / read timeouts and other transport errors - retry.
            last_exc = exc
        except ValueError as exc:  # JSON decode error - probably empty body
            last_exc = exc

        # Sleep before the next attempt unless this was the last one.
        if attempt < MAX_RETRIES - 1:
            time.sleep(RETRY_BACKOFF_S[attempt])

    raise WeatherFetchError(
        f"Open-Meteo request failed after {MAX_RETRIES} attempts: {last_exc}"
    ) from last_exc


# ===========================================================================
# Public convenience wrappers
# ===========================================================================

def fetch_weather(lat: float, lng: float) -> dict[str, Any]:
    """
    "Current conditions" mode: fetch the 14-day forecast and return the single
    15-min slot whose start time matches the current local quarter-hour.

    Returned dict has the 10 model features plus 'timestamp' and 'date'.
    """
    rows = fetch_forecast(lat, lng)
    if not rows:
        raise WeatherFetchError("Open-Meteo returned no minutely_15 rows")
    return _pick_current_row(rows)


def fetch_range_forecast(
    lat: float,
    lng: float,
    date_from: date,
    date_to: date,
) -> list[dict[str, Any]]:
    """
    Date-range mode: fetch the 14-day forecast and keep only the 15-min rows
    whose local date falls inside [date_from, date_to] inclusive.

    date_to is re-capped at today + MAX_FORECAST_DAYS as defense-in-depth
    against direct callers bypassing validators.
    """
    max_to = date.today() + timedelta(days=MAX_FORECAST_DAYS)
    if date_to > max_to:
        date_to = max_to
    if date_from > date_to:
        # Empty window after capping — return empty list rather than error.
        return []

    rows = fetch_forecast(lat, lng)
    df_str, dt_str = date_from.isoformat(), date_to.isoformat()
    return [r for r in rows if df_str <= r["date"] <= dt_str]


# ===========================================================================
# Internal helpers
# ===========================================================================

def _safe_float(arr: list[Any], i: int) -> float:
    """
    Read arr[i] as a float. Defaults to 0.0 on missing index, None, or any
    type error — Open-Meteo occasionally returns nulls (e.g. polar night).
    """
    try:
        v = arr[i]
    except (IndexError, TypeError):
        return 0.0
    return float(v) if v is not None else 0.0


def _pick_current_row(rows: list[dict[str, Any]]) -> dict[str, Any]:
    """
    Pick the 15-min slot whose start matches the most recent past quarter
    relative to the local clock.

    Open-Meteo returns timestamps in local time when timezone=auto. ISO 8601
    is fixed-width ('YYYY-MM-DDTHH:MM') so lexicographic string comparison
    matches chronological order — no datetime parsing per row needed.
    """
    now = datetime.now()
    minute = (now.minute // 15) * 15
    now_slot_iso = now.replace(minute=minute, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M")

    # Walk backwards: the first row whose timestamp is <= now_slot is the
    # current 15-min slot. Reverse iteration is O(1) for the common case.
    for r in reversed(rows):
        if r["timestamp"] <= now_slot_iso:
            return r
    # If "now" is before the forecast start (clock skew, edge case),
    # fall back to the first available row rather than failing.
    return rows[0]


def _payload_to_rows(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """
    Combine minutely_15 + hourly + daily into one flat list of feature rows.

    For each minutely_15 timestamp we look up:
      * the hourly bucket it falls into (truncate the minute component to :00)
      * the daily bucket it falls into (truncate to date)

    The output is the canonical 10-feature dict the model expects, plus
    'timestamp' and 'date' for the API/UI layer.
    """
    minutely = payload.get("minutely_15") or {}
    hourly = payload.get("hourly") or {}
    daily = payload.get("daily") or {}

    times: list[str] = minutely.get("time") or []
    if not times:
        return []

    # ---- Hourly lookup: 'YYYY-MM-DDTHH:00' -> {pressure, clouds, rain, snow} ----
    h_times: list[str] = hourly.get("time") or []
    h_pressure = hourly.get("surface_pressure") or []
    h_clouds = hourly.get("cloud_cover") or []
    h_rain = hourly.get("rain") or []
    h_snow = hourly.get("snowfall") or []
    hourly_lookup: dict[str, dict[str, float]] = {}
    for i, ht in enumerate(h_times):
        hourly_lookup[ht] = {
            "pressure":   _safe_float(h_pressure, i),
            "clouds_all": _safe_float(h_clouds, i),
            "rain_1h":    _safe_float(h_rain, i),
            # Open-Meteo returns snowfall in cm; the dataset uses mm.
            "snow_1h":    _safe_float(h_snow, i) * 10.0,
        }

    # ---- Daily lookup: 'YYYY-MM-DD' -> dayLength_minutes ----
    # IMPORTANT: dayLength in MINUTES — the retrained PR model expects this.
    d_dates: list[str] = daily.get("time") or []
    d_sunrise = daily.get("sunrise") or []
    d_sunset = daily.get("sunset") or []
    day_length_by_date: dict[str, float] = {}
    for i, d_str in enumerate(d_dates):
        try:
            sr_dt = datetime.fromisoformat(d_sunrise[i])
            ss_dt = datetime.fromisoformat(d_sunset[i])
            day_length_by_date[d_str] = max(0.0, (ss_dt - sr_dt).total_seconds() / 60.0)
        except (IndexError, TypeError, ValueError):
            # Polar regions can have null sunrise/sunset — fall back to 0.
            day_length_by_date[d_str] = 0.0

    # ---- Minutely arrays ----
    m_ghi = minutely.get("shortwave_radiation") or []
    m_temp = minutely.get("temperature_2m") or []
    m_hum = minutely.get("relative_humidity_2m") or []
    m_wind = minutely.get("wind_speed_10m") or []

    # Default hourly bucket if a minute timestamp falls outside the hourly
    # coverage (shouldn't happen with forecast_days=14, but be safe).
    _empty_hour = {"pressure": 0.0, "clouds_all": 0.0, "rain_1h": 0.0, "snow_1h": 0.0}

    rows: list[dict[str, Any]] = []
    for i, ts in enumerate(times):
        try:
            ts_dt = datetime.fromisoformat(ts)
        except ValueError:
            # Skip malformed timestamps rather than crashing the whole forecast.
            continue

        hour_key = ts_dt.replace(minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M")
        date_key = ts_dt.strftime("%Y-%m-%d")
        h = hourly_lookup.get(hour_key, _empty_hour)

        rows.append({
            "timestamp":  ts,
            "date":       date_key,
            "GHI":        _safe_float(m_ghi, i),
            "temp":       _safe_float(m_temp, i),
            "humidity":   _safe_float(m_hum, i),
            "wind_speed": _safe_float(m_wind, i),
            "pressure":   h["pressure"],
            "clouds_all": h["clouds_all"],
            "rain_1h":    h["rain_1h"],
            "snow_1h":    h["snow_1h"],
            "dayLength":  day_length_by_date.get(date_key, 0.0),
            "hour":       float(ts_dt.hour),
        })

    return rows
