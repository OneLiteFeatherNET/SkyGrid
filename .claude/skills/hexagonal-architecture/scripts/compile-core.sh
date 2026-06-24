#!/usr/bin/env bash
# Fast sanity check: compile only the framework-free modules (api + ports + core) with javac.
# No Gradle, no network.
#
# The api, ports, and core modules have zero Bukkit/Gson/Guice/Adventure dependencies, so they
# compile standalone. core depends on ports and api, and ports depends on api, so all three source
# roots are compiled together. This is a quick way to confirm an api/ports/core edit is well typed
# before running the full ./gradlew build. It does NOT compile the adapters or the platform modules
# or tests (those need Bukkit/Gson/Guice on the classpath).
#
# Usage: run from the repository root.
#   .claude/skills/hexagonal-architecture/scripts/compile-core.sh
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
API="$ROOT/api/src/main/java/net/onelitefeather/skygridng/api"
PORTS="$ROOT/ports/src/main/java/net/onelitefeather/skygridng/ports"
CORE="$ROOT/core/src/main/java/net/onelitefeather/skygridng/core"
OUT="$(mktemp -d)"

for dir in "$API" "$PORTS" "$CORE"; do
  if [ ! -d "$dir" ]; then
    echo "source directory not found at $dir" >&2
    exit 1
  fi
done

find "$API" "$PORTS" "$CORE" -name '*.java' > "$OUT/sources.txt"
COUNT="$(wc -l < "$OUT/sources.txt" | tr -d ' ')"

echo "Compiling $COUNT api + ports + core files with $(javac -version 2>&1) ..."
if javac -d "$OUT/classes" @"$OUT/sources.txt"; then
  echo "api + ports + core compile cleanly ($COUNT files)."
else
  echo "api + ports + core did NOT compile. Fix the type errors above before building." >&2
  exit 1
fi
