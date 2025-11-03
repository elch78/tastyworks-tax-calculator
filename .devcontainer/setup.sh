#!/bin/bash
set -e

echo "Installing system packages..."
sudo apt-get update
sudo apt-get install -y graphviz

echo "Configuring git..."
git config --global --add safe.directory ${containerWorkspaceFolder}

echo "Setting up Gradle..."
chmod +x gradlew
./gradlew --version

echo "Installing Claude Code..."
npm install -g @anthropic-ai/claude-code

echo "Setting up local bin directory..."
mkdir -p /home/developer/.local/bin

#echo "Downloading PlantUML..."
#wget -q -O /home/developer/.local/bin/plantuml.jar \
#  https://github.com/plantuml/plantuml/releases/download/v1.2024.7/plantuml.jar
#echo 'alias plantuml="java -jar /home/developer/.local/bin/plantuml.jar"' >> /home/developer/.bashrc

echo "Downloading Structurizr Lite..."
wget -q -O /home/developer/.local/bin/structurizr-lite.war \
  https://github.com/structurizr/lite/releases/latest/download/structurizr-lite.war

echo "Setup complete!"
