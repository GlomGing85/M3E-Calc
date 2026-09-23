#!/usr/bin/env bash
#
# Runs the expression-engine tests on a plain JVM. The calc package has no
# Android imports, so this needs nothing but the toolchain (kotlinc + JRE).
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-${TMPDIR:-/tmp}/m3e-toolchain}"
[ -x "$TC/java/bin/java" ] || "$ROOT/tools/setup-toolchain.sh" >/dev/null

BUILD="$ROOT/build/calc-tests"
rm -rf "$BUILD" && mkdir -p "$BUILD"

"$TC/java/bin/java" -cp "$TC/kotlin/lib/kotlin-compiler.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -nowarn -no-stdlib -jvm-target 1.8 -jdk-home "$TC/java" \
  -classpath "$TC/kotlin/lib/kotlin-stdlib.jar" \
  -d "$BUILD" \
  "$ROOT/app/src/main/kotlin/com/flexteam/m3ecalc/calc/Calculator.kt" \
  "$ROOT/app/src/main/kotlin/com/flexteam/m3ecalc/calc/Formatter.kt" \
  "$ROOT/app/src/main/kotlin/com/flexteam/m3ecalc/data/Units.kt" \
  "$ROOT/tools/calc-tests/CalcTests.kt" 2>&1 | grep -v '^warning:' || true

"$TC/java/bin/java" -cp "$BUILD:$TC/kotlin/lib/kotlin-stdlib.jar" \
  com.flexteam.m3ecalc.calc.CalcTestsKt
