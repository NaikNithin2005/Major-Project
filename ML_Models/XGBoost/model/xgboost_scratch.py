"""
xgboost_scratch.py
A from-scratch implementation of gradient-boosted decision trees following
the XGBoost formulation (2nd-order Taylor approximation of the loss, exact
greedy split finding using gradient/hessian sums, L2 leaf regularization).
No xgboost / lightgbm / sklearn.ensemble used.

Reference formulation (Chen & Guestrin, 2016):
  For a set of samples I in a leaf, the optimal leaf weight is:
      w* = -sum(g_i) / (sum(h_i) + lambda)
  and the loss reduction ("gain") for splitting I into I_L, I_R is:
      gain = 0.5 * [ (sum(g_L)^2)/(sum(h_L)+lambda)
                    + (sum(g_R)^2)/(sum(h_R)+lambda)
                    - (sum(g)^2)/(sum(h)+lambda) ]  - gamma
  where g_i = dL/dy_pred_i, h_i = d2L/dy_pred_i^2 (gradient/hessian of the
  loss at the current prediction), lambda = L2 reg, gamma = min gain to split.
"""

import numpy as np


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-np.clip(x, -30, 30)))


# Logistic loss gradient/hessian (binary classification)
def logloss_grad_hess(y_true, y_pred_raw):
    """
    y_true: 0/1 labels
    y_pred_raw: current raw score (pre-sigmoid, i.e. sum of tree outputs)
    Returns gradient g and hessian h of binary cross-entropy w.r.t. y_pred_raw.
    """
    p = sigmoid(y_pred_raw)
    g = p - y_true                 # dL/dy_pred
    h = p * (1.0 - p)              # d2L/dy_pred^2
    h = np.clip(h, 1e-6, None)     # avoid zero hessian
    return g, h


# A single regression tree, split on gradient/hessian statistics
class TreeNode:
    __slots__ = ["feature_idx", "threshold", "left", "right", "leaf_value", "is_leaf"]

    def __init__(self):
        self.feature_idx = None
        self.threshold = None
        self.left = None
        self.right = None
        self.leaf_value = None
        self.is_leaf = False


class RegressionTree:
    def __init__(self, max_depth=4, min_child_weight=1.0, lam=1.0, gamma=0.0,
                 n_candidate_thresholds=20):
        self.max_depth = max_depth
        self.min_child_weight = min_child_weight  # min sum(h) allowed in a leaf
        self.lam = lam                              # L2 regularization on leaf weight
        self.gamma = gamma                          # min gain required to split
        self.n_candidate_thresholds = n_candidate_thresholds
        self.root = None

    def _leaf_weight(self, g_sum, h_sum):
        return -g_sum / (h_sum + self.lam)

    def _gain(self, g_l, h_l, g_r, h_r):
        def term(g, h):
            return (g * g) / (h + self.lam)
        g_total, h_total = g_l + g_r, h_l + h_r
        return 0.5 * (term(g_l, h_l) + term(g_r, h_r) - term(g_total, h_total)) - self.gamma

    def _best_split(self, X, g, h, feature_indices):
        """
        Exact-ish greedy split search. For speed on continuous features we
        sample candidate thresholds from the unique sorted values rather than
        testing literally every split point (this is a simplified version of
        XGBoost's quantile sketch, done manually).
        """
        best_gain = -np.inf
        best_feat = None
        best_thresh = None

        n_samples, n_features = X.shape

        for f in feature_indices:
            col = X[:, f]
            uniq_vals = np.unique(col)
            if len(uniq_vals) <= 1:
                continue

            if len(uniq_vals) > self.n_candidate_thresholds:
                quantiles = np.linspace(0, 1, self.n_candidate_thresholds + 2)[1:-1]
                candidates = np.quantile(uniq_vals, quantiles)
                candidates = np.unique(candidates)
            else:
                # midpoints between consecutive unique values
                candidates = (uniq_vals[:-1] + uniq_vals[1:]) / 2.0

            for thresh in candidates:
                left_mask = col <= thresh
                right_mask = ~left_mask

                h_l = h[left_mask].sum()
                h_r = h[right_mask].sum()
                if h_l < self.min_child_weight or h_r < self.min_child_weight:
                    continue

                g_l = g[left_mask].sum()
                g_r = g[right_mask].sum()

                gain = self._gain(g_l, h_l, g_r, h_r)
                if gain > best_gain:
                    best_gain = gain
                    best_feat = f
                    best_thresh = thresh

        return best_feat, best_thresh, best_gain

    def _build(self, X, g, h, depth):
        node = TreeNode()
        g_sum, h_sum = g.sum(), h.sum()

        if depth >= self.max_depth or len(g) < 2 * self.min_child_weight:
            node.is_leaf = True
            node.leaf_value = self._leaf_weight(g_sum, h_sum)
            return node

        n_features = X.shape[1]
        feature_indices = np.arange(n_features)
        best_feat, best_thresh, best_gain = self._best_split(X, g, h, feature_indices)

        if best_feat is None or best_gain <= 0:
            node.is_leaf = True
            node.leaf_value = self._leaf_weight(g_sum, h_sum)
            return node

        left_mask = X[:, best_feat] <= best_thresh
        right_mask = ~left_mask

        node.feature_idx = best_feat
        node.threshold = best_thresh
        node.left = self._build(X[left_mask], g[left_mask], h[left_mask], depth + 1)
        node.right = self._build(X[right_mask], g[right_mask], h[right_mask], depth + 1)
        return node
    def fit(self, X, g, h):
        self.root = self._build(X, g, h, depth=0)
        return self


    def _predict_one(self, x, node):
        if node.is_leaf:
            return node.leaf_value
        if x[node.feature_idx] <= node.threshold:
            return self._predict_one(x, node.left)
        return self._predict_one(x, node.right)

    def predict(self, X):
        return np.array([self._predict_one(x, self.root) for x in X])


# Boosting ensemble
class XGBoostScratch:
    def __init__(self, n_estimators=50, max_depth=4, learning_rate=0.1,
                 lam=1.0, gamma=0.0, min_child_weight=1.0, subsample=1.0,
                 n_candidate_thresholds=20, random_state=42):
        self.n_estimators = n_estimators
        self.max_depth = max_depth
        self.lr = learning_rate
        self.lam = lam
        self.gamma = gamma
        self.min_child_weight = min_child_weight
        self.subsample = subsample
        self.n_candidate_thresholds = n_candidate_thresholds
        self.trees = []
        self.base_score = 0.0
        self.rng = np.random.RandomState(random_state)

    def fit(self, X, y, verbose=True):
        X = np.asarray(X, dtype=np.float64)
        y = np.asarray(y, dtype=np.float64)
        n_samples = X.shape[0]

        # initialize raw prediction with log-odds of the base rate
        pos_rate = np.clip(y.mean(), 1e-6, 1 - 1e-6)
        self.base_score = np.log(pos_rate / (1 - pos_rate))
        y_pred_raw = np.full(n_samples, self.base_score)

        self.trees = []
        for m in range(self.n_estimators):
            g, h = logloss_grad_hess(y, y_pred_raw)

            if self.subsample < 1.0:
                sample_idx = self.rng.choice(
                    n_samples, size=int(n_samples * self.subsample), replace=False
                )
            else:
                sample_idx = np.arange(n_samples)

            tree = RegressionTree(
                max_depth=self.max_depth,
                min_child_weight=self.min_child_weight,
                lam=self.lam,
                gamma=self.gamma,
                n_candidate_thresholds=self.n_candidate_thresholds,
            )
            tree.fit(X[sample_idx], g[sample_idx], h[sample_idx])
            update = tree.predict(X)
            y_pred_raw += self.lr * update
            self.trees.append(tree)

            if verbose and (m % max(1, self.n_estimators // 10) == 0 or m == self.n_estimators - 1):
                p = sigmoid(y_pred_raw)
                loss = -np.mean(y * np.log(p + 1e-9) + (1 - y) * np.log(1 - p + 1e-9))
                acc = np.mean((p > 0.5) == y)
                print(f"round {m:3d}  logloss={loss:.4f}  acc={acc:.3f}")

        return self

    def predict_raw(self, X):
        X = np.asarray(X, dtype=np.float64)
        y_pred_raw = np.full(X.shape[0], self.base_score)
        for tree in self.trees:
            y_pred_raw += self.lr * tree.predict(X)
        return y_pred_raw

    def predict_proba(self, X):
        return sigmoid(self.predict_raw(X))

    def predict(self, X, threshold=0.5):
        return (self.predict_proba(X) >= threshold).astype(int)

    def feature_importance(self, n_features):
        """Count how often each feature index is used as a split, across all trees."""
        counts = np.zeros(n_features)

        def walk(node):
            if node is None or node.is_leaf:
                return
            counts[node.feature_idx] += 1
            walk(node.left)
            walk(node.right)

        for tree in self.trees:
            walk(tree.root)
        return counts


if __name__ == "__main__":
    import sys
    sys.path.insert(0, ".")
    from features import featurize_to_vector, FEATURE_ORDER

    legit_urls = [
        "https://www.paypal.com/signin",
        "https://accounts.google.com/login",
        "https://www.amazon.com/gp/cart",
        "https://www.hdfcbank.com/personal",
        "https://www.netflix.com/browse",
        "https://mail.google.com/mail/u/0",
        "https://www.icicibank.com/login",
        "https://web.whatsapp.com/",
    ]
    malicious_urls = [
        "http://paypa1-secure-login.tk/verify",
        "http://192.168.10.4/login.php?id=1",
        "http://free-gift-claim-now.xyz/win",
        "http://amaz0n-account-locked.top/reset",
        "http://secure-icicibank-verify.ga/otp",
        "http://bit.ly/3xQz9fake",
        "http://netfl1x-billing-update.click/pay",
        "http://appleid-locked-verify-now.work/login",
    ]

    X = np.array([featurize_to_vector(u) for u in legit_urls + malicious_urls])
    y = np.array([0] * len(legit_urls) + [1] * len(malicious_urls))

    model = XGBoostScratch(n_estimators=30, max_depth=3, learning_rate=0.3,
                            lam=1.0, gamma=0.0, min_child_weight=1.0)
    model.fit(X, y)

    test_urls = [
        "https://www.paypal.com/account/settings",
        "http://paypal-account-verify-secure.ml/update",
    ]
    X_test = np.array([featurize_to_vector(u) for u in test_urls])
    probs = model.predict_proba(X_test)
    for u, p in zip(test_urls, probs):
        print(u, "-> P(malicious) =", round(float(p), 4))

    print("\nFeature importance (split count):")
    importances = model.feature_importance(len(FEATURE_ORDER))
    for name, imp in sorted(zip(FEATURE_ORDER, importances), key=lambda t: -t[1]):
        if imp > 0:
            print(f"  {name}: {int(imp)}")
