#!/usr/bin/env python3
"""
VoiceBoard Model Builder
Downloads and quantizes Whisper models for optimal mobile performance
"""

import os
import sys
import requests
import hashlib
from pathlib import Path
from tqdm import tqdm

# Model configurations
MODELS = {
    "tiny.en": {
        "original": "https://openaipublic.azureedge.net/main/whisper/models/d3dd57d32accea0b295c96e26691aa14d8822fac7d9d27d5dc00b4ca2826dd03/tiny.en.pt",
        "ggml": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin",
        "size_mb": 39,
        "description": "Fastest, lowest accuracy - good for testing"
    },
    "base.en": {
        "original": "https://openaipublic.azureedge.net/main/whisper/models/25a8566e1d0c1e2231d1c762132cd20e0f96a85d16145c3a00adf5d1ac670ead/base.en.pt",
        "ggml": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin", 
        "size_mb": 148,
        "description": "Balanced speed and accuracy - recommended default"
    },
    "small.en": {
        "original": "https://openaipublic.azureedge.net/main/whisper/models/f953ad0fd29cacd07d5a9eda5624af0f6bcf2258be67c92b79389873d91e0872/small.en.pt",
        "ggml": "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin",
        "size_mb": 244, 
        "description": "Slower, higher accuracy - for powerful devices"
    }
}

def download_file(url: str, filepath: Path, description: str = "") -> bool:
    """Download a file with progress bar"""
    try:
        response = requests.get(url, stream=True)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        
        with open(filepath, 'wb') as file:
            with tqdm(
                total=total_size,
                unit='B',
                unit_scale=True,
                desc=description or filepath.name
            ) as pbar:
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        file.write(chunk)
                        pbar.update(len(chunk))
        
        return True
        
    except Exception as e:
        print(f"❌ Error downloading {url}: {e}")
        if filepath.exists():
            filepath.unlink()
        return False

def verify_file_size(filepath: Path, expected_size_mb: int, tolerance: float = 0.1) -> bool:
    """Verify downloaded file size"""
    if not filepath.exists():
        return False
        
    actual_size_mb = filepath.stat().st_size / (1024 * 1024)
    expected_min = expected_size_mb * (1 - tolerance)
    expected_max = expected_size_mb * (1 + tolerance)
    
    return expected_min <= actual_size_mb <= expected_max

def main():
    print("🎙️  VoiceBoard Model Builder")
    print("=" * 50)
    
    # Get project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    models_dir = project_root / "models"
    
    # Create models directory
    models_dir.mkdir(exist_ok=True)
    
    print(f"📁 Models directory: {models_dir}")
    print()
    
    # Process each model
    for model_name, model_info in MODELS.items():
        print(f"🔄 Processing {model_name}...")
        print(f"   {model_info['description']}")
        print(f"   Size: ~{model_info['size_mb']} MB")
        
        # Download GGML version (pre-quantized for mobile)
        ggml_filename = f"ggml-{model_name}-q5_1.bin"
        ggml_filepath = models_dir / ggml_filename
        
        if ggml_filepath.exists() and verify_file_size(ggml_filepath, model_info['size_mb']):
            print(f"   ✅ {ggml_filename} already exists and verified")
        else:
            print(f"   ⬇️  Downloading {ggml_filename}...")
            if download_file(model_info['ggml'], ggml_filepath, f"{model_name} GGML"):
                if verify_file_size(ggml_filepath, model_info['size_mb']):
                    print(f"   ✅ {ggml_filename} downloaded and verified")
                else:
                    print(f"   ⚠️  {ggml_filename} size verification failed")
            else:
                print(f"   ❌ Failed to download {ggml_filename}")
                continue
        
        print()
    
    # Create model manifest
    manifest_path = models_dir / "manifest.json"
    manifest = {
        "models": {},
        "created_at": "2024-01-01T00:00:00Z",
        "version": "1.0.0"
    }
    
    for model_name, model_info in MODELS.items():
        ggml_filename = f"ggml-{model_name}-q5_1.bin"
        ggml_filepath = models_dir / ggml_filename
        
        if ggml_filepath.exists():
            manifest["models"][model_name] = {
                "filename": ggml_filename,
                "size_bytes": ggml_filepath.stat().st_size,
                "size_mb": round(ggml_filepath.stat().st_size / (1024 * 1024), 1),
                "description": model_info['description'],
                "format": "ggml-q5_1"
            }
    
    # Write manifest
    import json
    with open(manifest_path, 'w') as f:
        json.dump(manifest, f, indent=2)
    
    print(f"📋 Created model manifest: {manifest_path}")
    print()
    
    # Summary
    total_size_mb = sum(
        info['size_mb'] for info in manifest['models'].values()
    )
    
    print("📊 Summary:")
    print(f"   Models downloaded: {len(manifest['models'])}")
    print(f"   Total size: ~{total_size_mb:.1f} MB")
    print(f"   Location: {models_dir}")
    print()
    
    if len(manifest['models']) > 0:
        print("🎉 Model setup complete!")
        print()
        print("📱 Usage:")
        print("   • iOS: Models will be bundled with the app")
        print("   • Android: Models will be downloaded on first run")
        print()
        print("🔧 Next steps:")
        print("   1. Run setup_whisper.sh to build native libraries")
        print("   2. Build and test your apps")
    else:
        print("❌ No models were successfully downloaded")
        sys.exit(1)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\\n⚠️  Download cancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\\n❌ Error: {e}")
        sys.exit(1)