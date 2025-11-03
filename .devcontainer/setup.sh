#!/bin/bash
set -e

WORKSPACE_DIR="${1:-$(pwd)}"

echo "Configuring git..."
git config --global --add safe.directory "$WORKSPACE_DIR"

echo "Setting up Gradle..."
chmod +x gradlew
./gradlew --version

echo "Installing Claude Code..."
npm install -g @anthropic-ai/claude-code

echo "Setting up local bin directory..."
mkdir -p /home/developer/.local/bin

echo "Downloading Structurizr Lite..."
wget -q -O /home/developer/.local/bin/structurizr-lite.war \
  https://github.com/structurizr/lite/releases/latest/download/structurizr-lite.war

echo "Setup complete!"
