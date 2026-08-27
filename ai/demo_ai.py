import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ai.tinybert.model import TinyBertSmsClassifier
from ai.xgboost.model import XgboostUrlClassifier
from ai.anomaly.model import IsolationForestDetector

def main():
    print("\n" + "="*70)
    print(" 🛡️  SQUISH SHIELD AI ENGINE — LIVE TEACHER DEMONSTRATION  🛡️")
    print("="*70)

    print("\nLoading AI models into memory...")
    tb_classifier = TinyBertSmsClassifier("models/tinybert/v1/model.pth")
    xgb_classifier = XgboostUrlClassifier("models/xgboost/v1/model.json")
    iso_detector = IsolationForestDetector("models/isolation_forest/v1/model.joblib")
    print("✓ All 3 AI Models Loaded Successfully!\n")

    while True:
        print("-" * 70)
        print("Select Mode:")
        print(" [1] Test TinyBERT (SMS Smishing Detection)")
        print(" [2] Test XGBoost & Isolation Forest (URL Quishing Detection)")
        print(" [3] Run Complete Pipeline (SMS + Extracted URL)")
        print(" [4] Exit")
        choice = input("\nEnter choice (1-4): ").strip()

        if choice == "1":
            sms = input("\nEnter SMS Message text: ").strip()
            if not sms:
                continue
            res = tb_classifier.classify(sms)
            print("\n🤖 [TinyBERT SMS Classifier Result]")
            print(f"   • Prediction:   {res.label.upper()}")
            print(f"   • Confidence:   {res.confidence*100:.2f}%")
            print(f"   • Probabilities: Safe={res.probabilities['safe']*100:.1f}%, Smishing={res.probabilities['smishing']*100:.1f}%")

        elif choice == "2":
            url = input("\nEnter URL link: ").strip()
            if not url:
                continue
            xgb_res = xgb_classifier.classify(url)
            iso_res = iso_detector.predict(url)
            print("\n📊 [XGBoost URL Classifier Result]")
            print(f"   • Prediction:   {xgb_res.label.upper()}")
            print(f"   • Probability:  {xgb_res.probability*100:.2f}% Phishing")
            print(f"   • Confidence:   {xgb_res.confidence*100:.2f}%")
            print("\n🚨 [Isolation Forest Zero-Day Anomaly Result]")
            print(f"   • Is Anomalous: {iso_res.is_anomalous}")
            print(f"   • Anomaly Score: {iso_res.anomaly_score * 100:.1f} / 100")

        elif choice == "3":
            sms = input("\nEnter SMS Message text: ").strip()
            url = input("Enter embedded URL (or press Enter to skip): ").strip()
            
            res_sms = tb_classifier.classify(sms)
            print("\n🤖 [TinyBERT SMS Analysis]")
            print(f"   • Result: {res_sms.label.upper()} ({res_sms.confidence*100:.1f}% confidence)")

            if url:
                xgb_res = xgb_classifier.classify(url)
                iso_res = iso_detector.predict(url)
                print("\n📊 [XGBoost URL Analysis]")
                print(f"   • Result: {xgb_res.label.upper()} ({xgb_res.probability*100:.1f}% probability)")
                print("\n🚨 [Isolation Forest Anomaly Analysis]")
                print(f"   • Zero-Day Anomaly Score: {iso_res.anomaly_score * 100:.1f} / 100")

        elif choice == "4":
            print("\nExiting demonstration. Thank you!")
            break
        else:
            print("Invalid selection. Try again.")

if __name__ == "__main__":
    main()
