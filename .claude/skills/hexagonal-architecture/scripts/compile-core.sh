#!/usr/bin/env bash
# Fast sanity check: compile only the pure core with javac. No Gradle, no network.
#
# The core has zero Bukkit/Gson/Guice/Adventure dependencies, so it compiles standalone. This is a
# quick way to confirm a core edit is well typed before running the full ./gradlew build. It does
# NOT compile adapters, bootstrap, or tests (those need the platform jars).
#
# Usage: run from the repository root.
#   .claude/skills/hexagonal-architecture/scripts/compile-core.sh
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
CORE="$ROOT/src/main/java/net/onelitefeather/skygridng/core"
OUT="$(mktemp -d)"

if [ ! -d "$CORE" ]; then
  echo "core directory not found at $CORE" >&2
  exit 1
fi

find "$CORE" -name '*.java' > "$OUT/sources.txt"
COUNT="$(wc -l < "$OUT/sources.txt" | tr -d ' ')"

echo "Compiling $COUNT core files with $(javac -version 2>&1) ..."
if javac -d "$OUT/classes" @"$OUT/sources.txt"; then
  echo "Core compiles cleanly ($COUNT files)."
else
  echo "Core did NOT compile. Fix the type errors above before building." >&2
  exit 1
fi
