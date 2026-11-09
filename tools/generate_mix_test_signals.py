#!/usr/bin/env python3
"""Write clean and noisy mix/edit fixtures for SwaraLab.

Stdlib only. Run from the repo:

    python tools/generate_mix_test_signals.py

Writes testdata/mix-fixtures/ (classic A4 pair + phrase pair) and
testdata/mix-fixtures/batch/ (8 clean/noisy recipes for Mix & Save).
"""

from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 48_000
DURATION = 5.0
FREQUENCY = 440.0
VOLUME = 0.5
NOISE_LEVEL = 0.2
SEED = 440
BATCH_DURATION = 3.0
BATCH_GAP = 0.5

RECIPES = [
    {"id": "01_a4_white", "hz": 440.0, "volume": 0.50, "white": 0.20, "hiss": 0.0, "rumble": 0.0, "hum": 0.0, "knock": False, "second": None},
    {"id": "02_c4_hiss", "hz": 261.63, "volume": 0.48, "white": 0.08, "hiss": 0.14, "rumble": 0.0, "hum": 0.0, "knock": False, "second": None},
    {"id": "03_e4_rumble", "hz": 329.63, "volume": 0.50, "white": 0.07, "hiss": 0.0, "rumble": 0.09, "hum": 0.0, "knock": False, "second": None},
    {"id": "04_a3_hum", "hz": 220.0, "volume": 0.48, "white": 0.10, "hiss": 0.0, "rumble": 0.0, "hum": 0.06, "knock": False, "second": None},
    {"id": "05_melody_knock", "hz": 392.0, "volume": 0.46, "white": 0.10, "hiss": 0.0, "rumble": 0.0, "hum": 0.0, "knock": True, "second": 523.25},
    {"id": "06_a5_air", "hz": 880.0, "volume": 0.40, "white": 0.10, "hiss": 0.16, "rumble": 0.0, "hum": 0.0, "knock": False, "second": None},
    {"id": "07_quiet_lowsnr", "hz": 349.23, "volume": 0.26, "white": 0.14, "hiss": 0.08, "rumble": 0.0, "hum": 0.0, "knock": False, "second": None},
    {"id": "08_fifth", "hz": 440.0, "volume": 0.42, "white": 0.12, "hiss": 0.0, "rumble": 0.0, "hum": 0.0, "knock": False, "second": 660.0},
]


def repo_root() -> Path:
    here = Path(__file__).resolve()
    for parent in [here.parent, *here.parents]:
        if (parent / "settings.gradle.kts").is_file():
            return parent
    return Path.cwd()


def pack_sample(value: float) -> bytes:
    clamped = max(-1.0, min(1.0, value))
    return struct.pack("<h", int(clamped * 32767.0))


def write_wav(path: Path, samples: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as wav_file:
        wav_file.setparams((1, 2, SAMPLE_RATE, len(samples), "NONE", "not compressed"))
        wav_file.writeframes(b"".join(pack_sample(sample) for sample in samples))
    print(f"Wrote {path} ({len(samples) / SAMPLE_RATE:.1f}s)")


def tone_at(index: int, hz: float = FREQUENCY, volume: float = VOLUME) -> float:
    t = index / SAMPLE_RATE
    return math.sin(2.0 * math.pi * hz * t) * volume


def continuous(add_noise: bool) -> list[float]:
    rng = random.Random(SEED)
    frames = int(SAMPLE_RATE * DURATION)
    out = []
    for i in range(frames):
        sample = tone_at(i)
        if add_noise:
            sample += rng.uniform(-1.0, 1.0) * NOISE_LEVEL
        out.append(sample)
    return out


def phrase(add_noise: bool) -> list[float]:
    rng = random.Random(SEED + 1)
    frames = int(SAMPLE_RATE * DURATION)
    gap = SAMPLE_RATE
    sustain = frames - 2 * gap
    out = []
    for i in range(frames):
        singing = gap <= i < gap + sustain
        sample = tone_at(i) if singing else 0.0
        if add_noise:
            sample += rng.uniform(-1.0, 1.0) * NOISE_LEVEL
        out.append(sample)
    return out


def lcg(seed: int) -> callable:
    state = [seed | 1]

    def next_float() -> float:
        state[0] = (state[0] * 1_103_515_245 + 12_345) & 0x7FFFFFFF
        return state[0] / 2147483647.0

    return next_float


def recipe_samples(recipe: dict, noisy: bool) -> list[float]:
    n = int(SAMPLE_RATE * BATCH_DURATION)
    gap = int(SAMPLE_RATE * BATCH_GAP)
    sustain_end = n - gap
    nxt = lcg(sum(ord(c) for c in recipe["id"]) & 0x7FFFFFFF)
    knock_from = int(SAMPLE_RATE * 0.22)
    knock_to = int(SAMPLE_RATE * 0.24)
    split = gap + (sustain_end - gap) // 2
    out = []
    for i in range(n):
        singing = gap <= i < sustain_end
        hz = recipe["second"] if (recipe["second"] is not None and i >= split) else recipe["hz"]
        t = i / SAMPLE_RATE
        sample = tone_at(i, hz=hz, volume=recipe["volume"]) if singing else 0.0
        if noisy:
            sample += (nxt() * 2.0 - 1.0) * recipe["white"]
            sample += (nxt() * 2.0 - 1.0) * recipe["hiss"] * math.sin(2.0 * math.pi * 8400.0 * t)
            sample += recipe["rumble"] * math.sin(2.0 * math.pi * 58.0 * t)
            sample += recipe["hum"] * math.sin(2.0 * math.pi * 50.0 * t)
            if recipe["knock"] and knock_from <= i <= knock_to:
                sample += 0.32
        out.append(sample)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate SwaraLab mix/edit test WAVs")
    parser.add_argument("--out", type=Path, default=None, help="Output folder (default: testdata/mix-fixtures)")
    args = parser.parse_args()
    out_dir = args.out or (repo_root() / "testdata" / "mix-fixtures")
    write_wav(out_dir / "clean_test_signal.wav", continuous(add_noise=False))
    write_wav(out_dir / "noisy_test_signal.wav", continuous(add_noise=True))
    write_wav(out_dir / "clean_test_phrase.wav", phrase(add_noise=False))
    write_wav(out_dir / "noisy_test_phrase.wav", phrase(add_noise=True))
    batch = out_dir / "batch"
    for recipe in RECIPES:
        write_wav(batch / f"{recipe['id']}_clean.wav", recipe_samples(recipe, noisy=False))
        write_wav(batch / f"{recipe['id']}_noisy.wav", recipe_samples(recipe, noisy=True))


if __name__ == "__main__":
    main()
