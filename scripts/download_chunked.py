import os
import sys
import hashlib
import urllib.request

TOTAL_SIZE = 56259401
EXPECTED_SHA1 = "6882d39ef03b18879c1a5d2a83c2212361bed5fe"
URL = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.10/kotlin-compiler-embeddable-2.2.10.jar"
DEST = r"C:\Users\nithi\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-compiler-embeddable\2.2.10\6882d39ef03b18879c1a5d2a83c2212361bed5fe\kotlin-compiler-embeddable-2.2.10.jar"

CHUNK_SIZE = 2 * 1024 * 1024  # 2MB chunks

os.makedirs(os.path.dirname(DEST), exist_ok=True)
tmp_path = DEST + ".part"

# Resume if partial exists
start_byte = 0
if os.path.exists(tmp_path):
    start_byte = os.path.getsize(tmp_path)

print(f"Resuming download from byte {start_byte:,} / {TOTAL_SIZE:,}...", flush=True)

with open(tmp_path, "ab" if start_byte > 0 else "wb") as f:
    curr = start_byte
    while curr < TOTAL_SIZE:
        end = min(curr + CHUNK_SIZE - 1, TOTAL_SIZE - 1)
        req = urllib.request.Request(URL)
        req.add_header("Range", f"bytes={curr}-{end}")
        req.add_header("User-Agent", "Mozilla/5.0")

        success = False
        for attempt in range(5):
            try:
                with urllib.request.urlopen(req, timeout=20) as resp:
                    data = resp.read()
                    f.write(data)
                    f.flush()
                    curr += len(data)
                    pct = (curr / TOTAL_SIZE) * 100
                    print(f"Downloaded {curr:,} / {TOTAL_SIZE:,} ({pct:.1f}%)", flush=True)
                    success = True
                    break
            except Exception as e:
                print(f"Attempt {attempt+1} failed: {e}. Retrying...", flush=True)
        if not success:
            print("Failed to download chunk. Stopping.", flush=True)
            sys.exit(1)

print("Download complete. Verifying SHA-1...", flush=True)
hasher = hashlib.sha1()
with open(tmp_path, "rb") as f:
    while b := f.read(65536):
        hasher.update(b)
sha1 = hasher.hexdigest().lower()
print(f"Calculated SHA-1: {sha1}", flush=True)

if sha1 != EXPECTED_SHA1.lower():
    print(f"MISMATCH! Expected: {EXPECTED_SHA1}", flush=True)
    sys.exit(1)

if os.path.exists(DEST):
    os.remove(DEST)
os.rename(tmp_path, DEST)
print("SUCCESSFULLY SAVED kotlin-compiler-embeddable-2.2.10.jar to gradle cache!", flush=True)
