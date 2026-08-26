import os
import sys

REQUIRED_PATHS = [
    "android/build.gradle.kts",
    "android/app/build.gradle.kts",
    "backend/app/main.py",
    "backend/requirements.txt",
    "ai/requirements.txt",
    "ai/preprocessing/__init__.py",
    "ai/feature_engineering/__init__.py",
    "ai/tinybert/__init__.py",
    "ai/xgboost/__init__.py",
    "ai/anomaly/__init__.py",
    "ai/explainable_ai/__init__.py",
    "web/package.json",
    "web/src/app/page.tsx",
    "docs/README.md",
    "docs/CONVENTIONS.md",
    "scripts/README.md",
    "tests/README.md",
    "PRD.md",
    "Architecture.md",
    "Rules.md",
    "Phases.md",
    "Memory.md",
    ".gitignore",
    ".env.example",
    "docker-compose.yml",
    "README.md",
]

def verify():
    print("Checking Phase 0 Foundation Deliverables...")
    missing = []
    for path in REQUIRED_PATHS:
        if not os.path.exists(path):
            missing.append(path)
            print(f"[MISSING]: {path}")
        else:
            print(f"[OK]: {path}")

    if missing:
        print(f"\nPhase 0 Verification Failed! Missing {len(missing)} paths.")
        sys.exit(1)
    else:
        print("\n[OK] Phase 0 Foundation Verification Passed!")

if __name__ == "__main__":
    verify()
