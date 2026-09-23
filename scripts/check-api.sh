#!/usr/bin/env bash
set -euo pipefail
# Host tooling may export DEBUG=release, which Spring interprets as debug mode.
export DEBUG=false
cd "$(dirname "$0")/../apps/api"
if [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -S "$HOME/.colima/default/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
fi
exec ./mvnw -B -ntp "$@"
