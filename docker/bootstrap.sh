#!/bin/sh
set -eu
exec java -jar /var/app/zkui.jar "$@"
