#!/usr/bin/env python3
"""
Generate Zen Bell Audio Asset
Creates a 2-second, calm, 432Hz sine wave that smoothly fades out (meditation chime effect)
"""

import wave
import struct
import math

# Audio parameters
SAMPLE_RATE = 44100  # CD quality
DURATION = 2.0      # 2 seconds
FREQUENCY = 432.0   # Zen frequency (Hz)
VOLUME = 0.3        # 30% of maximum volume

# Calculate total samples
TOTAL_SAMPLES = int(SAMPLE_RATE * DURATION)

def generate_zen_bell():
    """Generate a calm 432Hz sine wave with smooth fade-out"""
    
    # Create wave file
    with wave.open('zen_bell.wav', 'w') as wav_file:
        wav_file.setnchannels(1)  # Mono
        wav_file.setsampwidth(2)  # 16-bit
        wav_file.setframerate(SAMPLE_RATE)
        
        # Generate audio samples
        for sample in range(TOTAL_SAMPLES):
            # Calculate time position
            t = sample / SAMPLE_RATE
            
            # Calculate fade-out (exponential decay)
            fade_start = 0.3  # Start fading after 0.3 seconds
            if t > fade_start:
                fade_duration = DURATION - fade_start
                fade_progress = (t - fade_start) / fade_duration
                fade_factor = math.exp(-3 * fade_progress)  # Exponential decay
            else:
                fade_factor = 1.0
            
            # Generate sine wave with fade
            amplitude = VOLUME * fade_factor
            value = amplitude * math.sin(2 * math.pi * FREQUENCY * t)
            
            # Convert to 16-bit integer
            sample_value = int(value * 32767)
            
            # Pack as signed 16-bit little-endian
            wav_file.writeframes(struct.pack('<h', sample_value))
    
    print(f"✅ Zen bell generated: zen_bell.wav")
    print(f"   Duration: {DURATION}s")
    print(f"   Frequency: {FREQUENCY}Hz")
    print(f"   Sample rate: {SAMPLE_RATE}Hz")
    print(f"   Total samples: {TOTAL_SAMPLES}")

if __name__ == "__main__":
    generate_zen_bell()
