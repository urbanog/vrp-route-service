#!/usr/bin/env bash
# Offline build: compiles main + tests with plain javac and runs the test suite with a tiny
# JUnit-5-compatible runner (tools/junit-shim). Use this where Maven Central is unreachable;
# everywhere else, `mvn verify` runs the same tests under real JUnit 5.
#
#   ./build.sh          compile + test
#   ./build.sh run      compile + start the server on $PORT (default 8080)
#   ./build.sh bench    compile + run the benchmark (args: timeLimitMs seeds)
#   ./build.sh jar      compile + build out/vrp-route-service.jar
set -euo pipefail
cd "$(dirname "$0")"

OUT=out
JAVAC_FLAGS=(--release 21 -Xlint:all,-serial -Werror -encoding UTF-8)

compile_main() {
  rm -rf "$OUT/main" && mkdir -p "$OUT/main"
  javac "${JAVAC_FLAGS[@]}" -d "$OUT/main" $(find src/main/java -name '*.java')
  cp -r src/main/resources/. "$OUT/main/"
}

compile_tests() {
  rm -rf "$OUT/shim" "$OUT/test" && mkdir -p "$OUT/shim" "$OUT/test"
  javac "${JAVAC_FLAGS[@]}" -d "$OUT/shim" $(find tools/junit-shim/src -name '*.java')
  javac "${JAVAC_FLAGS[@]}" -cp "$OUT/main:$OUT/shim" -d "$OUT/test" $(find src/test/java -name '*.java')
}

case "${1:-test}" in
  test)
    compile_main
    compile_tests
    echo "Running tests (JUnit shim runner)..."
    java -cp "$OUT/main:$OUT/shim:$OUT/test" junitshim.ShimRunner "$OUT/test"
    ;;
  run)
    compile_main
    exec java -cp "$OUT/main" io.github.urbanog.vrp.Main
    ;;
  bench)
    compile_main
    shift
    exec java -cp "$OUT/main" io.github.urbanog.vrp.bench.Benchmark "$@"
    ;;
  jar)
    compile_main
    jar --create --file "$OUT/vrp-route-service.jar" --main-class io.github.urbanog.vrp.Main -C "$OUT/main" .
    echo "built $OUT/vrp-route-service.jar"
    ;;
  *)
    echo "usage: $0 [test|run|bench|jar]" >&2
    exit 2
    ;;
esac
