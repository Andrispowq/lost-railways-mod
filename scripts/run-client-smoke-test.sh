#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# SDL 3 must use the Xvfb display, with Mesa software drivers on GPU-less runners.
export SDL_VIDEO_DRIVER=x11
export LIBGL_ALWAYS_SOFTWARE=true
export GALLIUM_DRIVER=llvmpipe
export XDG_RUNTIME_DIR
XDG_RUNTIME_DIR=$(mktemp -d /tmp/lost-railways-runtime.XXXXXX)
trap 'rm -rf "$XDG_RUNTIME_DIR"' EXIT
mkdir -p build/diagnostics

# Bound startup too: a graphics error dialog can otherwise outlive the test runner.
# Keep the workflow's job timeout longer so diagnostics can still be uploaded.
timeout --signal=TERM --kill-after=30s 12m \
    xvfb-run -a --server-args='-screen 0 1280x720x24 +extension GLX +render -noreset' \
    bash -c '
        set -euo pipefail
        glxinfo -B
        vulkaninfo --summary
        exec ./gradlew --no-daemon --console=plain "$@" runClientGameTest
    ' smoke-test "$@" 2>&1 | tee build/diagnostics/client-smoke-test.log
