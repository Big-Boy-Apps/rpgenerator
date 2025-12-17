#!/bin/bash
# Quick test script for the image generation service

echo "Testing Stable Diffusion service..."
echo ""

# Check if service is running
echo "1. Checking if service is healthy..."
response=$(curl -s http://127.0.0.1:5050/health)

if [ $? -eq 0 ]; then
    echo "✓ Service is running!"
    echo "   Response: $response"
else
    echo "✗ Service is not running"
    echo "   Start it with: ./start_service.sh"
    exit 1
fi

echo ""
echo "2. Testing image generation..."
echo "   Generating: 'a mystical forest with glowing mushrooms, fantasy art'"
echo "   This will take ~3-6 seconds on M1..."

curl -X POST http://127.0.0.1:5050/generate_file \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a mystical forest with glowing mushrooms, fantasy art",
    "output_path": "/tmp/test_rpg_scene.png",
    "steps": 20
  }' 2>/dev/null | python3 -m json.tool

if [ -f "/tmp/test_rpg_scene.png" ]; then
    echo ""
    echo "✓ Image generated successfully!"
    echo "   Opening image..."
    open /tmp/test_rpg_scene.png
else
    echo "✗ Image generation failed"
fi
