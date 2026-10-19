import os
import re
import unicodedata

# Cyrillic and Greek homoglyphs to ASCII mapping
HOMOGLYPH_MAP = {
    'а': 'a', 'е': 'e', 'о': 'o', 'р': 'p', 'с': 'c', 'у': 'y', 'х': 'x', 'і': 'i', 'ј': 'j', 'ѕ': 's',
    'А': 'A', 'В': 'B', 'Е': 'E', 'К': 'K', 'М': 'M', 'Н': 'H', 'О': 'O', 'Р': 'P', 'С': 'C', 'Т': 'T', 'Х': 'X',
    'α': 'a', 'ο': 'o', 'ν': 'v', 'ρ': 'p', 'τ': 't', 'κ': 'k',
    'Α': 'A', 'Β': 'B', 'Ε': 'E', 'Η': 'H', 'Ι': 'I', 'Κ': 'K', 'Μ': 'M', 'Ν': 'N', 'Ο': 'O', 'Ρ': 'P', 'Τ': 'T', 'Χ': 'X'
}

# Regex to strip invisible formatting and zero-width characters:
# \u200b (ZWSP), \u200c (ZWNJ), \u200d (ZWJ), \ufeff (BOM), \u00ad (soft hyphen),
# \u2060 (word joiner), \u200e/\u200f (directional marks), \u202a-\u202e (bidi overrides)
ZERO_WIDTH_PATTERN = re.compile(r'[\u200b-\u200f\ufeff\u00ad\u2060\u202a-\u202e]')

# Maximum text length limit to prevent ReDoS or memory exhaustion
MAX_SMS_LENGTH = 5000


def clean_sms_text(text: str) -> str:
    """
    Normalizes SMS text while preserving security indicators:
    - Preserves URLs, numbers, currency symbols ($/₹/£/€), and punctuation
    - Strips zero-width and invisible control characters used for filter evasion
    - Normalizes visual homoglyphs (e.g. Cyrillic/Greek lookalikes)
    - Enforces safety length boundaries to prevent ReDoS / memory exhaustion
    - Cleans malformed unicode and unifies whitespace
    """
    if not isinstance(text, str) or not text.strip():
        return ""

    # Truncate oversized inputs safely
    bounded = text[:MAX_SMS_LENGTH]

    # Strip zero-width and invisible formatting characters before NFKC
    without_zero_width = ZERO_WIDTH_PATTERN.sub('', bounded)

    # Normalize unicode (NFKC handles full-width Latin, accents, etc.)
    normalized = unicodedata.normalize("NFKC", without_zero_width)

    # Replace consecutive control characters or null bytes
    cleaned = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', normalized)

    # Replace visual homoglyphs
    homoglyph_cleaned = ''.join(HOMOGLYPH_MAP.get(c, c) for c in cleaned)

    # Collapse excess whitespace
    result = re.sub(r'\s+', ' ', homoglyph_cleaned).strip()

    return result


class SmsPreprocessor:
    def __init__(self, tokenizer_name: str = "huawei-noah/TinyBERT_General_4L_312D", max_length: int = 128):
        self.tokenizer_name = tokenizer_name
        self.max_length = max_length
        self._tokenizer = None

    def _get_tokenizer(self):
        if self._tokenizer is None:
            # Check local registered models directory first for offline inference
            local_vocab_dir = os.path.abspath(
                os.path.join(os.path.dirname(__file__), "..", "..", "models", "tinybert", "v2")
            )
            if os.path.exists(os.path.join(local_vocab_dir, "vocab.txt")):
                try:
                    from transformers import BertTokenizer
                    self._tokenizer = BertTokenizer.from_pretrained(local_vocab_dir, local_files_only=True)
                    return self._tokenizer
                except Exception:
                    pass

            try:
                from transformers import AutoTokenizer
                self._tokenizer = AutoTokenizer.from_pretrained(self.tokenizer_name, local_files_only=True)
            except Exception:
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

