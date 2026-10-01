"""Inspect XGBoost quantile models stored as .pkl files WITHOUT unpickling them.

Unpickling a file can run arbitrary code, so this script never does that. It reads the
XGBoost payload out of the pickle stream with `pickletools` (a static parser) and loads
only the booster, then prints:

  - the quantile level the model was trained for
  - the number of trees
  - the real maximum depth of the trees
  - the feature names (and their order)

Usage:
    pip install xgboost
    python inspect_models.py SolarPredict_Backend/functions/models/xgb_quantile_p*.pkl

Note: training hyperparameters such as learning rate or min_child_weight are not stored
reliably in a serialized booster, so they are deliberately not reported here.
"""
import json
import pickletools
import sys
import warnings

import xgboost as xgb

warnings.filterwarnings("ignore")


def load_booster(path):
    with open(path, "rb") as f:
        data = f.read()
    blobs = [
        bytes(arg)
        for _, arg, _ in pickletools.genops(data)
        if isinstance(arg, (bytes, bytearray)) and len(arg) > 1000
    ]
    if not blobs:
        raise ValueError("no XGBoost payload found in this file")
    booster = xgb.Booster()
    booster.__setstate__({"handle": bytearray(max(blobs, key=len))})
    return booster


def max_tree_depth(booster):
    df = booster.trees_to_dataframe()
    children = {
        row.ID: (row.Yes, row.No) for row in df.itertuples() if row.Feature != "Leaf"
    }
    deepest = 0
    for tree in df.Tree.unique():
        stack = [(f"{tree}-0", 0)]
        while stack:
            node, depth = stack.pop()
            if node in children:
                stack.extend((child, depth + 1) for child in children[node])
            else:
                deepest = max(deepest, depth)
    return deepest


def main(paths):
    if not paths:
        print(__doc__)
        return
    for path in paths:
        booster = load_booster(path)
        config = json.loads(booster.save_config())["learner"]
        alpha = config["objective"].get("quantile_loss_param", {}).get("quantile_alpha")
        print(path)
        print(f"  quantile level : {alpha}")
        print(f"  trees          : {booster.num_boosted_rounds()}")
        print(f"  max tree depth : {max_tree_depth(booster)}")
        print(f"  features       : {booster.feature_names}")


if __name__ == "__main__":
    main(sys.argv[1:])
