import re
import unicodedata

def clean_sms_text(text: str) -> str:
    """
    Normalizes SMS text while preserving security indicators:
    - Preserves URLs, numbers, currency symbols ($/₹/£/€), and punctuation
    - Cleans malformed unicode and unifies whitespace
    """
    if not isinstance(text, str) or not text.strip():
        return ""

    # Normalize unicode
    normalized = unicodedata.normalize("NFKC", text)

    # Replace consecutive control characters or null bytes
    cleaned = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', normalized)

    # Collapse excess whitespace
    cleaned = re.sub(r'\s+', ' ', cleaned).strip()

    return cleaned

class SmsPreprocessor:
    def __init__(self, tokenizer_name: str = "huawei-noah/TinyBERT_General_4L_312D", max_length: int = 128):
        self.tokenizer_name = tokenizer_name
        self.max_length = max_length
        self._tokenizer = None

    def _get_tokenizer(self):
        if self._tokenizer is None:
            try:
                from transformers import AutoTokenizer
                self._tokenizer = AutoTokenizer.from_pretrained(self.tokenizer_name)
            except Exception:
                from transformers import BertTokenizer
                # Fallback to standard bert-base-uncased if offline/checkpoint error
                self._tokenizer = BertTokenizer.from_pretrained("bert-base-uncased")
        return self._tokenizer

    def preprocess(self, text: str) -> str:
        return clean_sms_text(text)

    def encode(self, text: str):
        cleaned = self.preprocess(text)
        tokenizer = self._get_tokenizer()
        return tokenizer(
            cleaned,
            padding="max_length",
            truncation=True,
            max_length=self.max_length,
            return_tensors="pt"
        )
