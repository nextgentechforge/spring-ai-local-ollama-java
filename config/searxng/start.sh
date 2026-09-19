#!/bin/sh
set -eu
# A fresh runtime-only signing secret is sufficient for this private, stateless search instance.
export SEARXNG_SECRET="$(head -c 32 /dev/urandom | base64)"
exec /usr/local/searxng/entrypoint.sh
