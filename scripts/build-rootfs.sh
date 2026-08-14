#!/bin/bash
# Fast rootfs build: no qemu. Native deps use npm platform packages
# (sharp/koffi ship linux-arm64 variants), so a plain x86_64 npm install
# with --os=linux --cpu=arm64 produces a correct arm64 dependency tree.
# Output: app/src/main/assets/rootfs.tar.gz
set -euo pipefail

UBUNTU_VER="24.04.4"
NODE_VER="v24.16.0"
DSH_VERSION="0.1.0-rc.6"
WEBAPP_VERSION="0.0.1-rc.1"
WORK="$GITHUB_WORKSPACE"
ROOTFS="$WORK/rootfs-build"

mkdir -p "$ROOTFS"
cd "$WORK"

# 1. Ubuntu base arm64 rootfs (never executed here, so no chroot needed)
curl -sL -o ubuntu-base.tar.gz \
  "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-${UBUNTU_VER}-base-arm64.tar.gz"
tar -xzf ubuntu-base.tar.gz -C "$ROOTFS"

# 2. Node.js official linux-arm64 build
curl -sL -o node.tar.xz "https://nodejs.org/dist/${NODE_VER}/node-${NODE_VER}-linux-arm64.tar.xz"
mkdir -p "$ROOTFS/opt"
tar -xJf node.tar.xz -C "$ROOTFS/opt"
mv "$ROOTFS/opt/node-${NODE_VER}-linux-arm64" "$ROOTFS/opt/node"

# 3. CA bundle (dsh calls DeepSeek API over HTTPS at runtime)
curl -sL -o cacert.pem "https://curl.se/ca/cacert.pem"
mkdir -p "$ROOTFS/etc/ssl/certs"
cp cacert.pem "$ROOTFS/etc/ssl/certs/ca-certificates.crt"

# 4. DNS (rootfs has no systemd-resolved; proot uses this file)
printf 'nameserver 8.8.8.8\nnameserver 223.5.5.5\n' > "$ROOTFS/etc/resolv.conf"

# 5. Cross-install dsh deps on the x86_64 runner (fast, ~5-10 min).
#    All @deepseek-ai packages are pinned to the version set verified on
#    a real arm64 install (scripts/deps-lock.json), so half-published
#    upstream releases cannot break the build.
mkdir -p /tmp/dshdeps && cd /tmp/dshdeps
python3 "$WORK/scripts/gen-package-json.py" "$WORK/scripts/deps-lock.json" > package.json
npm install --legacy-peer-deps --ignore-scripts --no-audit --no-fund --os=linux --cpu=arm64 2>&1 | tail -10

# 6. Move node_modules into the rootfs
mkdir -p "$ROOTFS/opt/dsh"
cp -a /tmp/dshdeps/node_modules "$ROOTFS/opt/dsh/node_modules"

# 7. dsh patch (disable node-pty chain) + entrypoint
mkdir -p "$ROOTFS/root/.dsh"
cp scripts/cordis.patch.yml "$ROOTFS/root/.dsh/cordis.patch.yml"
cp scripts/entrypoint.sh "$ROOTFS/opt/dsh/entry.sh"
chmod +x "$ROOTFS/opt/dsh/entry.sh"

# 8. Package
mkdir -p app/src/main/assets
tar --owner=0 --group=0 -czf app/src/main/assets/rootfs.tar.gz -C "$ROOTFS" .
ls -la app/src/main/assets/rootfs.tar.gz
