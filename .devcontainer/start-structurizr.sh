#!/bin/bash

echo "Starting Structurizr Lite on port 8080..."
java -jar /home/developer/.local/bin/structurizr-lite.war ${containerWorkspaceFolder}/docs > /tmp/structurizr.log 2>&1 &
echo "Structurizr Lite started. Access it at http://localhost:8080"
echo "Logs available at /tmp/structurizr.log"
