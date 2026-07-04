#!/usr/bin/env bash
# Fast sanity check: compile only the platform-neutral modules (api + ports + core) with javac.
# No Gradle, no network.
#
# The api, ports, and core modules depend on no platform/framework code except Adventure
# (net.kyori.adventure), which is a compileOnly, cross-platform text API. So they compile standalone
# as long as the Adventure jars are on the classpath; this script pulls them from the local Gradle
# cache (already downloaded by a normal build). core depends on ports and api, and ports depends on
# api, so all three source roots are compiled together. This is a quick way to confirm an
# api/ports/core edit is well typed before running the full ./gradlew build. It does NOT compile the
# adapters, the platform modules, or tests (those need Bukkit/Gson/Guice on the classpath).
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

# Adventure (compileOnly) must be on the classpath. Find its jars in the Gradle cache.
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
CP="$(find "$GRADLE_CACHE" \
        \( -name 'adventure-api-*.jar' -o -name 'adventure-key-*.jar' -o -name 'examination-api-*.jar' \) \
        2>/dev/null | grep -vE 'sources|javadoc' | sort -u | tr '\n' ':')"
if [ -z "$CP" ]; then
  echo "Adventure jars not found in the Gradle cache. Run './gradlew :core:compileJava' once to" >&2
  echo "download them, then re-run this script." >&2
  exit 1
fi

find "$API" "$PORTS" "$CORE" -name '*.java' > "$OUT/sources.txt"
COUNT="$(wc -l < "$OUT/sources.txt" | tr -d ' ')"

echo "Compiling $COUNT api + ports + core files with $(javac -version 2>&1) ..."
if javac -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"; then
  echo "api + ports + core compile cleanly ($COUNT files)."
else
  echo "api + ports + core did NOT compile. Fix the type errors above before building." >&2
  exit 1
fi
