"""
validators.py - Input validation for SolarPredict Cloud Functions.

Every validator raises ValueError on bad input - main.py catches these and
converts them to HttpsError(INVALID_ARGUMENT) responses. PermissionError is
converted to UNAUTHENTICATED.
"""

from datetime import date, timedelta
from typing import Any
import re


MAX_FORECAST_DAYS = 14
USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,30}$")


def _require_dict(data: Any, name: str = "payload") -> dict:
    if not isinstance(data, dict):
        raise ValueError(f"{name} must be a JSON object, got {type(data).__name__}")
    return data


def _require_number(value: Any, field: str, min_val: float, max_val: float) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"'{field}' must be a number")
    value = float(value)
    if value < min_val or value > max_val:
        raise ValueError(f"'{field}' must be between {min_val} and {max_val}")
    return value


def _require_string(value: Any, field: str, max_len: int = 200) -> str:
    if not isinstance(value, str):
        raise ValueError(f"'{field}' must be a string")
    value = value.strip()
    if not value:
        raise ValueError(f"'{field}' cannot be empty")
    if len(value) > max_len:
        raise ValueError(f"'{field}' exceeds max length {max_len}")
    return value


def _optional_string(value: Any, field: str, max_len: int = 200) -> str:
    """Returns empty string for None/missing; otherwise validates."""
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ValueError(f"'{field}' must be a string")
    value = value.strip()
    if len(value) > max_len:
        raise ValueError(f"'{field}' exceeds max length {max_len}")
    return value


# ---------------------------------------------------------------------------
# Auth helper
# ---------------------------------------------------------------------------

def require_auth(auth) -> str:
    """
    Extract the authenticated UID from a CallableRequest.auth object.
    Anonymous Firebase users are also accepted - they get a real UID.
    Guest data should not call functions that persist data; client is
    responsible for routing guests to in-memory storage instead.
    """
    if auth is None or not getattr(auth, "uid", None):
        raise PermissionError("Authentication required")
    return auth.uid


# ---------------------------------------------------------------------------
# predict_energy input
# ---------------------------------------------------------------------------

def validate_predict_input(data: Any) -> dict:
    """
    Required:
        lat            in [-90, 90]
        lng            in [-180, 180]
        suprafata_user > 0
        eficienta_user in (0, 1]
    Optional date-range fields are validated separately by validate_date_range.
    """
    data = _require_dict(data, "predict_energy payload")
    lat = _require_number(data.get("lat"), "lat", -90.0, 90.0)
    lng = _require_number(data.get("lng"), "lng", -180.0, 180.0)
    suprafata = _require_number(data.get("suprafata_user"), "suprafata_user", 0.0001, 100000.0)
    eficienta = _require_number(data.get("eficienta_user"), "eficienta_user", 0.0001, 1.0)
    return {
        "lat": lat,
        "lng": lng,
        "suprafata_user": suprafata,
        "eficienta_user": eficienta,
    }


def validate_date_range(data: Any) -> tuple[date, date]:
    data = _require_dict(data, "date range")
    df_raw = data.get("date_from")
    dt_raw = data.get("date_to")
    if not isinstance(df_raw, str) or not isinstance(dt_raw, str):
        raise ValueError("'date_from' and 'date_to' must be ISO date strings (YYYY-MM-DD)")
    try:
        date_from = date.fromisoformat(df_raw)
    except ValueError:
        raise ValueError("'date_from' is not a valid ISO date (expected YYYY-MM-DD)")
    try:
        date_to = date.fromisoformat(dt_raw)
    except ValueError:
        raise ValueError("'date_to' is not a valid ISO date (expected YYYY-MM-DD)")
    if date_from > date_to:
        raise ValueError("'date_from' must be <= 'date_to'")
    today = date.today()
    if date_from < today:
        # Open-Meteo's forecast endpoint returns no rows for past days, and
        # the model is undefined for historical inputs, so we reject up-front
        # rather than silently returning empty hourly[].
        raise ValueError(
            f"'date_from' cannot be before today ({today.isoformat()})"
        )
    max_to = today + timedelta(days=MAX_FORECAST_DAYS)
    if date_to > max_to:
        raise ValueError(
            f"'date_to' cannot exceed today + {MAX_FORECAST_DAYS} days "
            f"(max allowed: {max_to.isoformat()})"
        )
    return date_from, date_to


# ---------------------------------------------------------------------------
# Panel config (no orientation / tilt anymore - removed per spec)
# ---------------------------------------------------------------------------

def validate_panel_config(data: Any) -> dict:
    """
    Required:
        name        non-empty string (unique per user, enforced at write site)
        area_m2     > 0
        efficiency  in (0, 1]   (decimal)
    Optional:
        capacity_kwp >= 0
    """
    data = _require_dict(data, "panel config")
    name = _require_string(data.get("name"), "name", max_len=80)
    area = _require_number(data.get("area_m2"), "area_m2", 0.0001, 100000.0)
    efficiency = _require_number(data.get("efficiency"), "efficiency", 0.0001, 1.0)
    capacity = data.get("capacity_kwp")
    capacity_kwp = 0.0
    if capacity is not None:
        capacity_kwp = _require_number(capacity, "capacity_kwp", 0.0, 100000.0)
    return {
        "name": name,
        "area_m2": area,
        "efficiency": efficiency,
        "capacity_kwp": capacity_kwp,
    }


# ---------------------------------------------------------------------------
# Location (saved location)
# ---------------------------------------------------------------------------

def validate_location_input(data: Any) -> dict:
    data = _require_dict(data, "location")
    name = _require_string(data.get("name"), "name", max_len=80)
    lat = _require_number(data.get("lat"), "lat", -90.0, 90.0)
    lng = _require_number(data.get("lng"), "lng", -180.0, 180.0)
    address = _optional_string(data.get("address"), "address", max_len=300)
    return {"name": name, "lat": lat, "lng": lng, "address": address}


# ---------------------------------------------------------------------------
# save_prediction input
# ---------------------------------------------------------------------------

def validate_save_prediction_input(data: Any) -> dict:
    data = _require_dict(data, "save_prediction payload")

    result = _require_dict(data.get("prediction_result"), "prediction_result")
    p10 = _require_number(result.get("p10"), "prediction_result.p10", 0.0, 1e12)
    p50 = _require_number(result.get("p50"), "prediction_result.p50", 0.0, 1e12)
    p90 = _require_number(result.get("p90"), "prediction_result.p90", 0.0, 1e12)
    if not (p10 <= p50 <= p90):
        raise ValueError("prediction quantiles must satisfy p10 <= p50 <= p90")

    location = _require_dict(data.get("location"), "location")
    lat = _require_number(location.get("lat"), "location.lat", -90.0, 90.0)
    lng = _require_number(location.get("lng"), "location.lng", -180.0, 180.0)
    location_name = _optional_string(location.get("name"), "location.name", max_len=120)
    address = _optional_string(location.get("address"), "location.address", max_len=300)

    panel_raw = _require_dict(data.get("panel_config"), "panel_config")
    panel = {
        "name": _optional_string(panel_raw.get("name"), "panel_config.name", max_len=80),
        "area_m2": _require_number(panel_raw.get("area_m2"), "panel_config.area_m2", 0.0001, 100000.0),
        "efficiency": _require_number(panel_raw.get("efficiency"), "panel_config.efficiency", 0.0001, 1.0),
    }

    weather = result.get("weather")
    if weather is not None and not isinstance(weather, dict):
        raise ValueError("prediction_result.weather must be an object if provided")

    date_from = data.get("date_from")
    date_to = data.get("date_to")
    if date_from is not None and not isinstance(date_from, str):
        raise ValueError("date_from must be a string if provided")
    if date_to is not None and not isinstance(date_to, str):
        raise ValueError("date_to must be a string if provided")

    return {
        "result": {
            "p10": p10,
            "p50": p50,
            "p90": p90,
            "unit": result.get("unit", "Wh"),
        },
        "location": {
            "lat": lat,
            "lng": lng,
            "name": location_name,
            "address": address,
        },
        "panel": panel,
        "weather_snapshot": weather or {},
        "date_from": date_from,
        "date_to": date_to,
    }


# ---------------------------------------------------------------------------
# get_history input
# ---------------------------------------------------------------------------

def validate_history_input(data: Any) -> int:
    if data is None:
        return 50
    data = _require_dict(data, "get_history payload")
    limit = data.get("limit", 50)
    if isinstance(limit, bool) or not isinstance(limit, int):
        raise ValueError("'limit' must be an integer")
    if limit < 1 or limit > 200:
        raise ValueError("'limit' must be between 1 and 200")
    return limit


# ---------------------------------------------------------------------------
# delete_prediction / delete_panel / delete_location input
# ---------------------------------------------------------------------------

def validate_doc_id(data: Any, field: str = "doc_id") -> str:
    data = _require_dict(data, "delete payload")
    doc_id = _require_string(data.get(field), field, max_len=128)
    if "/" in doc_id or doc_id in (".", ".."):
        raise ValueError(f"'{field}' contains invalid characters")
    return doc_id


# ---------------------------------------------------------------------------
# User profile input
# ---------------------------------------------------------------------------

def validate_username(value: Any) -> str:
    """Username: 3..30 chars, alphanumeric + underscore."""
    if not isinstance(value, str):
        raise ValueError("'username' must be a string")
    value = value.strip()
    if not USERNAME_RE.match(value):
        raise ValueError(
            "'username' must be 3-30 characters, letters/digits/underscores only"
        )
    return value


def validate_profile_input(data: Any) -> dict:
    data = _require_dict(data, "profile payload")
    username = validate_username(data.get("username"))
    return {"username": username}


# ---------------------------------------------------------------------------
# Favorite input
# ---------------------------------------------------------------------------

def validate_favorite_input(data: Any) -> str:
    data = _require_dict(data, "favorite payload")
    doc_id = _require_string(data.get("prediction_id"), "prediction_id", max_len=128)
    if "/" in doc_id or doc_id in (".", ".."):
        raise ValueError("'prediction_id' contains invalid characters")
    return doc_id
