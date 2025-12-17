#!/bin/bash
# Start the Stable Diffusion image generation service

cd "$(dirname "$0")"

# Activate virtual environment
source venv/bin/activate

# Start the service with preloaded model
echo "Starting Stable Diffusion service on port 5050..."
echo "This will download ~4GB of model files on first run."
echo ""

python3 sd_service.py --preload --port 5050
