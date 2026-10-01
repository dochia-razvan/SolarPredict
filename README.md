# SolarPredict

An Android app that forecasts how much energy a solar panel installation will produce, and shows an uncertainty band instead of a single number. Bachelor's diploma project, University POLITEHNICA of Bucharest, Faculty of Automatic Control and Computers (2026).

<!-- TODO: add 2-3 real app screenshots (docs/screenshots/) and link them here. The thesis has good ones (map, panel config, result with P10-P90 band, history). -->

## What it does

- Pick a location on a map (or search an address) and describe a single panel or a multi-panel system (area, efficiency, quantity).
- **14-day forecast** at 15-minute resolution: expected value (P50) with a P10-P90 band, daily totals, chart resolution from 15 minutes to 1 day.
- **Typical-year estimate** built from about 5 years of historical weather: monthly and annual totals.
- History, favourites, and saved panels, systems and locations. Estimated savings and avoided CO2 from your own price/kWh and CO2 factor.
- Email, Google or guest sign-in (guest data stays on the phone). English/Romanian UI, switchable at runtime. Blocks the UI while offline.

## Architecture

Three tiers: **Android client** (Kotlin, Jetpack Compose, MVVM) -> **Firebase Cloud Functions** (Python 3.11, `europe-west1`) -> **Cloud Firestore** + **Open-Meteo**.

The client contains no model and never reads Firestore directly. Every call goes through a Cloud Function that checks authentication and only touches `/users/{uid}`; Firestore security rules (owner-only) are a second, independent barrier. The backend exposes 25 callable functions, validates all inputs, and runs the three models in one batched call per quantile so a 14-day forecast (1345 intervals) fits in the function timeout.

## The model

- **Data:** public Kaggle dataset [Renewable Power Generation and Weather Conditions](https://www.kaggle.com/datasets/pythonafroz/renewable-power-generation-and-weather-conditions), 15-minute PV production and weather, Jan 2017 - Aug 2022. About 196.8k rows, about 195.9k after removing physically impossible rows.
- **Target:** Performance Ratio (IEC 61724-1), not raw energy. The dataset does not include panel area, efficiency or location, so predicting PR lets one model serve any panel: `E = PR * GHI * A_user * eta_user * dt`. Below 20 W/m2 of irradiance the app returns 0 without calling the models.
- **Inputs (10):** global horizontal irradiance, temperature, pressure, humidity, cloud cover, rain, snow, wind speed, day length, hour of day.
- **Production model:** three XGBoost quantile regressors (0.10 / 0.50 / 0.90, pinball loss; 600 trees, max depth 4).

### Results (chronological 80/20 split)

Benchmark on raw energy (Wh), five models:

| Model | MAE (Wh) | R2 |
|---|---|---|
| Random Forest | 127.11 | 0.93 |
| XGBoost | 128.94 | 0.93 |
| ARMAX | 244.19 | 0.85 |
| ARX+ARMA hybrid | 254.29 | 0.85 |
| Linear regression | 254.31 | 0.85 |

All pairwise differences are statistically significant (Diebold-Mariano, p < 0.01). Random Forest and XGBoost are effectively tied on point accuracy; XGBoost was chosen because it optimizes the quantile loss directly.

Production quantile model (predicts PR, scaled to energy): P50 MAE 244.2 Wh, R2 0.89. The P10-P90 band covers **76.9%** of test points against an 80% nominal target, so it is slightly under-covered.

### Validation

- **NREL PVWatts, five scenarios:** four within about 5%, mean absolute difference about 7%. A clear winter noon is overestimated by about 27%.
- **PVGIS annual totals, seven cities:** the app is higher in all seven (+4% to +47%, mean about +28%), and the gap grows in hot, high-irradiance climates. The model does not explicitly apply temperature and system losses.

## Provenance of the models and the notebook

- The model files in `SolarPredict_Backend/functions/models/` are the final thesis models: quantile levels 0.10 / 0.50 / 0.90, 600 trees, max depth 4. You can verify this without running them: `python inspect_models.py SolarPredict_Backend/functions/models/xgb_quantile_p*.pkl`.
- The notebook in [`ml/`](ml/) trains an **earlier** configuration (0.05 / 0.50 / 0.95, max depth 6). The notebook that produced the final models was lost in a hard-drive failure, so the numbers above come from the thesis, not from the stored notebook outputs.
- This repository is a snapshot of the backend and Android client from May 2026. The submitted thesis version adds a one-year estimate, saved multi-panel systems, guest mode and price/CO2 preferences.

## Limitations

- The training set is anonymous and its daytime irradiance peaks at about 229 W/m2, far below real clear-sky values (about 1000 W/m2). Predicting PR is a way to work around this, but the transfer to real installations is argued physically, not tested on a measured installation.
- The reference constants used to define PR (410 m2, 18% efficiency) are assumed, not measured.
- Panel orientation and tilt are not model inputs.
- The 14-day forecast is only validated qualitatively (no free numeric reference exists).

## Tech stack

Kotlin, Jetpack Compose, Material 3, Navigation Compose, Google Maps Compose and Places SDK, Vico charts, DataStore | Python 3.11, Firebase Cloud Functions, Firestore, Firebase Authentication | XGBoost, scikit-learn, statsmodels, pandas, NumPy, SciPy | Open-Meteo API. Firebase **Blaze** plan (required for outbound calls to the weather API).

## Running it

<!-- TODO: double-check these commands against your final setup before publishing. -->

1. **Android:** add `GOOGLE_MAPS_API_KEY=<your key>` to `SolarPredict_Android/local.properties` (never committed) and put your own `google-services.json` in `SolarPredict_Android/app/`. Minimum API 26.
2. **Backend:** `cd SolarPredict_Backend/functions && pip install -r requirements.txt`, then `firebase emulators:start` to run Auth, Functions and Firestore locally.
3. **Tests:** with the emulators running, `python test_backend.py` calls every callable function with valid and invalid inputs.
4. **Deploy:** `firebase deploy --only functions` (region `europe-west1`, must match the client).

## Training code

An earlier training notebook is in [`ml/`](ml/); see "Provenance" above for how it relates to the final models.

## Thesis

The full thesis (in Romanian): [`docs/thesis_ro.pdf`](docs/thesis_ro.pdf).
