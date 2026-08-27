import os
import hashlib
import pandas as pd

DATASET_DIR = "Datasets"

def compute_sha256(file_path):
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def inspect_datasets():
    if not os.path.exists(DATASET_DIR):
        print(f"Directory {DATASET_DIR} not found.")
        return

    files = [f for f in os.listdir(DATASET_DIR) if f.endswith(".csv")]
    print(f"Found {len(files)} CSV files in {DATASET_DIR}:\n")

    for filename in sorted(files):
        filepath = os.path.join(DATASET_DIR, filename)
        file_size_mb = os.path.getsize(filepath) / (1024 * 1024)
        file_hash = compute_sha256(filepath)
        
        print("=" * 80)
        print(f"Dataset File: {filename}")
        print(f"Size: {file_size_mb:.2f} MB")
        print(f"SHA-256: {file_hash}")

        try:
            df = pd.read_csv(filepath, low_memory=False)
            print(f"Rows: {len(df):,}")
            print(f"Columns ({len(df.columns)}): {list(df.columns)}")
            print("\nData Types:")
            print(df.dtypes)

            print("\nMissing Values per Column:")
            missing = df.isnull().sum()
            print(missing[missing > 0] if (missing > 0).any() else "No missing values")

            duplicates = df.duplicated().sum()
            print(f"\nDuplicate Rows: {duplicates:,}")

            print("\nSample Data (First 3 rows):")
            print(df.head(3))

            # Inspect probable label columns
            for col in df.columns:
                unique_vals = df[col].nunique()
                if unique_vals <= 10:
                    print(f"\nPotential Label Column '{col}' value counts:")
                    print(df[col].value_counts(dropna=False))

        except Exception as e:
            print(f"Error reading {filename}: {e}")

        print("=" * 80 + "\n")

if __name__ == "__main__":
    inspect_datasets()
