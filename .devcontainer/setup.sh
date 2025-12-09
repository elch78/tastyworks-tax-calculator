#!/bin/bash
set -e

echo "Configuring git..."
git config --global --add safe.directory "$(pwd)"

echo "Installing Claude Code..."
npm install -g @anthropic-ai/claude-code

echo "Setting up local bin directory..."
mkdir -p /home/developer/.local/bin

echo "Downloading Structurizr Lite..."
wget -q -O /home/developer/.local/bin/structurizr-lite.war \
  https://github.com/structurizr/lite/releases/latest/download/structurizr-lite.war

echo "Installing Opencode..."

curl -fsSL https://opencode.ai/install | bash

# Intellij changes the config home directory to /.jbdevcontainer/config. User $XDG_CONFIG_HOME to put the files in the right place
# https://www.jetbrains.com/help/idea/dev-container-limitations.html
mkdir -p /.jbdevcontainer/config/opencode
cp .devcontainer/opencode/* /.jbdevcontainer/config/opencode/

echo "Opencode installed"

echo "Setup complete!"
