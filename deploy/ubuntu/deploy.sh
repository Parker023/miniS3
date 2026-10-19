#!/usr/bin/env bash
# Build on this machine, copy to the Ubuntu host and (re)install there.
#
#   deploy/ubuntu/deploy.sh user@ubuntu-host
#
# Needs SSH access (e.g. over Tailscale) and sudo on the host.
set -euo pipefail

HOST="${1:?usage: deploy.sh user@host}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

"$ROOT/gradlew" -p "$ROOT" :service:bootJar -q

ssh "$HOST" 'mkdir -p /tmp/minis3-deploy'
scp -q "$ROOT/service/build/libs/minis3-storage-service.jar" \
       "$ROOT/deploy/ubuntu/setup.sh" \
       "$ROOT/deploy/ubuntu/minis3.service" \
       "$HOST:/tmp/minis3-deploy/"
ssh -t "$HOST" 'sudo bash /tmp/minis3-deploy/setup.sh /tmp/minis3-deploy/minis3-storage-service.jar && rm -rf /tmp/minis3-deploy'
