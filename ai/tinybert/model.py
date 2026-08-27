import time
from dataclasses import dataclass, asdict
import torch
import torch.nn as nn
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from ai.preprocessing.sms_preprocessor import SmsPreprocessor

MODEL_VERSION = "tinybert-smishing-v1"
CHECKPOINT_NAME = "huawei-noah/TinyBERT_General_4L_312D"

@dataclass
class TinyBertResult:
    model_version: str
    label: str
    probabilities: dict
    confidence: float
    timestamp: float

class PyTorchTinyBertModel(nn.Module):
    """
    4-Layer Transformer Architecture matching TinyBERT (312 hidden dim, 4 heads, 2 labels).
    """
    def __init__(self, vocab_size: int = 30522, hidden_dim: int = 312, num_classes: int = 2):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, hidden_dim)
        encoder_layer = nn.TransformerEncoderLayer(d_model=hidden_dim, nhead=4, dim_feedforward=1200, batch_first=True)
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=4)
        self.classifier = nn.Sequential(
            nn.Dropout(0.1),
            nn.Linear(hidden_dim, num_classes)
        )

    def forward(self, input_ids, attention_mask=None):
        x = self.embedding(input_ids)
        if attention_mask is not None:
            mask = (attention_mask == 0)
        else:
            mask = None
        out = self.encoder(x, src_key_padding_mask=mask)
        pooled = out[:, 0, :] # CLS token representation
        logits = self.classifier(pooled)
        return logits

class TinyBertSmsClassifier:
    def __init__(self, model_path: str = None, device: str = None):
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.preprocessor = SmsPreprocessor(max_length=128)
        self.model = None

        if model_path:
            self.load_model(model_path)

    def load_model(self, model_path: str):
        try:
            # Try loading Hugging Face sequence classification checkpoint or exported PyTorch weights
            self.model = AutoModelForSequenceClassification.from_pretrained(model_path)
        except Exception:
            try:
                self.model = PyTorchTinyBertModel()
                state_dict = torch.load(model_path, map_location=self.device)
                self.model.load_state_dict(state_dict)
            except Exception as e:
                raise RuntimeError(f"Failed to load TinyBERT model from {model_path}: {e}")
        self.model.to(self.device)
        self.model.eval()

    def classify(self, text: str) -> TinyBertResult:
        if self.model is None:
            raise RuntimeError("Model is not loaded. Call load_model() first.")

        inputs = self.preprocessor.encode(text)
        input_ids = inputs["input_ids"].to(self.device)
        attention_mask = inputs["attention_mask"].to(self.device)

        with torch.no_grad():
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
            if hasattr(outputs, "logits"):
                logits = outputs.logits
            else:
                logits = outputs
            probs = torch.softmax(logits, dim=-1).squeeze(0).cpu().numpy()

        p_safe = float(probs[0])
        p_smishing = float(probs[1])
        predicted_class = "smishing" if p_smishing >= 0.5 else "safe"
        confidence = float(max(p_safe, p_smishing))

        return TinyBertResult(
            model_version=MODEL_VERSION,
            label=predicted_class,
            probabilities={"safe": round(p_safe, 4), "smishing": round(p_smishing, 4)},
            confidence=round(confidence, 4),
            timestamp=time.time()
        )
