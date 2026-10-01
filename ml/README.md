# ml/ - training notebook (earlier run)

`solarpredict_training.ipynb` is a Google Colab notebook. It reads the dataset from Google Drive
(`/content/drive/MyDrive/ML_proiect_licenta/Renewable.csv`), cleans it, benchmarks five models,
trains three XGBoost quantile models on Performance Ratio, and exports the model files.

## Important: this is NOT the notebook behind the final thesis models

| | This notebook (earlier run) | Final models in `SolarPredict_Backend/functions/models/` (thesis) |
|---|---|---|
| Quantile levels | 0.05 / 0.50 / 0.95 | 0.10 / 0.50 / 0.90 |
| Max tree depth | 6 | 4 |
| Trees | 600 | 600 |

The notebook that produced the final models was lost in a hard-drive failure. This one trains the
earlier configuration, which is also what the first public snapshot of this repository contained.
The results reported in the thesis come from the final models, not from the outputs stored here.

You can check what any model file contains without running it:

    pip install xgboost
    python ../inspect_models.py ../SolarPredict_Backend/functions/models/xgb_quantile_p*.pkl

## Figures and tables

- `figures/` holds 23 charts exported from the final thesis run (data cleaning, PR distribution, daily and seasonal profiles, correlations, overfitting and learning curves, model comparison, Diebold-Mariano test, feature importance, residual analysis, quantile bands, calibration).
- `results/` holds the matching tables as CSV: data cleaning, model metrics, overfitting, Diebold-Mariano p-values, quantile calibration, and the PVWatts comparison. They correspond to the tables in Chapter 4 of the thesis.
- The notebook in this folder is an earlier run and only regenerates part of them (16 charts, 2 tables).


## Known quirks in the stored outputs

- Cell 16 compares the band's hit rate (84.3%) to an 80% target and prints "EXCELENTA". For a 5-95
  band the nominal level is 90%, so that run actually under-covers.
- Charts in cell 17 are titled "P10-P90" but use the 5th and 95th percentile models.
- `Renewable.csv` is already cleaned, so the cleaning cell reports 0 rows removed.

## Data

Not included. Download the public Kaggle dataset
[Renewable Power Generation and Weather Conditions](https://www.kaggle.com/datasets/pythonafroz/renewable-power-generation-and-weather-conditions)
and check its license on that page before reusing it.

Large benchmark models (ARMAX, hybrid, Random Forest) are not committed: several exceed GitHub's
100 MB file limit. Re-run the notebook to regenerate them.
