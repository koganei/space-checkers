#!/usr/bin/env bash
TOOLCHAIN="$HOME/.local/share/space-checkers-toolchain"
export JAVA_HOME="$(find "$TOOLCHAIN" -maxdepth 1 -type d -name 'jdk-*' | head -1)"
export ANDROID_HOME="$TOOLCHAIN/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$TOOLCHAIN/gradle-8.9/bin:$PATH"
