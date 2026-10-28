#!/usr/bin/env bash
set -euo pipefail

# Ubuntu 24.04; run as root (sudo in GitHub Actions, root during Docker build).
apt-get update
apt-get install -y --no-install-recommends \
    xvfb xauth mesa-utils libgl1-mesa-dri libglx-mesa0 \
    libvulkan1 mesa-vulkan-drivers vulkan-tools \
    libx11-6 libx11-xcb1 libxcursor1 libxrandr2 libxi6 libxinerama1 libasound2t64
