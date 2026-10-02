An Android app that estimates how much energy a solar panel will produce at a chosen location, using live weather data. Instead of a single number, the app gives a low, an expected and a high estimate for every 15 minutes over the next 14 days. It is my diploma project.

A short video of the final version of the app: [watch the demo](https://drive.google.com/file/d/1dFUF1NBbRNr3dUdUgMyUi5-Rt8rsnY03/view?usp=sharing)

The user picks a location on a map or searches for an address, enters the area and the efficiency of the panel, and gets the forecast as a chart with daily totals. Predictions can be saved in a history, marked as favorites and opened again later. The app has an English and a Romanian interface and can also be used as a guest, without an account.

The app is written in Kotlin with Jetpack Compose, using the MVVM pattern. It does not run any model on the phone. It calls a backend made of Python Firebase Cloud Functions, which gets the weather from Open-Meteo, runs the models and sends the result back. The saved predictions, panels and locations are stored in Cloud Firestore, and every user can only access their own data.

The forecast comes from three XGBoost models trained on a public dataset of solar production and weather (about 196,000 records at 15-minute intervals): one for the low estimate, one for the expected one and one for the high one. The models predict the performance ratio of the panel instead of the raw energy, and the result is then multiplied by the size and efficiency of the user's panel, so the same model works for any panel. I compared XGBoost with linear regression, ARMAX, a hybrid model and Random Forest. The tree models reached an R2 of 0.93 against 0.85 for the linear ones, and the range between the low and the high estimate contains about 77% of the real values (the target was 80%).

I checked the results against NREL PVWatts, where the average difference was about 7% on five test cases, and against PVGIS, where the app gives higher yearly estimates in all seven cities I tested, by about 28% on average. The most likely reason is that my model does not account for temperature and system losses. The backend also has automated tests that run against the local Firebase emulators.

The model was never tested on a real installation, because the dataset has no information about the panel and its sunniest readings are weaker than real sunshine. The orientation of the panel is not used either.

This repository contains an earlier version of the project, from May 2026. The final version, which is the one in the video, also has a one-year estimate, saved multi-panel systems, Google sign-in, savings calculations and an offline screen. The notebook in the ml folder is also from an earlier training run, so its numbers are a little different from the final models in the backend. The script inspect_models.py prints the settings stored in a model file.

Android: Kotlin, Jetpack Compose, Google Maps and Places

Backend: Python, Firebase Cloud Functions, Cloud Firestore, Firebase Authentication, Open-Meteo API

Machine learning: Python, XGBoost, scikit-learn, statsmodels, pandas, NumPy
