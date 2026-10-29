#!/usr/bin/env bash
# Checks that run WITHOUT Google Maven, NuGet or a Windows/Android machine.
# They complement CI; they do not replace it (no APK is built, nothing is run
# on Windows). Downloads go to ~/.cache/focus-checks; nothing is installed.
#
#   tools/sandbox-checks.sh            # all checks
#
# Third-party inputs (pinned, used only as compile-time stubs or linters):
#   * AOSP framework jar, android-16.0.0_r2 build, from github.com/Reginer/aosp-android-jar
#     (superset of the public SDK: catches type errors, may miss hidden-API misuse)
#   * PowerShell 7.5.4 and PSScriptAnalyzer 1.24.0 (official GitHub releases)
#   * actionlint 1.7.7 (official GitHub release)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${HOME}/.cache/focus-checks"
mkdir -p "$CACHE"
fail=0

echo "== Rust core"
( cd "$ROOT" && RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-stable}" cargo fmt --check \
  && RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-stable}" cargo clippy --all-targets -q -- -D warnings \
  && RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-stable}" cargo test -q ) || fail=1

echo "== Windows probe (compile only)"
if command -v dotnet >/dev/null; then
  for p in Probe Target; do
    DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 dotnet build "$ROOT/probes/windows/$p" -c Release -p:UseAppHost=false -v q -nologo || fail=1
  done
  rm -rf "$ROOT"/probes/windows/*/bin "$ROOT"/probes/windows/*/obj
else echo "dotnet not installed (apt-get install dotnet-sdk-10.0)"; fail=1; fi

echo "== Android probe (Kotlin type-check against AOSP API 36; not an APK build)"
JAR="$CACHE/aosp/android-36/android.jar"
if [ ! -f "$JAR" ]; then
  rm -rf "$CACHE/aosp"
  git clone -q --depth 1 --filter=blob:none --no-checkout https://github.com/Reginer/aosp-android-jar "$CACHE/aosp"
  git -C "$CACHE/aosp" checkout -q HEAD -- android-36/android.jar
fi
GRADLE_LIB="$(dirname "$(dirname "$(readlink -f "$(command -v gradle)")")")/lib"
KCP=$(ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar "$GRADLE_LIB"/kotlin-stdlib-2*.jar "$GRADLE_LIB"/kotlin-reflect-*.jar \
      "$GRADLE_LIB"/kotlin-script-runtime-*.jar "$GRADLE_LIB"/kotlin-daemon-embeddable-*.jar "$GRADLE_LIB"/trove4j*.jar \
      "$GRADLE_LIB"/annotations-*.jar "$GRADLE_LIB"/kotlinx-coroutines-core-jvm-*.jar 2>/dev/null | tr '\n' ':')
STDLIB=$(ls "$GRADLE_LIB"/kotlin-stdlib-2*.jar)
# Stand-in for the generated R class (the real one comes from the Gradle build in CI):
# one Int constant per resource name found under src/main/res.
gen_r() { # $1 module dir, $2 package, $3 output file
  python3 -I - "$1" "$2" "$3" <<'PY'
import sys, pathlib, re, collections
mod, pkg, out = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
kinds = collections.defaultdict(set)
for f in (mod / "src/main/res").rglob("*"):
    if not f.is_file(): continue
    d = f.parent.name.split("-")[0]
    if d == "values":
        for k, n in re.findall(r'<(bool|string|integer|color|dimen)\s+name="([A-Za-z0-9_]+)"', f.read_text()):
            kinds[k].add(n)
    else:
        kinds[d].add(f.stem)
body = "".join(f"    object {k} {{ " + "; ".join(f"const val {n} = 0" for n in sorted(v)) + " }\n" for k, v in sorted(kinds.items()))
pathlib.Path(out).write_text(f"package {pkg}\n\n@Suppress(\"ClassName\")\nobject R {{\n{body}}}\n")
PY
}
for m in probe target; do
  pkg=$(grep -oE 'namespace = "[^"]+"' "$ROOT/probes/android/$m/build.gradle.kts" | cut -d'"' -f2)
  mkdir -p "$CACHE/rstub/$m"; gen_r "$ROOT/probes/android/$m" "$pkg" "$CACHE/rstub/$m/R.kt"
  out=$(java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
        -cp "$JAR:$STDLIB" -d "$CACHE/ktout/$m" "$CACHE/rstub/$m/R.kt" $(find "$ROOT/probes/android/$m" -name '*.kt') 2>&1 | grep -v JAVA_TOOL || true)
  if echo "$out" | grep -qE "error:|warning:"; then echo "$out"; fail=1; else echo "$m: clean"; fi
done

echo "== VM scripts (parse + PSScriptAnalyzer)"
PW="$CACHE/pwsh"
if [ ! -x "$PW/bin/pwsh" ]; then
  mkdir -p "$PW/bin" "$PW/mods/PSScriptAnalyzer"
  curl -sSL -o "$PW/pwsh.tgz" https://github.com/PowerShell/PowerShell/releases/download/v7.5.4/powershell-7.5.4-linux-x64.tar.gz
  tar -xzf "$PW/pwsh.tgz" -C "$PW/bin" && chmod +x "$PW/bin/pwsh"
  curl -sSL -o "$PW/pssa.nupkg" https://github.com/PowerShell/PSScriptAnalyzer/releases/download/1.24.0/PSScriptAnalyzer.1.24.0.nupkg
  (cd "$PW/mods/PSScriptAnalyzer" && unzip -qo "$PW/pssa.nupkg")
fi
"$PW/bin/pwsh" -NoProfile -Command "
  Import-Module '$PW/mods/PSScriptAnalyzer/PSScriptAnalyzer.psd1'
  \$r = Invoke-ScriptAnalyzer -Path '$ROOT/probes/windows-vm' -Severity Error,Warning -ExcludeRule PSAvoidUsingWriteHost,PSUseShouldProcessForStateChangingFunctions
  \$r | Format-Table -AutoSize | Out-String | Write-Host
  if (\$r) { exit 1 } else { 'VM scripts: clean' }" || fail=1

echo "== CI workflow (actionlint)"
AL="$CACHE/actionlint"
if [ ! -x "$AL/actionlint" ]; then
  mkdir -p "$AL"
  curl -sSL -o "$AL/al.tgz" https://github.com/rhysd/actionlint/releases/download/v1.7.7/actionlint_1.7.7_linux_amd64.tar.gz
  tar -xzf "$AL/al.tgz" -C "$AL" actionlint
fi
"$AL/actionlint" -shellcheck= -pyflakes= "$ROOT/.github/workflows/ci.yml" && echo "workflow: clean" || fail=1

[ "$fail" = 0 ] && echo "ALL SANDBOX CHECKS PASSED" || { echo "SOME CHECKS FAILED"; exit 1; }
