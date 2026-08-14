#!/bin/bash
# Build the arm64 rootfs (Ubuntu base + Node + dsh) on a x86_64 runner
# using qemu-user-static. Output: app/src/main/assets/rootfs.tar.gz
set -euo pipefail

UBUNTU_VER="24.04.4"
NODE_VER="v24.16.0"
DSH_VERSION="0.1.0-rc.6"
WEBAPP_VERSION="0.0.1-rc.1"
WORK="$GITHUB_WORKSPACE"
ROOTFS="$WORK/rootfs-build"

sudo apt-get update -qq
sudo apt-get install -y -qq qemu-user-static xz-utils

mkdir -p "$ROOTFS"
cd "$WORK"

# 1. Ubuntu base rootfs
curl -sL -o ubuntu-base.tar.gz \
  "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-${UBUNTU_VER}-base-arm64.tar.gz"
sudo tar -xzf ubuntu-base.tar.gz -C "$ROOTFS"

# 2. qemu static binary so arm64 binaries run
sudo cp /usr/bin/qemu-aarch64-static "$ROOTFS/usr/bin/"
sudo cp /etc/resolv.conf "$ROOTFS/etc/resolv.conf"

# 3. chroot mounts
sudo mount -t proc /proc "$ROOTFS/proc" || true
sudo mount --bind /sys "$ROOTFS/sys" || true
sudo mount --bind /dev "$ROOTFS/dev" || true
sudo mount --bind /dev/pts "$ROOTFS/dev/pts" || true
trap 'sudo umount -l "$ROOTFS/dev/pts" "$ROOTFS/dev" "$ROOTFS/sys" "$ROOTFS/proc" 2>/dev/null || true' EXIT

# 4. install basics in the arm64 rootfs
sudo chroot "$ROOTFS" /bin/bash -lc '
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends ca-certificates bash coreutils
'

# 5. Node.js official arm64 build
curl -sL -o node.tar.xz "https://nodejs.org/dist/${NODE_VER}/node-${NODE_VER}-linux-arm64.tar.xz"
sudo mkdir -p "$ROOTFS/opt"
sudo tar -xJf node.tar.xz -C "$ROOTFS/opt"
sudo mv "$ROOTFS/opt/node-${NODE_VER}-linux-arm64" "$ROOTFS/opt/node"

# 6. npm install dsh inside the arm64 rootfs (qemu emulated, slow)
sudo chroot "$ROOTFS" /bin/bash -lc '
set -e
export PATH=/opt/node/bin:$PATH HOME=/root
export npm_config_audit=false npm_config_fund=false npm_config_update_notifier=false
mkdir -p /opt/dsh && cd /opt/dsh
npm init -y >/dev/null 2>&1
npm install --no-audit --no-fund --no-save \
  @deepseek-ai/dsh@'"$DSH_VERSION"' \
  @deepseek-ai/dsh-web-app@'"$WEBAPP_VERSION"' 2>&1 | tail -20
'

# 7. dsh patch (disable node-pty chain) + entrypoint
sudo mkdir -p "$ROOTFS/root/.dsh"
sudo cp scripts/cordis.patch.yml "$ROOTFS/root/.dsh/cordis.patch.yml"
sudo cp scripts/entrypoint.sh "$ROOTFS/opt/dsh/entry.sh"
sudo chmod +x "$ROOTFS/opt/dsh/entry.sh"

# 8. package
mkdir -p app/src/main/assets
sudo tar --owner=0 --group=0 -czf app/src/main/assets/rootfs.tar.gz -C "$ROOTFS" .
ls -la app/src/main/assets/rootfs.tar.gz
