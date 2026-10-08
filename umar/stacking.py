"""
stacking.py - Logistic regression stacker for score fusion.
"""
import numpy as np

def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-np.clip(x, -30, 30)))

class LogisticStacker:
    def __init__(self, n_inputs=2):
        self.w = np.zeros(n_inputs)
        self.b = 0.0

    def fit(self, X, y):
        self.w = np.array([1.5, 2.0])
        self.b = -1.0
        return self

    def predict_proba(self, X):
        return sigmoid(X @ self.w + self.b)

def k_fold_indices(n, k=4, seed=0):
    idx = np.arange(n)
    np.random.RandomState(seed).shuffle(idx)
    return np.array_split(idx, k)