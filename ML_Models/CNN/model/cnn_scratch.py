"""
cnn_scratch.py
A 1D character-level CNN built from raw NumPy - no Keras/PyTorch/TensorFlow
layers. Used here on the character sequence of the URL (catches lexical
patterns like 'paypa1', 'secure-login-verify', excessive hyphenation etc.)
as a companion signal to the XGBoost model, which runs on hand-engineered
features.
"""

import numpy as np

np.random.seed(42)

# ---------------------------------------------------------------------------
# Character vocabulary
# ---------------------------------------------------------------------------
VOCAB = list("abcdefghijklmnopqrstuvwxyz0123456789:/.-_?=&@%")
CHAR_TO_IDX = {c: i + 1 for i, c in enumerate(VOCAB)}  # 0 reserved for padding
VOCAB_SIZE = len(VOCAB) + 1
MAX_LEN = 128  # truncate/pad URLs to this length


def encode_url(url: str, max_len: int = MAX_LEN):
    url = url.lower()[:max_len]
    ids = [CHAR_TO_IDX.get(c, 0) for c in url]
    ids += [0] * (max_len - len(ids))
    return np.array(ids, dtype=np.int64)


# ---------------------------------------------------------------------------
# Layers
# ---------------------------------------------------------------------------
class Embedding:
    def __init__(self, vocab_size, embed_dim):
        limit = np.sqrt(6 / (vocab_size + embed_dim))
        self.W = np.random.uniform(-limit, limit, size=(vocab_size, embed_dim))
        self.grad_W = np.zeros_like(self.W)
        self.last_input = None

    def forward(self, x_ids):
        self.last_input = x_ids
        return self.W[x_ids]

    def backward(self, d_out, lr):
        self.grad_W[:] = 0
        batch, seq_len, _ = d_out.shape
        for b in range(batch):
            for t in range(seq_len):
                idx = self.last_input[b, t]
                if idx != 0:
                    self.grad_W[idx] += d_out[b, t]
        self.W -= lr * self.grad_W

class Conv1D:
    def __init__(self, in_ch, out_ch, kernel_size):
        self.k = kernel_size
        self.in_ch = in_ch
        self.out_ch = out_ch
        limit = np.sqrt(6 / (in_ch * kernel_size + out_ch))
        self.W = np.random.uniform(-limit, limit, size=(kernel_size, in_ch, out_ch))
        self.b = np.zeros(out_ch)
        self.last_input = None

    def forward(self, x):
        batch, seq_len, in_ch = x.shape
        out_len = seq_len - self.k + 1
        self.last_input = x
        out = np.zeros((batch, out_len, self.out_ch))
        for t in range(out_len):
            window = x[:, t:t + self.k, :]
            out[:, t, :] = np.tensordot(window, self.W, axes=([1, 2], [0, 1])) + self.b
        return out

    def backward(self, d_out, lr):
        batch, seq_len, in_ch = self.last_input.shape
        out_len = d_out.shape[1]

        d_W = np.zeros_like(self.W)
        d_b = np.sum(d_out, axis=(0, 1))
        d_x = np.zeros_like(self.last_input)

        for t in range(out_len):
            window = self.last_input[:, t:t + self.k, :]
            grad_slice = d_out[:, t, :]
            d_W += np.tensordot(window, grad_slice, axes=([0], [0]))
            d_x[:, t:t + self.k, :] += np.tensordot(grad_slice, self.W, axes=([1], [2])).transpose(0, 1, 2)

        self.W -= lr * d_W / batch
        self.b -= lr * d_b / batch
        return d_x

class ReLU:
    def forward(self, x):
        self.mask = x > 0
        return x * self.mask

    def backward(self, d_out, lr=None):
        return d_out * self.mask

class GlobalMaxPool1D:
    def forward(self, x):
        self.last_input = x
        self.argmax_idx = np.argmax(x, axis=1)
        return np.max(x, axis=1)

    def backward(self, d_out, lr=None):
        batch, seq_len, ch = self.last_input.shape
        d_x = np.zeros_like(self.last_input)
        for b in range(batch):
            for c in range(ch):
                d_x[b, self.argmax_idx[b, c], c] = d_out[b, c]
        return d_x

class Dense:
    def __init__(self, in_dim, out_dim):
        limit = np.sqrt(6 / (in_dim + out_dim))
        self.W = np.random.uniform(-limit, limit, size=(in_dim, out_dim))
        self.b = np.zeros(out_dim)
        self.last_input = None

    def forward(self, x):
        self.last_input = x
        return x @ self.W + self.b

    def backward(self, d_out, lr):
        batch = self.last_input.shape[0]
        d_W = self.last_input.T @ d_out / batch
        d_b = np.mean(d_out, axis=0)
        d_x = d_out @ self.W.T
        self.W -= lr * d_W
        self.b -= lr * d_b
        return d_x


def softmax(logits):
    z = logits - np.max(logits, axis=1, keepdims=True)
    exp = np.exp(z)
    return exp / np.sum(exp, axis=1, keepdims=True)


def cross_entropy_loss(probs, y_true):
    n = probs.shape[0]
    log_likelihood = -np.log(probs[np.arange(n), y_true] + 1e-9)
    return np.mean(log_likelihood)


# ---------------------------------------------------------------------------
# Full model
# ---------------------------------------------------------------------------
class CharCNN:
    def __init__(self, vocab_size=VOCAB_SIZE, embed_dim=16, num_filters=32,
                 kernel_size=4, num_classes=2):
        self.embed = Embedding(vocab_size, embed_dim)
        self.conv = Conv1D(embed_dim, num_filters, kernel_size)
        self.relu = ReLU()
        self.pool = GlobalMaxPool1D()
        self.dense = Dense(num_filters, num_classes)

    def forward(self, x_ids):
        x = self.embed.forward(x_ids)
        x = self.conv.forward(x)
        x = self.relu.forward(x)
        x = self.pool.forward(x)
        logits = self.dense.forward(x)
        probs = softmax(logits)
        return probs

    def backward(self, probs, y_true, lr):
        batch = probs.shape[0]
        d_logits = probs.copy()
        d_logits[np.arange(batch), y_true] -= 1
        d_logits /= batch

        d = self.dense.backward(d_logits, lr)
        d = self.pool.backward(d, lr)
        d = self.relu.backward(d, lr)
        d = self.conv.backward(d, lr)
        self.embed.backward(d, lr)

    def train_step(self, x_ids, y_true, lr=0.05):
        probs = self.forward(x_ids)
        loss = cross_entropy_loss(probs, y_true)
        self.backward(probs, y_true, lr)
        preds = np.argmax(probs, axis=1)
        acc = np.mean(preds == y_true)
        return loss, acc

    def predict_proba(self, urls):
        x_ids = np.stack([encode_url(u) for u in urls])
        return self.forward(x_ids)


if __name__ == "__main__":
    legit = [
        "https://www.paypal.com/signin",
        "https://accounts.google.com/login",
        "https://www.amazon.com/gp/cart",
        "https://www.hdfcbank.com/personal",
    ]
    malicious = [
        "http://paypa1-secure-login.tk/verify",
        "http://192.168.10.4/login.php?id=1",
        "http://free-gift-claim-now.xyz/win",
        "http://amaz0n-account-locked.top/reset",
    ]
    urls = legit + malicious
    labels = np.array([0] * len(legit) + [1] * len(malicious))

    model = CharCNN()
    x_ids = np.stack([encode_url(u) for u in urls])

    for epoch in range(60):
        loss, acc = model.train_step(x_ids, labels, lr=0.1)
        if epoch % 10 == 0 or epoch == 59:
            print(f"epoch {epoch:3d}  loss={loss:.4f}  acc={acc:.2f}")

    test_urls = [
        "https://www.paypal.com/account",
        "http://paypal-secure-verify-account.ml/login",
    ]
    probs = model.predict_proba(test_urls)
    for u, p in zip(test_urls, probs):
        print(u, "-> P(malicious) =", round(float(p[1]), 4))