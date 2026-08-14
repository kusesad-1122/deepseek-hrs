#!/bin/bash
# Entry point inside the rootfs: boot dsh web profile.
cd /root || exit 1
mkdir -p /root/.dsh /root/.npm 2>/dev/null
exec /opt/node/bin/node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --profile web
