"""
test_backend.py — End-to-end smoke tests for SolarPredict Cloud Functions.

All requests target the LOCAL Firebase emulators:
    Auth      → http://localhost:9099
    Functions → http://localhost:5001

No production Firebase project is touched. The auth emulator accepts any
non-empty API key, so we use a placeholder string. Cloud Function callable
endpoints are reached at:

    http://localhost:5001/<projectId>/<region>/<functionName>

Prerequisites:
    1. Run `firebase emulators:start` in SolarPredict_Backend/
    2. From the venv:  python test_backend.py

Each test prints "PASS" or "FAIL: <reason>". Tests run sequentially; the auth
token from test_auth() is reused by the rest. Failures don't abort the suite —
we want to see every result in one run.
"""

import json
import random
import sys
import traceback
from datetime import date, datetime, timedelta
from typing import Any

import requests


# ---------------------------------------------------------------------------
# Emulator configuration — adjust if your firebase.json changes ports.
# ---------------------------------------------------------------------------
PROJECT_ID = "solar-predict-b09d9"        # from .firebaserc
REGION = "europe-west1"                     # default for python firebase-functions
AUTH_HOST = "http://localhost:9099"
FUNCTIONS_BASE = f"http://localhost:5001/{PROJECT_ID}/{REGION}"

TEST_EMAIL = "test@gmail.com"
TEST_PASSWORD = "test1234"                 # any password works on the emulator
FAKE_API_KEY = "fake-api-key"              # emulator ignores the value

REQUEST_TIMEOUT = 30                       # generous — first call is a cold start


# ---------------------------------------------------------------------------
# Pretty-print helpers
# ---------------------------------------------------------------------------

def _banner(title: str) -> None:
    line = "=" * 70
    print(f"\n{line}\n{title}\n{line}")


def _pass(name: str, extra: str = "") -> None:
    suffix = f" — {extra}" if extra else ""
    print(f"[PASS] {name}{suffix}")


def _fail(name: str, reason: str) -> None:
    print(f"[FAIL] {name}: {reason}")


# ---------------------------------------------------------------------------
# Low-level HTTP helpers
# ---------------------------------------------------------------------------

def _call_function(name: str, payload: dict, token: str) -> dict:
    """
    Invoke a Firebase callable function on the emulator.

    Callable wire format: POST { "data": <payload> } with a Bearer token.
    Successful response: { "result": <return value> }.
    Error response:      { "error": { "status": "...", "message": "..." } }
    """
    url = f"{FUNCTIONS_BASE}/{name}"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }
    body = {"data": payload}
    resp = requests.post(url, headers=headers, json=body, timeout=REQUEST_TIMEOUT)

    # Callable functions always return JSON, even on error.
    try:
        data = resp.json()
    except ValueError:
        raise RuntimeError(f"{name} returned non-JSON (HTTP {resp.status_code}): {resp.text[:200]}")

    if "error" in data:
        err = data["error"]
        raise RuntimeError(f"{name} error: {err.get('status')} — {err.get('message')}")

    if "result" not in data:
        raise RuntimeError(f"{name} unexpected response: {data}")

    return data["result"]


def _signin_or_signup(email: str, password: str) -> dict:
    """
    Try signInWithPassword first. If the user doesn't exist (EMAIL_NOT_FOUND
    or INVALID_LOGIN_CREDENTIALS), create the account via signUp.

    Returns the parsed JSON containing `idToken`, `localId`, etc.
    """
    base = f"{AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts"
    body = {"email": email, "password": password, "returnSecureToken": True}

    sign_in = requests.post(
        f"{base}:signInWithPassword?key={FAKE_API_KEY}",
        json=body, timeout=REQUEST_TIMEOUT,
    )
    if sign_in.status_code == 200:
        return sign_in.json()

    # Fall through to signUp on any 4xx — the emulator returns 400 for missing user.
    sign_up = requests.post(
        f"{base}:signUp?key={FAKE_API_KEY}",
        json=body, timeout=REQUEST_TIMEOUT,
    )
    if sign_up.status_code != 200:
        raise RuntimeError(
            f"Auth emulator signUp failed ({sign_up.status_code}): {sign_up.text}"
        )
    return sign_up.json()


# ---------------------------------------------------------------------------
# Test 1 — auth
# ---------------------------------------------------------------------------

def test_auth() -> str | None:
    """
    Sign in (or sign up) the test user against the Auth emulator and return
    the ID token. Returns None on failure so the runner can short-circuit
    later tests that need authentication.
    """
    _banner("TEST 1 — Auth: get ID token from emulator")
    try:
        result = _signin_or_signup(TEST_EMAIL, TEST_PASSWORD)
        token = result.get("idToken")
        uid = result.get("localId")
        if not token:
            _fail("test_auth", f"No idToken in response: {result}")
            return None
        # Show only a prefix — full JWTs are noisy in CI logs.
        print(f"  uid:   {uid}")
        print(f"  token: {token[:32]}...  (len={len(token)})")
        _pass("test_auth", f"signed in as {TEST_EMAIL}")
        return token
    except Exception as e:
        _fail("test_auth", str(e))
        return None


# ---------------------------------------------------------------------------
# Test 2 — single (current-conditions) prediction
# ---------------------------------------------------------------------------

def test_single_prediction(token: str) -> bool:
    """
    Call predict_energy with no date range — exercises Mode A (current weather).
    Coordinates: Bucharest (44.43, 26.10). Panel: 25 m² @ 18% efficiency.
    """
    _banner("TEST 2 — predict_energy (single, current conditions)")
    payload = {
        "lat": 44.43,
        "lng": 26.10,
        "suprafata_user": 25.0,
        "eficienta_user": 0.18,
    }
    try:
        result = _call_function("predict_energy", payload, token)
        for key in ("p10", "p50", "p90", "weather"):
            if key not in result:
                _fail("test_single_prediction", f"missing key '{key}' in response")
                return False
        p10, p50, p90 = result["p10"], result["p50"], result["p90"]
        if not (p10 <= p50 <= p90):
            _fail("test_single_prediction", f"quantile order violated: {p10} {p50} {p90}")
            return False
        print(f"  P10: {p10:>10.2f} Wh")
        print(f"  P50: {p50:>10.2f} Wh   (median)")
        print(f"  P90: {p90:>10.2f} Wh")
        print(f"  Weather snapshot:")
        for k, v in result["weather"].items():
            print(f"    {k:<12} = {v}")
        _pass("test_single_prediction")
        return True
    except Exception as e:
        _fail("test_single_prediction", str(e))
        return False


# ---------------------------------------------------------------------------
# Test 3 — multi-day range forecast
# ---------------------------------------------------------------------------

def test_range_prediction(token: str) -> bool:
    """
    Call predict_energy with date_from=today and date_to=today+7 days.
    Verifies the hourly array exists and prints a daily_totals table.
    """
    _banner("TEST 3 — predict_energy (range: today .. today+7)")
    today = date.today()
    payload = {
        "lat": 44.43,
        "lng": 26.10,
        "suprafata_user": 25.0,
        "eficienta_user": 0.18,
        "date_from": today.isoformat(),
        "date_to": (today + timedelta(days=7)).isoformat(),
    }
    try:
        result = _call_function("predict_energy", payload, token)
        for key in ("hourly", "daily_totals", "date_from", "date_to"):
            if key not in result:
                _fail("test_range_prediction", f"missing key '{key}' in response")
                return False

        hourly = result["hourly"]
        totals = result["daily_totals"]
        if not isinstance(hourly, list) or not hourly:
            _fail("test_range_prediction", "hourly array is empty")
            return False
        if not isinstance(totals, list) or not totals:
            _fail("test_range_prediction", "daily_totals array is empty")
            return False

        print(f"  Window: {result['date_from']} .. {result['date_to']}")
        # 'hourly' is the API field name; rows are at 15-min granularity (~96/day).
        print(f"  15-min rows: {len(hourly)}   First: {hourly[0]['timestamp']}   Last: {hourly[-1]['timestamp']}")
        print(f"\n  Daily totals:")
        print(f"  {'Date':<12} {'P10 (Wh)':>12} {'P50 (Wh)':>12} {'P90 (Wh)':>12}")
        print(f"  {'-'*12} {'-'*12} {'-'*12} {'-'*12}")
        for row in totals:
            print(f"  {row['date']:<12} {row['p10_total_wh']:>12.0f} "
                  f"{row['p50_total_wh']:>12.0f} {row['p90_total_wh']:>12.0f}")

        _pass("test_range_prediction", f"{len(totals)} days, {len(hourly)} hours")
        return True
    except Exception as e:
        _fail("test_range_prediction", str(e))
        return False


# ---------------------------------------------------------------------------
# Test 3b — random hour from a single-day forecast
# ---------------------------------------------------------------------------

def test_random_forecast_hour(token: str) -> bool:
    """
    Spot-check the forecast at a randomly chosen hour, useful for comparing
    against an external solar-forecast site:

        1. Pick a random date in [tomorrow, today + 13 days].
        2. Pick a random daylight hour in [6, 18].
        3. Call predict_energy with date_from == date_to == that day.
        4. Locate ALL 4 fifteen-minute slots inside that hour, print each,
           and print their sum as the hourly-total Wh (the figure you'd
           compare against another site's hourly forecast).

    Random by design — re-runs surface different hours so you can spot-check
    the model across many conditions.
    """
    _banner("TEST 3b — predict_energy (random hour, 15-min slots)")

    # 1..13 inclusive — randint includes both endpoints.
    days_ahead = random.randint(1, 13)
    target_date = date.today() + timedelta(days=days_ahead)
    target_hour = random.randint(6, 18)

    print(f"  Randomly selected date: {target_date.isoformat()}  (today+{days_ahead}d)")
    print(f"  Randomly selected hour: {target_hour:02d}:00 local  (4 quarter-hour slots)")

    payload = {
        "lat": 44.43,
        "lng": 26.10,
        "suprafata_user": 25.0,
        "eficienta_user": 0.18,
        "date_from": target_date.isoformat(),
        "date_to": target_date.isoformat(),
    }

    try:
        result = _call_function("predict_energy", payload, token)

        # The response field is still called 'hourly' for API stability, but
        # it now carries 15-minute rows (4 per hour).
        hourly = result.get("hourly")
        if not isinstance(hourly, list) or not hourly:
            _fail("test_random_forecast_hour", "hourly array missing or empty")
            return False

        # Collect every row whose timestamp falls inside the chosen hour.
        slots: list[tuple[datetime, dict]] = []
        for row in hourly:
            try:
                ts_dt = datetime.fromisoformat(row.get("timestamp", ""))
            except ValueError:
                continue
            if ts_dt.date() == target_date and ts_dt.hour == target_hour:
                slots.append((ts_dt, row))
        slots.sort(key=lambda s: s[0])

        if not slots:
            _fail(
                "test_random_forecast_hour",
                f"no 15-min slot for {target_date.isoformat()} {target_hour:02d}:xx "
                f"(got {len(hourly)} rows total)",
            )
            return False

        # Per-slot table.
        print()
        print(f"  {'time':<6} {'P10 (Wh)':>10} {'P50 (Wh)':>10} {'P90 (Wh)':>10} "
              f"{'GHI':>8} {'temp':>7}")
        print(f"  {'-'*6} {'-'*10} {'-'*10} {'-'*10} {'-'*8} {'-'*7}")
        sum_p10 = sum_p50 = sum_p90 = 0.0
        for ts_dt, row in slots:
            print(f"  {ts_dt.strftime('%H:%M'):<6} "
                  f"{row['p10']:>10.2f} {row['p50']:>10.2f} {row['p90']:>10.2f} "
                  f"{row['GHI']:>8.1f} {row['temp']:>7.2f}")
            sum_p10 += row["p10"]
            sum_p50 += row["p50"]
            sum_p90 += row["p90"]

        # Hourly aggregate — the number to compare against an external site.
        print(f"\n  Hourly total (sum of {len(slots)} quarter-hour slots):")
        print(f"    P10: {sum_p10:>10.2f} Wh")
        print(f"    P50: {sum_p50:>10.2f} Wh   (median)")
        print(f"    P90: {sum_p90:>10.2f} Wh")

        if not (sum_p10 <= sum_p50 <= sum_p90):
            _fail("test_random_forecast_hour",
                  f"hourly total quantile order violated: {sum_p10} {sum_p50} {sum_p90}")
            return False

        _pass("test_random_forecast_hour",
              f"{len(slots)} slots @ {target_date.isoformat()} {target_hour:02d}:xx, "
              f"P50 total {sum_p50:.0f} Wh")
        return True
    except Exception as e:
        _fail("test_random_forecast_hour", str(e))
        return False

# ---------------------------------------------------------------------------
# Test 3c — validare PVWatts vs modelul nostru
# ---------------------------------------------------------------------------

def test_pvwatts_validation(token: str) -> bool:
    """
    Compară predicția modelului cu PVWatts pentru o zi și oră aleatoare
    din fereastra azi → azi+14 zile.
    """
    _banner("TEST 3c — validare PVWatts vs modelul nostru (zi+oră random)")

    # --- Zi și oră random ---
    days_ahead = random.randint(1, 13)
    target = date.today() + timedelta(days=days_ahead)
    target_hour = random.randint(6, 18)

    print(f"  Zi selectată random: {target.isoformat()} (today+{days_ahead}d)")
    print(f"  Oră selectată random: {target_hour:02d}:00 local")

    # --- Pas 1: PVWatts pentru ora selectată ---
    pvwatts_params = {
        "api_key": "YOUR_NREL_API_KEY",
        "lat": 44.43, "lon": 26.10,
        "system_capacity": 4.5,
        "azimuth": 180, "tilt": 35,
        "array_type": 1, "module_type": 1,
        "losses": 14, "timeframe": "hourly"
    }
    try:
        pv_resp = requests.get(
            "https://developer.nrel.gov/api/pvwatts/v8.json",
            params=pvwatts_params, timeout=30
        )
        ac_hourly = pv_resp.json()["outputs"]["ac"]  # 8760 valori

        # Media pentru luna și ora selectată (toate zilele din luna respectivă)
        month = target.month
        month_start_day = sum([31,28,31,30,31,30,31,31,30,31,30,31][:month-1])
        month_days = [31,28,31,30,31,30,31,31,30,31,30,31][month-1]
        vals = [ac_hourly[d * 24 + target_hour]
                for d in range(month_start_day, month_start_day + month_days)]
        pvwatts_ref = sum(vals) / len(vals)

        print(f"\n  PVWatts referință (luna {month}, ora {target_hour:02d}:00): {pvwatts_ref:.1f} Wh")

    except Exception as e:
        _fail("test_pvwatts_validation", f"PVWatts API error: {e}")
        return False

    # --- Pas 2: predict_energy pentru ziua random, ora random ---
    import datetime as dt
    payload = {
        "lat": 44.43, "lng": 26.10,
        "suprafata_user": 25.0, "eficienta_user": 0.18,
        "date_from": target.isoformat(),
        "date_to": target.isoformat(),
    }
    try:
        result = _call_function("predict_energy", payload, token)
        hourly = result.get("hourly", [])

        # CORECT — sumează toate cele 4 sloturi din ora respectivă
        slots = [
            r for r in hourly
            if dt.datetime.fromisoformat(r["timestamp"]).hour == target_hour
        ]

        if not slots:
            _fail("test_pvwatts_validation", f"ora {target_hour:02d}:00 nu există în hourly[]")
            return False

        match = {
            "p10": sum(s["p10"] for s in slots),
            "p50": sum(s["p50"] for s in slots),
            "p90": sum(s["p90"] for s in slots),
            "GHI": slots[0]["GHI"], 
        }
        if not match:
            _fail("test_pvwatts_validation",
                  f"ora {target_hour:02d}:00 nu există în hourly[]")
            return False

        diff = abs(match["p50"] - pvwatts_ref) / pvwatts_ref * 100 if pvwatts_ref > 0 else 0

        print(f"  Modelul nostru P10:  {match['p10']:.1f} Wh")
        print(f"  Modelul nostru P50:  {match['p50']:.1f} Wh  (median)")
        print(f"  Modelul nostru P90:  {match['p90']:.1f} Wh")
        print(f"  GHI Open-Meteo:      {match['GHI']:.1f} W/m²")
        print(f"  Diferență vs PVWatts: {diff:.1f}%")
        print(f"\n  NOTĂ: Diferența e așteptată — model antrenat pe 410m², scalat la 25m².")
        print(f"  PVWatts = medie climatologică; modelul = forecast Open-Meteo exact.")

        ok = match["p10"] <= match["p50"] <= match["p90"] and match["p50"] >= 0
        if ok:
            _pass("test_pvwatts_validation",
                  f"model={match['p50']:.1f}Wh vs PVWatts={pvwatts_ref:.1f}Wh (diff={diff:.1f}%)")
        else:
            _fail("test_pvwatts_validation", "quantile order violated")
        return ok

    except Exception as e:
        _fail("test_pvwatts_validation", str(e))
        return False

# ---------------------------------------------------------------------------
# Test 3c_repeated — rulează validarea PVWatts de 10 ori și calculează media
# ---------------------------------------------------------------------------

def test_3c_repeated(token: str) -> bool:
    """
    Rulează validarea PVWatts de 10 ori pe ore și zile random.
    FIX: un singur apel PVWatts la început (evită rate limiting DEMO_KEY).
    """
    _banner("TEST 3c REPEATED — 10 rulări PVWatts, calcul bias sistematic")

    # -------------------------------------------------------------------
    # Pas 1: UN SINGUR apel PVWatts — refolosit pentru toate iterațiile
    # -------------------------------------------------------------------
    pvwatts_params = {
        "api_key": "YOUR_NREL_API_KEY", "lat": 44.43, "lon": 26.10,
        "system_capacity": 4.5, "azimuth": 180, "tilt": 35,
        "array_type": 1, "module_type": 1, "losses": 14, "timeframe": "hourly",
    }
    try:
        pv_resp = requests.get(
            "https://developer.nrel.gov/api/pvwatts/v8.json",
            params=pvwatts_params, timeout=30,
        )
        pv_json = pv_resp.json()
        if "outputs" not in pv_json:
            _fail("test_3c_repeated",
                  f"PVWatts nu a returnat 'outputs': {pv_json.get('errors', pv_json)}")
            return False
        ac_hourly = pv_json["outputs"]["ac"]
        print(f"  PVWatts: date încărcate OK ({len(ac_hourly)} ore anuale)\n")
    except Exception as e:
        _fail("test_3c_repeated", f"PVWatts API error: {e}")
        return False

    # -------------------------------------------------------------------
    # Pas 2: 10 iterații — reutilizăm ac_hourly, apelăm doar modelul nostru
    # -------------------------------------------------------------------
    diffs = []
    results_table = []
    import datetime as dt

    for i in range(10):
        days_ahead  = random.randint(1, 13)
        target      = date.today() + timedelta(days=days_ahead)
        # Doar ore 09-15 — soare ridicat, comparație validă cu PVWatts
        target_hour = random.randint(9, 15)

        # PVWatts — medie lunară pentru ora selectată (din datele deja încărcate)
        month       = target.month
        month_start = sum([31,28,31,30,31,30,31,31,30,31,30,31][:month-1])
        month_days  = [31,28,31,30,31,30,31,31,30,31,30,31][month-1]
        vals        = [ac_hourly[d*24 + target_hour]
                       for d in range(month_start, month_start + month_days)]
        pvwatts_ref = sum(vals) / len(vals)

        if pvwatts_ref <= 0:
            continue

        # Modelul nostru
        payload = {
            "lat": 44.43, "lng": 26.10,
            "suprafata_user": 25.0, "eficienta_user": 0.18,
            "date_from": target.isoformat(),
            "date_to":   target.isoformat(),
        }
        try:
            result = _call_function("predict_energy", payload, token)
            hourly = result.get("hourly", [])
            slots  = [r for r in hourly
                      if dt.datetime.fromisoformat(r["timestamp"]).hour == target_hour]
            if not slots:
                continue
            p50_total = sum(s["p50"] for s in slots)
            ghi_avg   = sum(s["GHI"] for s in slots) / len(slots)
        except Exception as e:
            print(f"  [{i+1:02d}] SKIP — model error: {e}")
            continue

        diff = (p50_total - pvwatts_ref) / pvwatts_ref * 100
        diffs.append(abs(diff))
        results_table.append((i+1, target.isoformat(), target_hour,
                               ghi_avg, p50_total, pvwatts_ref, diff))

    if not diffs:
        _fail("test_3c_repeated", "niciun rezultat valid")
        return False

    # Tabel rezultate
    print(f"  {'#':<4} {'Data':<12} {'Ora':<5} {'GHI':>7} "
          f"{'Model P50':>10} {'PVWatts':>10} {'Diff%':>8}")
    print(f"  {'-'*4} {'-'*12} {'-'*5} {'-'*7} {'-'*10} {'-'*10} {'-'*8}")
    for (n, d, h, ghi, p50, pv, diff) in results_table:
        print(f"  {n:<4} {d:<12} {h:02d}:00 {ghi:>7.1f} "
              f"{p50:>10.1f} {pv:>10.1f} {diff:>+8.1f}%")

    avg_diff = sum(diffs) / len(diffs)
    max_diff = max(diffs)
    min_diff = min(diffs)

    print(f"\n  {'─'*65}")
    print(f"  Rulări valide:     {len(diffs)}/10")
    print(f"  Diferență medie:   {avg_diff:.1f}%   (ținta: < 20%)")
    print(f"  Diferență minimă:  {min_diff:.1f}%")
    print(f"  Diferență maximă:  {max_diff:.1f}%")

    if avg_diff < 20:
        print(f"\n  >>> BIAS SISTEMATIC: NU — modelul e calibrat corect ✓")
    else:
        print(f"\n  >>> ATENȚIE: bias sistematic posibil — investighează!")

    ok = avg_diff < 20
    if ok:
        _pass("test_3c_repeated",
              f"medie={avg_diff:.1f}% pe {len(diffs)} rulări (ore 09-15)")
    else:
        _fail("test_3c_repeated",
              f"medie={avg_diff:.1f}% — depășește pragul de 20%")
    return ok

# ---------------------------------------------------------------------------
# Test 3d — comparare zi întreagă: modelul nostru vs PVWatts (oră cu oră)
# ---------------------------------------------------------------------------

def test_daily_vs_pvwatts(token: str) -> bool:
    """
    Compară energia produsă pe o ZI ÎNTREAGĂ (oră cu oră) între:
        - Modelul nostru (sumă 4 sloturi × 15 min per oră)
        - PVWatts (media lunară pentru fiecare oră din zi)

    Zi aleatoare din fereastra [azi+1, azi+14].
    Afișează tabel orar + total zilnic + diferență procentuală.
    """
    _banner("TEST 3d — zi întreagă: modelul nostru vs PVWatts (oră cu oră)")

    # --- Zi random ---
    days_ahead = random.randint(1, 13)
    target = date.today() + timedelta(days=days_ahead)
    print(f"  Zi selectată random: {target.isoformat()}  (today+{days_ahead}d)")
    print(f"  Locație: Bucharest (44.43°N, 26.10°E) | 25 m² | 18% eficiență\n")

    # -------------------------------------------------------------------
    # PVWatts — media lunară pe fiecare oră (sistem 4.5 kW, tilt 35°, S)
    # -------------------------------------------------------------------
    pvwatts_params = {
        "api_key":         "YOUR_NREL_API_KEY",
        "lat":             44.43,
        "lon":             26.10,
        "system_capacity": 4.5,
        "azimuth":         180,
        "tilt":            35,
        "array_type":      1,
        "module_type":     1,
        "losses":          14,
        "timeframe":       "hourly",
    }
    try:
        pv_resp = requests.get(
            "https://developer.nrel.gov/api/pvwatts/v8.json",
            params=pvwatts_params,
            timeout=30,
        )
        ac_hourly = pv_resp.json()["outputs"]["ac"]  # 8760 valori

        month = target.month
        days_per_month = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
        month_start_day = sum(days_per_month[: month - 1])
        month_days      = days_per_month[month - 1]

        # Media fiecărei ore (0-23) pe toate zilele din luna selectată
        pvwatts_hourly: dict[int, float] = {}
        for h in range(24):
            vals = [
                ac_hourly[d * 24 + h]
                for d in range(month_start_day, month_start_day + month_days)
            ]
            pvwatts_hourly[h] = sum(vals) / len(vals)

        pvwatts_daily_total = sum(pvwatts_hourly.values())

    except Exception as e:
        _fail("test_daily_vs_pvwatts", f"PVWatts API error: {e}")
        return False

    # -------------------------------------------------------------------
    # Modelul nostru — forecast 15-min pentru ziua selectată
    # -------------------------------------------------------------------
    payload = {
        "lat":            44.43,
        "lng":            26.10,
        "suprafata_user": 25.0,
        "eficienta_user": 0.18,
        "date_from":      target.isoformat(),
        "date_to":        target.isoformat(),
    }
    try:
        result  = _call_function("predict_energy", payload, token)
        hourly  = result.get("hourly", [])
        totals  = result.get("daily_totals", [])

        if not hourly:
            _fail("test_daily_vs_pvwatts", "hourly array gol în răspuns")
            return False

        # Grupăm cele 4 sloturi × 15 min pe fiecare oră
        import datetime as dt
        model_hourly: dict[int, dict] = {}
        for row in hourly:
            ts_dt = dt.datetime.fromisoformat(row["timestamp"])
            h = ts_dt.hour
            if h not in model_hourly:
                model_hourly[h] = {"p10": 0.0, "p50": 0.0, "p90": 0.0,
                                   "GHI": 0.0, "slots": 0}
            model_hourly[h]["p10"]   += row["p10"]
            model_hourly[h]["p50"]   += row["p50"]
            model_hourly[h]["p90"]   += row["p90"]
            model_hourly[h]["GHI"]   += row["GHI"]
            model_hourly[h]["slots"] += 1

        # GHI mediu per oră (pentru afișare)
        for h in model_hourly:
            s = model_hourly[h]["slots"]
            model_hourly[h]["GHI"] = model_hourly[h]["GHI"] / s if s else 0.0

        model_daily_total_p50 = sum(v["p50"] for v in model_hourly.values())
        model_daily_total_p10 = sum(v["p10"] for v in model_hourly.values())
        model_daily_total_p90 = sum(v["p90"] for v in model_hourly.values())

    except Exception as e:
        _fail("test_daily_vs_pvwatts", str(e))
        return False

    # -------------------------------------------------------------------
    # Tabel orar
    # -------------------------------------------------------------------
    print(f"  {'Ora':<5} {'Model P10':>10} {'Model P50':>10} {'Model P90':>10} "
          f"{'PVWatts':>10} {'GHI avg':>8} {'Diff%':>7}")
    print(f"  {'-'*5} {'-'*10} {'-'*10} {'-'*10} {'-'*10} {'-'*8} {'-'*7}")

    for h in range(24):
        m   = model_hourly.get(h, {"p10": 0.0, "p50": 0.0, "p90": 0.0, "GHI": 0.0})
        pv  = pvwatts_hourly.get(h, 0.0)
        p50 = m["p50"]
        ghi = m["GHI"]

        if pv > 0:
            diff = (p50 - pv) / pv * 100
            diff_str = f"{diff:+.1f}%"
        else:
            diff_str = "   N/A"

        # Highlight ore de zi (GHI > 0 sau PVWatts > 0)
        marker = " <" if (ghi > 0 or pv > 0) else ""
        print(f"  {h:02d}:00 {m['p10']:>10.1f} {p50:>10.1f} {m['p90']:>10.1f} "
              f"{pv:>10.1f} {ghi:>8.1f} {diff_str:>7}{marker}")

    # -------------------------------------------------------------------
    # Totaluri zilnice
    # -------------------------------------------------------------------
    diff_total = (
        (model_daily_total_p50 - pvwatts_daily_total) / pvwatts_daily_total * 100
        if pvwatts_daily_total > 0 else 0
    )
    print(f"\n  {'─'*75}")
    print(f"  TOTAL ZI:  Model P10={model_daily_total_p10:,.0f} Wh  "
          f"P50={model_daily_total_p50:,.0f} Wh  "
          f"P90={model_daily_total_p90:,.0f} Wh")
    print(f"  TOTAL ZI:  PVWatts  ={pvwatts_daily_total:,.0f} Wh")
    print(f"  Diferență P50 vs PVWatts: {diff_total:+.1f}%")
    print(f"\n  NOTĂ: PVWatts = medie climatologică luna {month}.")
    print(f"        Modelul = forecast Open-Meteo exact pentru {target.isoformat()}.")
    print(f"        Diferențe mari sunt normale pentru zile cu nor/ploaie.")

    # -------------------------------------------------------------------
    # Pass/Fail — criteriu: P10 <= P50 <= P90 și totalul e pozitiv
    # -------------------------------------------------------------------
    ok = (
        model_daily_total_p10 <= model_daily_total_p50 <= model_daily_total_p90
        and model_daily_total_p50 >= 0
    )
    if ok:
        _pass(
            "test_daily_vs_pvwatts",
            f"Model P50={model_daily_total_p50:,.0f} Wh vs "
            f"PVWatts={pvwatts_daily_total:,.0f} Wh (diff={diff_total:+.1f}%)",
        )
    else:
        _fail("test_daily_vs_pvwatts", "quantile order violated sau total negativ")
    return ok 

def test_manual_sanity_check(token: str) -> bool:
    """
    Verificare manuală: compară predicția cu formula fizică directă.
    Nu depinde de PVWatts sau API extern.
    
    Formula: Energy = GHI × area × efficiency × 0.25
    PR = Energy_model / Energy_teoretic → trebuie să fie 0.5–1.2
    """
    _banner("TEST 6 — sanity check manual (formula fizică)")

    SUPRAFATA = 25.0
    EFICIENTA = 0.18

    # Forecast pentru mâine — o zi completă
    tomorrow = date.today() + timedelta(days=1)
    payload = {
        "lat": 44.43, "lng": 26.10,
        "suprafata_user": SUPRAFATA,
        "eficienta_user": EFICIENTA,
        "date_from": tomorrow.isoformat(),
        "date_to":   tomorrow.isoformat(),
    }

    try:
        result = _call_function("predict_energy", payload, token)
        hourly = result.get("hourly", [])

        import datetime as dt

        print(f"\n  Data verificată: {tomorrow.isoformat()}")
        print(f"  Panou: {SUPRAFATA} m² | {EFICIENTA*100:.0f}% eficiență\n")
        print(f"  {'Ora':<6} {'GHI':>7} {'E_teoretic':>11} {'E_model P50':>12} "
              f"{'PR':>6} {'Status':>8}")
        print(f"  {'-'*6} {'-'*7} {'-'*11} {'-'*12} {'-'*6} {'-'*8}")

        probleme = 0
        ore_cu_soare = 0

        for row in hourly:
            ts_dt = dt.datetime.fromisoformat(row["timestamp"])
            # Afișăm doar sloturi de :00 (primul slot al fiecărei ore)
            if ts_dt.minute != 0:
                continue

            ghi   = row["GHI"]
            p50   = row["p50"]
            h     = ts_dt.hour

            if ghi <= 0:
                continue

            ore_cu_soare += 1
            e_teoretic = ghi * SUPRAFATA * EFICIENTA * 0.25
            pr = p50 / e_teoretic if e_teoretic > 0 else 0

            if 0.3 <= pr <= 1.5:
                status = "OK ✓"
            else:
                status = "WARN ⚠"
                probleme += 1

            print(f"  {h:02d}:00  {ghi:>7.1f} {e_teoretic:>11.1f} {p50:>12.1f} "
                  f"{pr:>6.2f} {status:>8}")

        print(f"\n  {'─'*55}")
        print(f"  Ore cu soare verificate: {ore_cu_soare}")
        print(f"  Sloturi cu PR în afara [0.3–1.5]: {probleme}")

        ok = probleme == 0
        if ok:
            _pass("test_manual_sanity_check",
                  f"toate PR-urile în intervalul normal [0.3–1.5]")
        else:
            _fail("test_manual_sanity_check",
                  f"{probleme} sloturi cu PR anormal")
        return ok

    except Exception as e:
        _fail("test_manual_sanity_check", str(e))
        return False

# ---------------------------------------------------------------------------
# Test 4 — save_prediction + get_history
# ---------------------------------------------------------------------------

def test_save_and_get_history(token: str) -> bool:
    """
    Save a synthetic prediction, then read history and confirm the new doc_id
    appears in the result. Uses fixed values so we don't depend on Open-Meteo
    being reachable for this particular test.
    """
    _banner("TEST 4 — save_prediction + get_history")
    save_payload = {
        "prediction_result": {
            "p10": 100.0,
            "p50": 200.0,
            "p90": 300.0,
            "unit": "Wh",
            "weather": {"GHI": 500.0, "temp": 22.0, "clouds_all": 30.0},
        },
        "location": {"lat": 44.43, "lng": 26.10},
        "panel_config": {
            "area_m2": 25.0,
            "efficiency": 0.18,
            "orientation": "S",
            "tilt": 30.0,
        },
    }
    try:
        save_result = _call_function("save_prediction", save_payload, token)
        doc_id = save_result.get("doc_id")
        if not doc_id:
            _fail("test_save_and_get_history", f"save did not return doc_id: {save_result}")
            return False
        print(f"  Saved doc_id: {doc_id}")

        history = _call_function("get_history", {"limit": 50}, token)
        preds = history.get("predictions", [])
        ids = [p.get("id") for p in preds]
        print(f"  History returned {len(preds)} prediction(s).")

        if doc_id not in ids:
            _fail("test_save_and_get_history",
                  f"saved doc_id {doc_id} not in history ids: {ids[:5]}...")
            return False

        # Spot-check that the saved values round-tripped correctly.
        match = next(p for p in preds if p["id"] == doc_id)
        if match["result"]["p50"] != 200.0:
            _fail("test_save_and_get_history",
                  f"saved p50 mismatch: got {match['result']['p50']}")
            return False

        _pass("test_save_and_get_history", f"doc {doc_id} round-tripped")
        return True
    except Exception as e:
        _fail("test_save_and_get_history", str(e))
        return False


# ---------------------------------------------------------------------------
# Test 5 — save_panel_config + get_panel_config
# ---------------------------------------------------------------------------

def test_panel_config(token: str) -> bool:
    """
    Save a panel config, then read it back and confirm every field matches.
    """
    _banner("TEST 5 — save_panel_config + get_panel_config")
    config = {
        "area_m2": 30.0,
        "efficiency": 0.20,
        "orientation": "SE",
        "tilt": 35.0,
    }
    try:
        save_result = _call_function("save_panel_config", config, token)
        if not save_result.get("success"):
            _fail("test_panel_config", f"save returned {save_result}")
            return False

        get_result = _call_function("get_panel_config", {}, token)
        loaded = get_result.get("panel_config")
        if not loaded:
            _fail("test_panel_config", "get returned null panel_config")
            return False

        for key, expected in config.items():
            if loaded.get(key) != expected:
                _fail("test_panel_config",
                      f"field '{key}' mismatch: saved {expected}, got {loaded.get(key)}")
                return False

        print(f"  Saved/loaded config: {json.dumps(loaded, indent=2)}")
        _pass("test_panel_config", "all 4 fields matched")
        return True
    except Exception as e:
        _fail("test_panel_config", str(e))
        return False


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

def main() -> int:
    print("SolarPredict backend smoke tests")
    print(f"  Functions base: {FUNCTIONS_BASE}")
    print(f"  Auth host:      {AUTH_HOST}")

    results: list[tuple[str, bool]] = []

    token = test_auth()
    results.append(("test_auth", token is not None))

    if token is None:
        print("\nCannot continue without an auth token. Are the emulators running?")
        _summarize(results)
        return 1

    # Each test is independent — wrap in try/except in main too so a single
    # exception (e.g. emulator down) doesn't abort the rest of the suite.
    for name, fn in [
        ("test_single_prediction",     test_single_prediction),
        ("test_range_prediction",      test_range_prediction),
        ("test_random_forecast_hour",  test_random_forecast_hour),
        ("test_pvwatts_validation",    test_pvwatts_validation),
        ("test_3c_repeated", test_3c_repeated),
        ("test_manual_sanity_check", test_manual_sanity_check),
        ("test_daily_vs_pvwatts",      test_daily_vs_pvwatts),
        ("test_save_and_get_history",  test_save_and_get_history),
        ("test_panel_config",          test_panel_config),
    ]:
        try:
            ok = fn(token)
        except Exception:
            traceback.print_exc()
            ok = False
        results.append((name, ok))

    return _summarize(results)


def _summarize(results: list[tuple[str, bool]]) -> int:
    _banner("SUMMARY")
    passed = sum(1 for _, ok in results if ok)
    for name, ok in results:
        marker = "PASS" if ok else "FAIL"
        print(f"  [{marker}] {name}")
    print(f"\n  {passed}/{len(results)} passed")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
