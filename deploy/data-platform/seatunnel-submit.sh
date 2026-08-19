#!/bin/sh
set -eu

# The existing cluster binds its Hazelcast port to the Docker bridge only.
# Local mode avoids coupling platform jobs to that dynamic bridge address.
exec /opt/data-center/shared/seatunnel/current/bin/seatunnel.sh -m local "$@"
