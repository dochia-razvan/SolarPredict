"""
predictor.py — XGBoost quantile model loader + inference for SolarPredict.

The 3 models predict the Performance Ratio (PR) — a dimensionless number,
typically in [0.5, 1.2] — for a 15-minute interval. Energy in Wh is then
computed PHYSICALLY (no learned area/efficiency scaling):

    energy_15min_wh = PR * GHI * area_user * eff_user * 0.25

where 0.25 h is the 15-minute interval (TIMP_INTERVAL from the training
dataset). The product GHI * area * eff has units of watts; multiplying by
hours yields watt-hours.

Quantile naming: the underlying models were fit at quantiles 0.05 / 0.50 / 0.95.
We continue to expose them as p10 / p50 / p90 in the public API so the Android
client doesn't need to rename anything — the labels are conventional, only the
math under the hood changed.

Night / dawn / dusk short-circuit:
    The PR models were trained ONLY on samples with GHI > 20 W/m². Below that
    threshold we return zeros without calling the models — both physically
    correct (no irradiance => no production) and a safety guard against
    extrapolation outside the training distribution.

Why module-level model loading:
    Cold start cost (loading 3 XGBoost models) is paid once per container.
    Warm invocations reuse the loaded objects — typical Cloud Function pattern.
"""

import json
import os
from typing import Any

import joblib
import numpy as np


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# 15-minute interval expressed in hours — the dataset's TIMP_INTERVAL constant.
# Used to convert instantaneous power (W) into energy (Wh) per slot.
TIMP_INTERVAL_H = 0.25

# Below this irradiance the model is undefined (out of training distribution)
# AND production is effectively zero. Short-circuit to {0,0,0}.
GHI_NIGHT_THRESHOLD_WM2 = 20.0

# Resolve model paths relative to THIS file, not the CWD. Cloud Functions can
# launch from arbitrary working directories.
_MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")
_FEATURES_PATH = os.path.join(_MODELS_DIR, "features_list.json")
_MODEL_PATHS = {
    "p10": os.path.join(_MODELS_DIR, "xgb_quantile_p10.pkl"),
    "p50": os.path.join(_MODELS_DIR, "xgb_quantile_p50.pkl"),
    "p90": os.path.join(_MODELS_DIR, "xgb_quantile_p90.pkl"),
}


# ---------------------------------------------------------------------------
# Lazy singletons — populated on first call to _ensure_loaded()
# ---------------------------------------------------------------------------
_MODELS: dict[str, Any] | None = None
_FEATURE_ORDER: list[str] | None = None


def _ensure_loaded() -> None:
    """
    Load the feature list and the 3 XGBoost models on first use.
    Subsequent calls are no-ops. Safe to call from every prediction request.
    """
    global _MODELS, _FEATURE_ORDER

    if _FEATURE_ORDER is None:
        with open(_FEATURES_PATH, "r", encoding="utf-8") as f:
            meta = json.load(f)
        _FEATURE_ORDER = list(meta["features"])

    if _MODELS is None:
        _MODELS = {key: joblib.load(path) for key, path in _MODEL_PATHS.items()}


def _build_feature_vector(weather: dict[str, float]) -> np.ndarray:
    """
    Assemble the input row in the EXACT order from features_list.json.

    Returns a (1, 10) numpy array — the shape XGBoost expects for a single
    prediction. Raises KeyError if the weather dict is missing a feature, which
    indicates a bug in weather.py rather than a user error.
    """
    assert _FEATURE_ORDER is not None  # _ensure_loaded() must run first
    row = [weather[feat] for feat in _FEATURE_ORDER]
    return np.array([row], dtype=np.float32)


def _pr_to_energy(pr: float, ghi: float, area: float, eff: float) -> float:
    """
    Convert a Performance Ratio prediction to energy (Wh) for one 15-min slot.

    PR is dimensionless. The product (GHI * area * eff) is the instantaneous
    power in watts (W/m² * m² * decimal). Multiplying by TIMP_INTERVAL_H
    (0.25 h) yields Wh over the 15-minute interval — same semantics as the
    dataset's `Energy delta[Wh]` target.
    """
    return pr * ghi * area * eff * TIMP_INTERVAL_H


def predict_batch(
    rows: list[dict[str, float]],
    suprafata_user: float,
    eficienta_user: float,
) -> list[dict[str, float]]:
    """
    Run inference for many 15-minute slots in one shot.

    Why: each model.predict() call has fixed per-call overhead (DMatrix build,
    Python-C boundary). For a 14-day forecast (~1344 rows × 3 models = ~4000
    individual calls in the per-row loop), that overhead dominates total runtime
    and used to push us past the Cloud Function timeout. Building one numpy
    matrix and calling model.predict() exactly 3 times is 10-50x faster.

    Behaviour matches predict():
      - GHI <= GHI_NIGHT_THRESHOLD_WM2 returns zeros without invoking the model
      - quantile band is clamped so p10 <= p50 <= p90
      - all values rounded to 2 decimals (Wh)

    Returns a list with the same length as `rows`, each entry {p10, p50, p90}.
    """
    if not rows:
        return []

    _ensure_loaded()
    assert _MODELS is not None and _FEATURE_ORDER is not None

    # Split rows into "daylight" (need model inference) vs "night" (zero by
    # definition). We only build a matrix and call the model on daylight rows.
    daylight_indices: list[int] = []
    daylight_features: list[list[float]] = []
    daylight_ghi: list[float] = []
    results: list[dict[str, float]] = [
        {"p10": 0.0, "p50": 0.0, "p90": 0.0} for _ in rows
    ]

    for i, row in enumerate(rows):
        ghi = float(row.get("GHI") or 0.0)
        if ghi <= GHI_NIGHT_THRESHOLD_WM2:
            continue
        daylight_indices.append(i)
        daylight_features.append([row[feat] for feat in _FEATURE_ORDER])
        daylight_ghi.append(ghi)

    if not daylight_indices:
        return results

    x = np.array(daylight_features, dtype=np.float32)

    # ONE model.predict() call per quantile, regardless of row count.
    pr_arrays = {q: model.predict(x) for q, model in _MODELS.items()}

    for j, idx in enumerate(daylight_indices):
        ghi = daylight_ghi[j]
        energy = {
            q: max(0.0, _pr_to_energy(
                float(pr_arrays[q][j]), ghi, suprafata_user, eficienta_user,
            ))
            for q in ("p10", "p50", "p90")
        }
        # Enforce monotonicity of the quantile band per slot.
        energy["p50"] = max(energy["p50"], energy["p10"])
        energy["p90"] = max(energy["p90"], energy["p50"])
        results[idx] = {q: round(v, 2) for q, v in energy.items()}

    return results


def predict(
    weather: dict[str, float],
    suprafata_user: float,
    eficienta_user: float,
) -> dict[str, float]:
    """
    Run inference for a SINGLE 15-minute interval.

    Pipeline:
        1. Read GHI from the weather dict.
        2. If GHI <= GHI_NIGHT_THRESHOLD_WM2 (20 W/m²): short-circuit to zeros
           — no model invocation, no compute. The model is undefined here
           (trained only on GHI > 20) and physically there's no production.
        3. Otherwise: build the 10-feature vector and run all 3 PR models.
        4. Convert each PR to Wh via `pr * GHI * area * eff * 0.25`.
        5. Floor at 0 and enforce p10 <= p50 <= p90 so the confidence band
           shown on the Android chart never inverts.

    Args:
        weather:        10-feature dict (must include 'GHI')
        suprafata_user: panel area in m² (validated > 0)
        eficienta_user: panel efficiency as decimal in (0, 1]

    Returns:
        { p10, p50, p90 } — all in Wh for ONE 15-minute interval, rounded to 2dp.
    """
    ghi = float(weather.get("GHI") or 0.0)

    # Night / dawn / dusk fast path. Saves 3 model.predict() calls per slot,
    # which adds up across a 14-day forecast (~1344 slots, ~half are dark).
    if ghi <= GHI_NIGHT_THRESHOLD_WM2:
        return {"p10": 0.0, "p50": 0.0, "p90": 0.0}

    _ensure_loaded()
    assert _MODELS is not None

    x = _build_feature_vector(weather)

    # Each model.predict returns shape (1,) — pull out the scalar PR value.
    pr = {key: float(model.predict(x)[0]) for key, model in _MODELS.items()}

    # PR -> Wh for each quantile, using the physical conversion.
    energy = {
        q: _pr_to_energy(pr[q], ghi, suprafata_user, eficienta_user)
        for q in ("p10", "p50", "p90")
    }

    # Floor at 0 — quantile regressors can occasionally undershoot into
    # negative PR; energy can't be negative.
    for q in energy:
        energy[q] = max(0.0, energy[q])

    # Enforce monotonicity of the quantile band AFTER conversion. Clamping in
    # PR-space wouldn't be enough because the conversion is monotone in PR
    # (positive multiplier) but the 3 models are independent.
    energy["p50"] = max(energy["p50"], energy["p10"])
    energy["p90"] = max(energy["p90"], energy["p50"])

    # Round to 2 decimals — Wh resolution finer than that is meaningless for the UI.
    return {q: round(v, 2) for q, v in energy.items()}
