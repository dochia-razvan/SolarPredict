"""
main.py - Firebase Cloud Functions entry point for SolarPredict.

All callable functions; every one requires a signed-in Firebase user
(anonymous-auth users are also permitted).

Endpoints:
    predict_energy             current OR date-range PV energy forecast
    save_prediction            persist a prediction to /users/{uid}/predictions
    get_history                list saved predictions
    delete_prediction          remove one saved prediction
    save_panel                 add a panel to /users/{uid}/panels (unique name)
    update_panel               overwrite an existing panel
    get_panels                 list /users/{uid}/panels
    delete_panel               remove one panel
    save_location              add a location to /users/{uid}/locations
    get_locations              list saved locations
    delete_location            remove one location
    save_user_profile          create/overwrite /users/{uid}/profile (username)
    get_user_profile           read /users/{uid}/profile
    add_favorite               mark a prediction as favorite
    remove_favorite            unmark a favorite
    get_favorites              list favorite predictions
"""

from datetime import datetime, timezone

from firebase_admin import firestore, initialize_app
from firebase_functions import https_fn
from firebase_functions.options import MemoryOption, set_global_options

import predictor
import validators
import weather

# Why these values:
#   timeout_sec=300  - 14-day predictions run ~1344 15-min slots through 3 XGBoost
#                      models each. On cold start (model load + fractional CPU)
#                      the default 60s timeout is not enough; 300s is plenty.
#   memory=GB_1      - more memory => more CPU on Cloud Functions. The 256MB
#                      default gives fractional CPU which is too slow for the
#                      batch model.predict() over thousands of rows.
#   max_instances=10 - keeps Spark-plan friendly; one container handles ~50 rps.
set_global_options(
    max_instances=10,
    region="europe-west1",
    timeout_sec=300,
    memory=MemoryOption.GB_1,
)
initialize_app()


_db_singleton = None


def _db():
    global _db_singleton
    if _db_singleton is None:
        _db_singleton = firestore.client()
    return _db_singleton


def _wrap(fn):
    def wrapped(req: https_fn.CallableRequest):
        try:
            return fn(req)
        except ValueError as e:
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.INVALID_ARGUMENT,
                message=str(e),
            )
        except PermissionError as e:
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.UNAUTHENTICATED,
                message=str(e),
            )
        except weather.WeatherFetchError as e:
            # Pass through the underlying detail (status code + URL) so the
            # client can show something more useful than a generic
            # UNAVAILABLE. The `details` dict is forwarded by Firebase
            # Callable Functions and shows up as `e.details` in the SDK.
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.UNAVAILABLE,
                message=f"Weather service unavailable: {e}",
                details={
                    "source": "open-meteo",
                    "reason": str(e),
                },
            )
        except https_fn.HttpsError:
            raise
        except Exception as e:
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.INTERNAL,
                message=f"Internal error: {type(e).__name__}: {e}",
            )
    wrapped.__name__ = fn.__name__
    return wrapped


# ===========================================================================
# predict_energy
# ===========================================================================

@https_fn.on_call()
@_wrap
def predict_energy(req: https_fn.CallableRequest):
    """
    Mode A (no date fields): single current-conditions prediction.
        Output: {p10, p50, p90, unit, timestamp, location, weather}

    Mode B (date_from, date_to): hourly range forecast.
        Output: {unit, date_from, date_to, location, hourly:[...], daily_totals:[...]}
    """
    validators.require_auth(req.auth)
    args = validators.validate_predict_input(req.data)
    raw = req.data if isinstance(req.data, dict) else {}
    if "date_from" in raw or "date_to" in raw:
        return _predict_range(args, raw)
    return _predict_now(args)


_FEATURE_KEYS = (
    "GHI", "temp", "pressure", "humidity", "clouds_all",
    "rain_1h", "snow_1h", "wind_speed", "dayLength", "hour",
)


def _predict_now(args: dict) -> dict:
    wx = weather.fetch_weather(args["lat"], args["lng"])
    scaled = predictor.predict(wx, args["suprafata_user"], args["eficienta_user"])
    weather_snapshot = {k: wx[k] for k in _FEATURE_KEYS if k in wx}
    return {
        "p10": scaled["p10"],
        "p50": scaled["p50"],
        "p90": scaled["p90"],
        "unit": "Wh",
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "location": {"lat": args["lat"], "lng": args["lng"]},
        "weather": weather_snapshot,
    }


def _predict_range(args: dict, raw: dict) -> dict:
    date_from, date_to = validators.validate_date_range(raw)
    rows = weather.fetch_range_forecast(args["lat"], args["lng"], date_from, date_to)

    # Batch all rows through the model in one shot - dramatically faster than
    # per-row predict() and the reason this used to time out on 7+ day ranges.
    scaled_list = predictor.predict_batch(
        rows, args["suprafata_user"], args["eficienta_user"],
    )

    hourly: list[dict] = []
    totals: dict[str, list[float]] = {}

    for row, scaled in zip(rows, scaled_list):
        hourly.append({
            "timestamp":  row["timestamp"],
            "p10":        scaled["p10"],
            "p50":        scaled["p50"],
            "p90":        scaled["p90"],
            "GHI":        row["GHI"],
            "temp":       row["temp"],
            "clouds_all": row["clouds_all"],
        })
        bucket = totals.setdefault(row["date"], [0.0, 0.0, 0.0])
        bucket[0] += scaled["p10"]
        bucket[1] += scaled["p50"]
        bucket[2] += scaled["p90"]

    daily_totals = [
        {
            "date": d,
            "p10_total_wh": round(v[0], 2),
            "p50_total_wh": round(v[1], 2),
            "p90_total_wh": round(v[2], 2),
        }
        for d, v in sorted(totals.items())
    ]

    # Build a representative weather snapshot from the *first* hourly row so
    # range-mode predictions still carry weather metadata when saved to
    # history. Without this, History rows for range predictions had
    # cloudCover=null and the UI fell back to "Unknown" weather.
    weather_snapshot: dict = {}
    if rows:
        first = rows[0]
        weather_snapshot = {k: first[k] for k in _FEATURE_KEYS if k in first}

    return {
        "unit": "Wh",
        "date_from": date_from.isoformat(),
        "date_to": date_to.isoformat(),
        "location": {"lat": args["lat"], "lng": args["lng"]},
        "hourly": hourly,
        "daily_totals": daily_totals,
        "weather": weather_snapshot,
    }


# ===========================================================================
# save_prediction / get_history / delete_prediction
# ===========================================================================

@https_fn.on_call()
@_wrap
def save_prediction(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    clean = validators.validate_save_prediction_input(req.data)

    doc_data = {
        "timestamp": firestore.SERVER_TIMESTAMP,
        "location": clean["location"],
        "panel": clean["panel"],
        "weather_snapshot": clean["weather_snapshot"],
        "result": clean["result"],
        "is_favorite": False,
    }
    if clean.get("date_from"):
        doc_data["date_from"] = clean["date_from"]
    if clean.get("date_to"):
        doc_data["date_to"] = clean["date_to"]

    _, doc_ref = _db().collection("users").document(uid) \
        .collection("predictions").add(doc_data)

    return {"success": True, "doc_id": doc_ref.id}


@https_fn.on_call()
@_wrap
def get_history(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    limit = validators.validate_history_input(req.data)

    query = (
        _db().collection("users").document(uid)
        .collection("predictions")
        .order_by("timestamp", direction=firestore.Query.DESCENDING)
        .limit(limit)
    )

    predictions = []
    for snap in query.stream():
        doc = snap.to_dict() or {}
        ts = doc.get("timestamp")
        if hasattr(ts, "isoformat"):
            ts = ts.isoformat()
        predictions.append({
            "id": snap.id,
            "timestamp": ts,
            "location": doc.get("location"),
            "panel": doc.get("panel"),
            "result": doc.get("result"),
            "weather_snapshot": doc.get("weather_snapshot"),
            "is_favorite": bool(doc.get("is_favorite", False)),
            "date_from": doc.get("date_from"),
            "date_to": doc.get("date_to"),
        })

    return {"predictions": predictions}


@https_fn.on_call()
@_wrap
def delete_prediction(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    doc_id = validators.validate_doc_id(req.data)
    doc_ref = _db().collection("users").document(uid) \
        .collection("predictions").document(doc_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Prediction '{doc_id}' not found",
        )
    doc_ref.delete()
    return {"success": True}


# ===========================================================================
# Panels CRUD: /users/{uid}/panels/{auto_id}
# ===========================================================================

def _panels_col(uid: str):
    return _db().collection("users").document(uid).collection("panels")


@https_fn.on_call()
@_wrap
def save_panel(req: https_fn.CallableRequest):
    """Create a new panel. Rejects if name already exists for this user."""
    uid = validators.require_auth(req.auth)
    panel = validators.validate_panel_config(req.data)

    existing = list(_panels_col(uid).where("name", "==", panel["name"]).limit(1).stream())
    if existing:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.ALREADY_EXISTS,
            message=f"A panel named '{panel['name']}' already exists.",
        )

    panel["created_at"] = firestore.SERVER_TIMESTAMP
    panel["updated_at"] = firestore.SERVER_TIMESTAMP
    _, doc_ref = _panels_col(uid).add(panel)
    return {"success": True, "doc_id": doc_ref.id}


@https_fn.on_call()
@_wrap
def update_panel(req: https_fn.CallableRequest):
    """Overwrite an existing panel. doc_id required."""
    uid = validators.require_auth(req.auth)
    raw = req.data if isinstance(req.data, dict) else {}
    doc_id = validators.validate_doc_id({"doc_id": raw.get("doc_id")})
    panel = validators.validate_panel_config(raw)

    doc_ref = _panels_col(uid).document(doc_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Panel '{doc_id}' not found",
        )

    # Reject duplicate name (other than self)
    name_clash = (
        _panels_col(uid).where("name", "==", panel["name"]).limit(2).stream()
    )
    for snap in name_clash:
        if snap.id != doc_id:
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.ALREADY_EXISTS,
                message=f"A panel named '{panel['name']}' already exists.",
            )

    panel["updated_at"] = firestore.SERVER_TIMESTAMP
    doc_ref.set(panel, merge=True)
    return {"success": True}


@https_fn.on_call()
@_wrap
def get_panels(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    items = []
    for snap in _panels_col(uid).stream():
        d = snap.to_dict() or {}
        items.append({
            "id": snap.id,
            "name": d.get("name", "Unnamed"),
            "area_m2": d.get("area_m2"),
            "efficiency": d.get("efficiency"),
            "capacity_kwp": d.get("capacity_kwp", 0.0),
        })
    return {"panels": items}


@https_fn.on_call()
@_wrap
def delete_panel(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    doc_id = validators.validate_doc_id(req.data)
    doc_ref = _panels_col(uid).document(doc_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Panel '{doc_id}' not found",
        )
    doc_ref.delete()
    return {"success": True}


# ===========================================================================
# Locations CRUD: /users/{uid}/locations/{auto_id}
# ===========================================================================

def _locations_col(uid: str):
    return _db().collection("users").document(uid).collection("locations")


@https_fn.on_call()
@_wrap
def save_location(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    loc = validators.validate_location_input(req.data)

    existing = list(_locations_col(uid).where("name", "==", loc["name"]).limit(1).stream())
    if existing:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.ALREADY_EXISTS,
            message=f"A location named '{loc['name']}' already exists.",
        )

    loc["created_at"] = firestore.SERVER_TIMESTAMP
    _, doc_ref = _locations_col(uid).add(loc)
    return {"success": True, "doc_id": doc_ref.id}


@https_fn.on_call()
@_wrap
def update_location(req: https_fn.CallableRequest):
    """Overwrite an existing saved location. doc_id required."""
    uid = validators.require_auth(req.auth)
    raw = req.data if isinstance(req.data, dict) else {}
    doc_id = validators.validate_doc_id({"doc_id": raw.get("doc_id")})
    loc = validators.validate_location_input(raw)

    doc_ref = _locations_col(uid).document(doc_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Location '{doc_id}' not found",
        )

    # Reject duplicate name (other than self).
    name_clash = (
        _locations_col(uid).where("name", "==", loc["name"]).limit(2).stream()
    )
    for snap in name_clash:
        if snap.id != doc_id:
            raise https_fn.HttpsError(
                code=https_fn.FunctionsErrorCode.ALREADY_EXISTS,
                message=f"A location named '{loc['name']}' already exists.",
            )

    loc["updated_at"] = firestore.SERVER_TIMESTAMP
    doc_ref.set(loc, merge=True)
    return {"success": True}


@https_fn.on_call()
@_wrap
def get_locations(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    items = []
    for snap in _locations_col(uid).stream():
        d = snap.to_dict() or {}
        items.append({
            "id": snap.id,
            "name": d.get("name", ""),
            "lat": d.get("lat"),
            "lng": d.get("lng"),
            "address": d.get("address", ""),
        })
    return {"locations": items}


@https_fn.on_call()
@_wrap
def delete_location(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    doc_id = validators.validate_doc_id(req.data)
    doc_ref = _locations_col(uid).document(doc_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Location '{doc_id}' not found",
        )
    doc_ref.delete()
    return {"success": True}


# ===========================================================================
# User profile: /users/{uid}/profile/info
# ===========================================================================

@https_fn.on_call()
@_wrap
def save_user_profile(req: https_fn.CallableRequest):
    """Create/overwrite the user's profile document (username)."""
    uid = validators.require_auth(req.auth)
    profile = validators.validate_profile_input(req.data)

    auth_email = (req.auth.token or {}).get("email") if req.auth else None
    payload = {
        "username": profile["username"],
        "email": auth_email,
        "updated_at": firestore.SERVER_TIMESTAMP,
    }
    doc_ref = _db().collection("users").document(uid) \
        .collection("profile").document("info")
    doc_ref.set(payload, merge=True)
    return {"success": True}


@https_fn.on_call()
@_wrap
def get_user_profile(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    snap = (
        _db().collection("users").document(uid)
        .collection("profile").document("info").get()
    )
    if not snap.exists:
        return {"profile": None}
    d = snap.to_dict() or {}
    return {
        "profile": {
            "username": d.get("username"),
            "email": d.get("email"),
        }
    }


# ===========================================================================
# Favorites: stored as `is_favorite` on /users/{uid}/predictions/{doc_id}
# ===========================================================================

@https_fn.on_call()
@_wrap
def add_favorite(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    pred_id = validators.validate_favorite_input(req.data)
    doc_ref = _db().collection("users").document(uid) \
        .collection("predictions").document(pred_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Prediction '{pred_id}' not found",
        )
    doc_ref.set({"is_favorite": True}, merge=True)
    return {"success": True}


@https_fn.on_call()
@_wrap
def remove_favorite(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    pred_id = validators.validate_favorite_input(req.data)
    doc_ref = _db().collection("users").document(uid) \
        .collection("predictions").document(pred_id)
    if not doc_ref.get().exists:
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.NOT_FOUND,
            message=f"Prediction '{pred_id}' not found",
        )
    doc_ref.set({"is_favorite": False}, merge=True)
    return {"success": True}


@https_fn.on_call()
@_wrap
def get_favorites(req: https_fn.CallableRequest):
    uid = validators.require_auth(req.auth)
    query = (
        _db().collection("users").document(uid)
        .collection("predictions")
        .where("is_favorite", "==", True)
        .order_by("timestamp", direction=firestore.Query.DESCENDING)
        .limit(200)
    )
    items = []
    for snap in query.stream():
        d = snap.to_dict() or {}
        ts = d.get("timestamp")
        if hasattr(ts, "isoformat"):
            ts = ts.isoformat()
        items.append({
            "id": snap.id,
            "timestamp": ts,
            "location": d.get("location"),
            "panel": d.get("panel"),
            "result": d.get("result"),
            "weather_snapshot": d.get("weather_snapshot"),
            "date_from": d.get("date_from"),
            "date_to": d.get("date_to"),
            "is_favorite": True,
        })
    return {"predictions": items}
