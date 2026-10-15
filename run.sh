#!/bin/sh
set -eu
: "${ZK_SERVER:?Set ZK_SERVER to a reachable ZooKeeper ensemble}"
exec docker run -d --name "${CONTAINER_NAME:-zkui}" \
  -p "${BIND_ADDRESS:-127.0.0.1}:${HTTP_PORT:-9090}:9090" \
  -e ZK_SERVER -e USER_SET -e LOGIN_MESSAGE -e ZKUI_BASEURL \
  --mount "type=volume,source=${DATA_VOLUME:-zkui-data},target=/var/app/data" \
  "${IMAGE:-zkui:local}"
