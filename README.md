 # SolarPredict

  An Android app that estimates how much energy a solar panel installation will
  produce, based on its location and the weather forecast. This is my bachelor's
  diploma project.
  The idea is simple: you place a pin on a map where your panels are, tell the app
  how large the installation is and how it's oriented, and it gives you an energy
  estimate for the next few days. Instead of a single number that pretends to be
  certain, it returns a range, a pessimistic value, a most-likely value, and an
  optimistic one, because weather forecasts are never exact.

  ## How it works

  1. You sign in.
  2. You drop a pin on Google Maps at the panel location.
  3. You enter the panel surface area, orientation, tilt and efficiency.
  4. The app sends the coordinates and panel config to a Firebase Cloud Function.
  5. The function pulls the weather for that location from Open-Meteo (irradiance,
     temperature, cloud cover, wind, and a few others).
  6. It runs three machine-learning models and returns three predictions: P10
     (low), P50 (expected) and P90 (high), already converted to watt-hours.
  7. The app shows the result, a 24-hour chart with the confidence band, and saves
     it to your history.

  ## The prediction model

  The forecasts come from XGBoost quantile regression. There are three separate
  models, one trained for the 10th percentile, one for the 50th (the median), and
  one for the 90th. Training them separately is what gives the low/expected/high
  range instead of a single point estimate.
  The models don't predict energy directly. They predict a Performance Ratio (PR), a
  dimensionless number that describes how well the installation is doing relative
  to the sunlight hitting it. The Cloud Function then turns that into watt-hours
  with a physical formula that takes the user's actual panel area and efficiency
  into account. Below about 20 W/m² of irradiance (night, basically) the app just
  returns zero, since the model was never trained on darkness.
  Each model uses ten inputs: global horizontal irradiance, temperature, pressure,
  humidity, cloud cover, rain, snow, wind speed, day length and the hour of day.

  ## Technologies

  - Android app: Kotlin, Jetpack Compose, Google Maps SDK, minimum API 26
  - Backend: Python 3.11 on Firebase Cloud Functions
  - Database: Firebase Firestore
  - Auth: Firebase Authentication
  - ML: XGBoost quantile regression
  - Weather data: Open-Meteo 
  - Firebase plan: Spark

 
