#!/usr/bin/env bash
# Fast sanity check: compile only the domain module (the platform-neutral feature packages) with javac.
# No Gradle, no network.
#
# The domain module depends on no platform/framework code except Adventure (net.kyori.adventure),
# which is a compileOnly, cross-platform text API. So it compiles standalone as long as the Adventure
# jars are on the classpath; this script pulls them from the local Gradle cache (already downloaded by
# a normal build). This is a quick way to confirm a domain edit is well typed before running the full
# ./gradlew build. It does NOT compile the plugin module or tests (those need Bukkit/Jackson/Guice).
#
# Usage: run from the repository root.
#   .claude/skills/hexagonal-architecture/scripts/compile-core.sh
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
DOMAIN="$ROOT/domain/src/main/java"
OUT="$(mktemp -d)"

for dir in "$DOMAIN"; do
  if [ ! -d "$dir" ]; then
    echo "source directory not found at $dir" >&2
    exit 1
  fi
done

# Adventure (compileOnly) must be on the classpath. Find its jars in the Gradle cache.
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
CP="$(find "$GRADLE_CACHE" \
        \( -name 'adventure-api-*.jar' -o -name 'adventure-key-*.jar' -o -name 'examination-api-*.jar' \) \
        2>/dev/null | grep -vE 'sources|javadoc' | sort -u | tr '\n' ':')"
if [ -z "$CP" ]; then
  echo "Adventure jars not found in the Gradle cache. Run './gradlew :domain:compileJava' once to" >&2
  echo "download them, then re-run this script." >&2
  exit 1
fi

find "$DOMAIN" -name '*.java' > "$OUT/sources.txt"
COUNT="$(wc -l < "$OUT/sources.txt" | tr -d ' ')"

echo "Compiling $COUNT domain files with $(javac -version 2>&1) ..."
if javac -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"; then
  echo "domain compiles cleanly ($COUNT files)."
else
  echo "domain did NOT compile. Fix the type errors above before building." >&2
  exit 1
fi
