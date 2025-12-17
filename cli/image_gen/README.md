# Image Generation Service for RPGenerator

Local AI image generation for scene visualization using Stable Diffusion 1.5 on Apple Silicon.

## Features

- **Fast Generation**: 3-6 seconds per image on M1/M2/M3 Macs
- **Apple Silicon Optimized**: Uses MPS (Metal Performance Shaders) acceleration
- **HTTP API**: Simple REST API for image generation
- **Automatic Style Enhancement**: Adds fantasy/RPG art style keywords automatically

## Setup

### 1. Install Dependencies

```bash
cd cli/image_gen
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

**First run will download ~4GB of Stable Diffusion 1.5 model files from Hugging Face.**

### 2. Start the Service

```bash
./start_service.sh
```

Or manually:

```bash
source venv/bin/activate
python3 sd_service.py --preload --port 5050
```

The service will:
- Start on `http://127.0.0.1:5050`
- Pre-load the SD 1.5 model for faster first generation
- Use MPS acceleration if available (M1/M2/M3)

### 3. Run the Game with Image Generation

In a separate terminal:

```bash
./gradlew :cli:run --args="--mock --image-gen"
```

Or with real AI:

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
./gradlew :cli:run --args="--claude --image-gen"
```

## API Endpoints

### Health Check

```bash
curl http://127.0.0.1:5050/health
```

Response:
```json
{
  "status": "ok",
  "model": "SD 1.5",
  "device": "mps"
}
```

### Generate Image (Base64)

```bash
curl -X POST http://127.0.0.1:5050/generate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a dark forest with ancient trees, fantasy art",
    "steps": 20,
    "width": 512,
    "height": 512
  }'
```

### Generate Image (Save to File)

```bash
curl -X POST http://127.0.0.1:5050/generate_file \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a mystical cave entrance, fantasy art",
    "output_path": "/tmp/test_scene.png",
    "steps": 20
  }'
```

## Performance

On Apple Silicon M1:
- **20 steps (default)**: ~3-6 seconds
- **30 steps (better quality)**: ~8-12 seconds
- **512x512 (default)**: Fastest
- **768x768**: ~2x slower but better quality

## Prompt Enhancement

The service automatically enhances prompts with fantasy/RPG art style keywords:
- Input: `"a dark forest"`
- Enhanced: `"a dark forest, fantasy art, detailed, atmospheric lighting"`

This ensures consistent, high-quality fantasy scene generation.

## Troubleshooting

### Service Won't Start

```bash
# Check if port is already in use
lsof -i :5050

# Kill existing process
kill -9 <PID>
```

### Model Download Fails

```bash
# Set Hugging Face cache directory (optional)
export HF_HOME="/path/to/cache"

# Restart service - it will retry download
./start_service.sh
```

### Images Look Bad

- Increase steps: `"steps": 30` (slower but better quality)
- Increase resolution: `"width": 768, "height": 768"`
- Enhance prompt with more descriptive keywords
- Use negative_prompt to avoid unwanted elements

### MPS Not Available

If you see "Warning: MPS not available, using CPU":
- Ensure you're on macOS 12.3+ with Apple Silicon
- Update PyTorch: `pip install --upgrade torch`

## Generated Images Location

Images are saved to: `~/.rpgenerator/images/`

Each image is named with a timestamp: `scene_20231120_143052.png`

## Stopping the Service

Press `Ctrl+C` in the terminal running the service.

## Model Information

- **Model**: Stable Diffusion 1.5 (`runwayml/stable-diffusion-v1-5`)
- **Size**: ~4GB download
- **License**: CreativeML Open RAIL-M
- **Cache Location**: `~/.cache/huggingface/`

## Future Improvements

- [ ] Support for SDXL (better quality, ~8-15s)
- [ ] Image-to-image for scene variations
- [ ] LoRA support for consistent character generation
- [ ] Batch generation for faster multiple scenes
- [ ] ASCII art fallback when service unavailable
