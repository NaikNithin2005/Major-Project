import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.preprocessing.sms_preprocessor import clean_sms_text, SmsPreprocessor

def test_clean_sms_text():
    raw = "  URGENT! Claim your $1000 prize now at http://win.com! \n\n "
    cleaned = clean_sms_text(raw)
    assert "URGENT" in cleaned
    assert "$1000" in cleaned
    assert "http://win.com" in cleaned
    assert "\n" not in cleaned

def test_sms_preprocessor_encoding():
    prep = SmsPreprocessor(max_length=128)
    sample = "Test security message for smishing analysis"
    encoded = prep.encode(sample)
    assert "input_ids" in encoded
    assert "attention_mask" in encoded
    assert encoded["input_ids"].shape == (1, 128)
