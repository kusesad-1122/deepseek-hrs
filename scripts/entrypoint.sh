#!/bin/bash
# Entry point inside the rootfs: boot dsh web profile.
export HOME=/root DSH_HOME=/root/.dsh
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/node/bin
export LANG=C.UTF-8
mkdir -p /root/.dsh /root/.npm 2>/dev/null
cd /sdcard || cd /root || exit 1
exec /opt/node/bin/node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --profile web
