# SolarPredict

An Android app that forecasts how much energy a solar panel installation will produce, and shows an uncertainty band instead of a single number. Bachelor's diploma project, University POLITEHNICA of Bucharest, Faculty of Automatic Control and Computers (2026).

## What it does

- Pick a location on a Google map (tap to place a pin) or search an address (Places autocomplete). Save locations for reuse.
- Describe a panel by its area and efficiency. Save panels for reuse.
- **14-day forecast** at 15-minute resolution: expected value (P50) with a P10-P90 band and daily totals. Choose the time range (next 24 h, 7 days, 14 days or custom) and switch the chart between hourly and daily views.
- History of saved predictions, favourites, and a read-only detail view.
- Email/password sign-in, or **guest mode** (anonymous Firebase account; guest data stays on the phone). English/Romanian UI, switchable in settings.

## Architecture

Three tiers: **Android client** (Kotlin, Jetpack Compose, MVVM) -> **Firebase Cloud Functions** (Python 3.11, `europe-west1`) -> **Cloud Firestore** + **Open-Meteo**.

The client contains no model and never reads Firestore directly. Every call goes through a Cloud Function that checks authentication and only touches `/users/{uid}`; Firestore security rules (owner-only) are a second, independent barrier. The backend exposes 17 callable functions, validates all inputs, and runs the three models in one batched call per quantile so a 14-day forecast (about 1,340 intervals) fits in the function timeout.

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
- This repository is a snapshot of the backend and Android client from May 2026. The final submitted version also has a one-year estimate from historical weather, saved multi-panel systems, Google sign-in, price/CO2 savings preferences, an offline screen and Vico-based charts. Those parts are not in this repository.

## Limitations

- The training set is anonymous and its daytime irradiance peaks at about 229 W/m2, far below real clear-sky values (about 1000 W/m2). Predicting PR is a way to work around this, but the transfer to real installations is argued physically, not tested on a measured installation.
- The reference constants used to define PR (410 m2, 18% efficiency) are assumed, not measured.
- Panel orientation and tilt are not model inputs.
- The 14-day forecast is only validated qualitatively (no free numeric reference exists).

## Tech stack

Kotlin, Jetpack Compose, Material 3, Navigation Compose, Google Maps Compose and Places SDK, DataStore, custom Compose Canvas charts | Python 3.11, Firebase Cloud Functions, Firestore, Firebase Authentication | XGBoost, scikit-learn, statsmodels, pandas, NumPy, SciPy | Open-Meteo API. Firebase **Blaze** plan (required for outbound calls to the weather API).

## Running it

1. **Android:** add `GOOGLE_MAPS_API_KEY=<your key>` to `SolarPredict_Android/local.properties` (never committed) and put your own `google-services.json` in `SolarPredict_Android/app/`. Minimum API 26.
2. **Backend:** `cd SolarPredict_Backend/functions && pip install -r requirements.txt`, then from `SolarPredict_Backend` run `firebase emulators:start` to start Auth, Functions and Firestore locally.
3. **Tests:** with the emulators running, `python functions/test_backend.py` (from `SolarPredict_Backend`) calls every callable function with valid and invalid inputs. The PVWatts check needs a free NREL API key: replace `YOUR_NREL_API_KEY` in `test_backend.py` (get one at developer.nrel.gov).
4. **Deploy:** `firebase deploy --only functions` (region `europe-west1`, must match the client).

## Training code

An earlier training notebook is in [`ml/`](ml/); see "Provenance" above for how it relates to the final models.

