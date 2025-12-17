#!/usr/bin/env python3
"""
Stable Diffusion 1.5 service using PyTorch MPS for Apple Silicon.
Provides a simple HTTP API for generating images from text prompts.
"""

import sys
import os
from pathlib import Path
import base64
from io import BytesIO

try:
    from flask import Flask, request, jsonify
    from PIL import Image
    import torch
    from diffusers import StableDiffusionPipeline
except ImportError as e:
    print(f"Error: Missing dependencies. Run: pip install -r requirements.txt", file=sys.stderr)
    print(f"Details: {e}", file=sys.stderr)
    sys.exit(1)

app = Flask(__name__)

# Global model instance (loaded once)
pipe = None

def load_model():
    """Load SD 1.5 model (happens once on startup)"""
    global pipe
    if pipe is None:
        print("Loading Stable Diffusion 1.5 model for Apple Silicon...", file=sys.stderr)

        # Check if MPS is available
        if not torch.backends.mps.is_available():
            print("Warning: MPS not available, using CPU (will be slow)", file=sys.stderr)
            device = "cpu"
        else:
            device = "mps"
            print(f"Using Metal Performance Shaders (MPS) acceleration", file=sys.stderr)

        # Load SD 1.5 model
        pipe = StableDiffusionPipeline.from_pretrained(
            "runwayml/stable-diffusion-v1-5",
            torch_dtype=torch.float16 if device == "mps" else torch.float32,
            safety_checker=None  # Disable for speed
        )
        pipe = pipe.to(device)

        print("Model loaded successfully!", file=sys.stderr)
    return pipe

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    device = "mps" if torch.backends.mps.is_available() else "cpu"
    return jsonify({
        "status": "ok",
        "model": "SD 1.5",
        "device": device
    })

@app.route('/generate', methods=['POST'])
def generate():
    """
    Generate an image from a text prompt.

    Request JSON:
    {
        "prompt": "a dark forest with ancient trees, fantasy art",
        "negative_prompt": "blurry, low quality",
        "steps": 20,
        "width": 512,
        "height": 512,
        "guidance_scale": 7.5
    }

    Response JSON:
    {
        "image": "base64_encoded_png",
        "prompt": "original prompt"
    }
    """
    try:
        data = request.get_json()

        if not data or 'prompt' not in data:
            return jsonify({"error": "Missing 'prompt' in request"}), 400

        prompt = data['prompt']
        negative_prompt = data.get('negative_prompt', 'blurry, low quality, distorted, ugly')
        steps = data.get('steps', 20)  # 20 steps = ~3-6 seconds on M1
        width = data.get('width', 512)
        height = data.get('height', 512)
        guidance_scale = data.get('guidance_scale', 7.5)

        print(f"Generating image for: {prompt[:60]}...", file=sys.stderr)

        # Load model if not already loaded
        model = load_model()

        # Generate image
        with torch.inference_mode():
            result = model(
                prompt=prompt,
                negative_prompt=negative_prompt,
                num_inference_steps=steps,
                width=width,
                height=height,
                guidance_scale=guidance_scale
            )

        image = result.images[0]

        # Convert PIL Image to base64
        buffered = BytesIO()
        image.save(buffered, format="PNG")
        img_base64 = base64.b64encode(buffered.getvalue()).decode('utf-8')

        print(f"Image generated successfully!", file=sys.stderr)

        return jsonify({
            "image": img_base64,
            "prompt": prompt,
            "width": width,
            "height": height
        })

    except Exception as e:
        print(f"Error generating image: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route('/generate_file', methods=['POST'])
def generate_file():
    """
    Generate an image and save to file.

    Request JSON:
    {
        "prompt": "a dark forest with ancient trees, fantasy art",
        "output_path": "/path/to/output.png",
        "steps": 20
    }

    Response JSON:
    {
        "path": "/path/to/output.png",
        "prompt": "original prompt"
    }
    """
    try:
        data = request.get_json()

        if not data or 'prompt' not in data or 'output_path' not in data:
            return jsonify({"error": "Missing 'prompt' or 'output_path'"}), 400

        prompt = data['prompt']
        output_path = Path(data['output_path'])
        negative_prompt = data.get('negative_prompt', 'blurry, low quality, distorted, ugly')
        steps = data.get('steps', 20)
        width = data.get('width', 512)
        height = data.get('height', 512)
        guidance_scale = data.get('guidance_scale', 7.5)

        # Create output directory if needed
        output_path.parent.mkdir(parents=True, exist_ok=True)

        print(f"Generating image to {output_path}...", file=sys.stderr)

        # Load model and generate
        model = load_model()

        with torch.inference_mode():
            result = model(
                prompt=prompt,
                negative_prompt=negative_prompt,
                num_inference_steps=steps,
                width=width,
                height=height,
                guidance_scale=guidance_scale
            )

        image = result.images[0]

        # Save to file
        image.save(output_path)

        print(f"Image saved to {output_path}", file=sys.stderr)

        return jsonify({
            "path": str(output_path),
            "prompt": prompt
        })

    except Exception as e:
        print(f"Error generating image: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description='Stable Diffusion Service for Apple Silicon')
    parser.add_argument('--port', type=int, default=5050, help='Port to run on')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind to')
    parser.add_argument('--preload', action='store_true', help='Load model on startup')

    args = parser.parse_args()

    if args.preload:
        print("Preloading model...", file=sys.stderr)
        load_model()

    print(f"Starting Stable Diffusion service on {args.host}:{args.port}", file=sys.stderr)
    print(f"API endpoints:", file=sys.stderr)
    print(f"  - POST /generate (returns base64 image)", file=sys.stderr)
    print(f"  - POST /generate_file (saves to file)", file=sys.stderr)
    print(f"  - GET /health (check status)", file=sys.stderr)
    app.run(host=args.host, port=args.port, debug=False)
