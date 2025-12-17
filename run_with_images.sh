#!/bin/bash
# Launcher script for RPGenerator with image generation

set -e

echo "🎮 RPGenerator with AI Image Generation"
echo "========================================"
echo ""

# Check if Python service is running
if ! curl -s http://127.0.0.1:5050/health > /dev/null 2>&1; then
    echo "⚠️  Image generation service is not running"
    echo "   Starting service in background..."
    echo ""

    # Check if venv exists
    if [ ! -d "cli/image_gen/venv" ]; then
        echo "📦 Setting up Python environment (first time only)..."
        cd cli/image_gen
        python3 -m venv venv
        source venv/bin/activate
        pip install -q --upgrade pip
        pip install -q -r requirements.txt
        cd ../..
        echo "✓ Python environment ready"
        echo ""
    fi

    # Start service in background
    echo "🚀 Starting image generation service..."
    cd cli/image_gen
    source venv/bin/activate
    python3 sd_service.py --preload --port 5050 > /tmp/rpg_image_service.log 2>&1 &
    SERVICE_PID=$!
    cd ../..

    echo "   Service PID: $SERVICE_PID"
    echo "   Waiting for service to start..."

    # Wait for service to be ready (max 60 seconds)
    for i in {1..60}; do
        if curl -s http://127.0.0.1:5050/health > /dev/null 2>&1; then
            echo "✓ Service ready!"
            echo ""
            break
        fi
        sleep 1
        echo -n "."
    done

    if ! curl -s http://127.0.0.1:5050/health > /dev/null 2>&1; then
        echo ""
        echo "✗ Service failed to start. Check logs: /tmp/rpg_image_service.log"
        exit 1
    fi
else
    echo "✓ Image generation service is already running"
    echo ""
fi

# Build the CLI if needed
if [ ! -f "cli/build/install/cli/bin/cli" ]; then
    echo "🔨 Building CLI (first time only)..."
    ./gradlew :cli:installDist
    echo ""
fi

# Run the CLI with image generation enabled
echo "🎮 Starting RPGenerator..."
echo "   Images will be saved to: ~/.rpgenerator/images/"
echo ""

# Pass through all arguments, add --image-gen
./cli/build/install/cli/bin/cli --image-gen "$@"

echo ""
echo "👋 Thanks for playing!"
