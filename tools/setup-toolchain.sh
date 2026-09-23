#!/usr/bin/env bash
#
# Downloads the offline Android build toolchain used by tools/build-apk.sh.
#
# The project is built without Gradle and without network access to Google's
# Maven / SDK repositories, so the individual build tools are pulled from the
# package registries that mirror them:
#
#   * a JDK 17 runtime         <- PyPI  "jdk4py"          (runs kotlinc / d8 / apksigner / keytool)
#   * the Kotlin compiler     <- npm   "kotlin-compiler" (kotlinc 2.4.x, ships kotlin-stdlib.jar)
#   * aapt2 (linux x86_64)    <- npm   "aaptjs3"         (resource compile + link)
#   * d8, ecj, apksigner, android.jar (API 34)
#                             <- npm   "@drxiaozhi/minapk"
#
# Everything is cached in ${TOOLCHAIN_DIR} (default: /tmp/m3e-toolchain, outside
# the repo so the working tree stays clean). Re-running is a no-op once cached.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-${TMPDIR:-/tmp}/m3e-toolchain}"
JDK_WHEEL_NAME="${JDK_WHEEL_NAME:-jdk4py}"
# JDK 17: a version string both Kotlin 1.9 and the d8/apksigner jars parse.
JDK_VERSION="${JDK_VERSION:-17.0.9.2}"
KOTLIN_VERSION="${KOTLIN_VERSION:-1.9.25}"
AAPT_PKG_VERSION="${AAPT_PKG_VERSION:-2.0.2}"
MINAPK_VERSION="${MINAPK_VERSION:-0.4.0}"

mkdir -p "$TC/dl"
cd "$TC"

say() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

fetch() { # fetch <url> <dest>
  if [ -s "$2" ]; then return 0; fi
  say "downloading $(basename "$2")"
  curl -fsSL --retry 3 -o "$2.part" "$1"
  mv "$2.part" "$2"
}

# ---------------------------------------------------------------- Java runtime
if [ ! -x "$TC/java/bin/java" ]; then
  say "fetching $JDK_WHEEL_NAME $JDK_VERSION (PyPI)"
  wheel="$(python3 "$ROOT/tools/resolve-jdk.py" "$JDK_WHEEL_NAME" "$JDK_VERSION")"
  fetch "$wheel" "$TC/dl/$JDK_WHEEL_NAME-$JDK_VERSION.whl"
  rm -rf "$TC/jdkx" && mkdir -p "$TC/jdkx"
  (cd "$TC/jdkx" && unzip -q "../dl/$JDK_WHEEL_NAME-$JDK_VERSION.whl")
  runtime="$(find "$TC/jdkx" -maxdepth 3 -type d -name java-runtime | head -1)"
  [ -n "$runtime" ] || { echo "could not locate java-runtime in wheel" >&2; exit 1; }
  mv "$runtime" "$TC/java"
fi
JAVA="$TC/java/bin/java"
"$JAVA" -version >/dev/null 2>&1 || { echo "JRE does not run" >&2; exit 1; }

# --------------------------------------------------------------- Kotlin compiler
if [ ! -f "$TC/kotlin/lib/kotlin-compiler.jar" ]; then
  say "fetching Kotlin compiler $KOTLIN_VERSION (npm)"
  fetch "https://registry.npmjs.org/kotlin-compiler/-/kotlin-compiler-$KOTLIN_VERSION.tgz" \
        "$TC/dl/kotlin-compiler-$KOTLIN_VERSION.tgz"
  rm -rf "$TC/kotlin" && mkdir -p "$TC/kotlin"
  tar xzf "$TC/dl/kotlin-compiler-$KOTLIN_VERSION.tgz" -C "$TC/kotlin" --strip-components=1
fi

# ------------------------------------------------------------------------- aapt2
if [ ! -x "$TC/bin/aapt2" ]; then
  say "fetching aapt2 (npm: aaptjs3 $AAPT_PKG_VERSION)"
  fetch "https://registry.npmjs.org/aaptjs3/-/aaptjs3-$AAPT_PKG_VERSION.tgz" \
        "$TC/dl/aaptjs3.tgz"
  rm -rf "$TC/aaptjs3" && mkdir -p "$TC/aaptjs3" "$TC/bin"
  tar xzf "$TC/dl/aaptjs3.tgz" -C "$TC/aaptjs3" --strip-components=1
  cp "$TC/aaptjs3/bin/x64/linux/aapt2" "$TC/bin/aapt2"
  chmod +x "$TC/bin/aapt2"
fi

# ------------------------------- d8 + ecj + apksigner + android.jar (API 34)
if [ ! -f "$TC/lib/d8.jar" ] || [ ! -f "$TC/lib/android.jar" ] || [ ! -f "$TC/lib/ecj.jar" ]; then
  say "fetching d8 / apksigner / android.jar (npm: @drxiaozhi/minapk $MINAPK_VERSION)"
  fetch "https://registry.npmjs.org/@drxiaozhi%2Fminapk/-/minapk-$MINAPK_VERSION.tgz" \
        "$TC/dl/minapk.tgz"
  rm -rf "$TC/minapk" && mkdir -p "$TC/minapk" "$TC/lib"
  tar xzf "$TC/dl/minapk.tgz" -C "$TC/minapk" --strip-components=1
  cp "$TC/minapk/tools/d8.jar" "$TC/lib/d8.jar"
  cp "$TC/minapk/tools/ecj-"*.jar "$TC/lib/ecj.jar"
  cp "$TC/minapk/tools/apksigner.jar" "$TC/lib/apksigner.jar"
  cp "$TC/minapk/tools/android.jar" "$TC/lib/android.jar"
fi

# ------------------------------------------------------------------- keystore
KEYSTORE="${KEYSTORE:-$ROOT/keystore/release.jks}"
if [ ! -f "$KEYSTORE" ]; then
  say "generating release keystore at ${KEYSTORE#$ROOT/}"
  mkdir -p "$(dirname "$KEYSTORE")"
  "$TC/java/bin/keytool" -genkeypair -v \
    -keystore "$KEYSTORE" -alias "${KEY_ALIAS:-m3ecalc}" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "${KEY_STOREPASS:-m3ecalc123}" -keypass "${KEY_KEYPASS:-m3ecalc123}" \
    -dname "${KEY_DNAME:-CN=M3E Calc, OU=FlexTeam, O=FlexTeam, C=UA}" >/dev/null 2>&1
  echo "   (delete $KEYSTORE and set KEYSTORE=/path/to/your.jks to sign with your own key)"
fi

cat > "$TC/versions.txt" <<EOF
java=$("$JAVA" -version 2>&1 | head -1)
aapt2=$("$TC/bin/aapt2" version 2>&1 | head -1)
d8=$("$JAVA" -cp "$TC/lib/d8.jar" com.android.tools.r8.D8 --version 2>/dev/null | head -1)
apksigner=$("$JAVA" -jar "$TC/lib/apksigner.jar" --version 2>/dev/null | head -1)
EOF
say "toolchain ready in $TC"
cat "$TC/versions.txt"
