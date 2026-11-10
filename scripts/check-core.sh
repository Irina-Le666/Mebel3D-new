#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_DIR="$ROOT/android/app/src/main/java/ru/mebel3d/app/core"
TEST_DIR="$ROOT/build-check"
OUT="${TMPDIR:-/tmp}/mebel3d-core-tests-$$.jar"
RUNNER="${TMPDIR:-/tmp}/Mebel3dCoreSmokeRunner-$$.kt"
trap 'rm -f "$OUT" "$RUNNER"' EXIT
mapfile -t CORE < <(find "$CORE_DIR" -maxdepth 1 -name '*.kt' ! -name 'ProjectStorage.kt' -print | sort)
mapfile -t TESTS < <(find "$TEST_DIR" -maxdepth 1 -name '*.kt' -print | sort)
if ((${#CORE[@]} == 0 || ${#TESTS[@]} == 0)); then
  echo "Core sources or smoke tests are missing" >&2
  exit 2
fi
{
  echo 'object Mebel3dCoreSmokeRunner {'
  echo '  @JvmStatic fun main(args: Array<String>) {'
  echo '    val tests = listOf('
  for source in "${TESTS[@]}"; do
    base="$(basename "$source" .kt)"
    if grep -q "^object $base[[:space:]]*{" "$source"; then
      class="$base"
    else
      class="${base}Kt"
    fi
    if grep -q '^package ru\.mebel3d\.app\.core' "$source"; then
      class="ru.mebel3d.app.core.$class"
    fi
    printf '      "%s",\n' "$class"
  done
  echo '    )'
  echo '    var passed = 0'
  echo '    for (name in tests) {'
  echo '      print("[RUN] $name ... ")'
  echo '      try {'
  echo '        Class.forName(name).getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>())'
  echo '        passed++'
  echo '      } catch (error: Throwable) {'
  echo '        val cause = error.cause ?: error'
  echo '        System.err.println("FAILED: $name: ${cause.message}")'
  echo '        throw cause'
  echo '      }'
  echo '    }'
  echo '    println("\\nCORE REGRESSION: $passed/${tests.size} smoke tests passed.")'
  echo '  }'
  echo '}'
} > "$RUNNER"
printf 'Compiling %d core files and %d smoke tests into one runner...\n' "${#CORE[@]}" "${#TESTS[@]}"
kotlinc "${CORE[@]}" "${TESTS[@]}" "$RUNNER" -include-runtime -d "$OUT"
java -cp "$OUT" Mebel3dCoreSmokeRunner
"$ROOT/scripts/check-android-config.sh"
