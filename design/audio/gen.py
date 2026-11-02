#!/usr/bin/env python3
"""Development recordings for Lockd In's built-in study audio (R48).

Everything here is synthesised from scratch by this script: no samples, no recordings, no
third-party material. The project owns the output. These are DEVELOPMENT recordings: good
enough to build and test the player, meant to be replaced by produced recordings before
release (see docs/audio-assets.md).

Output: Ogg Opus, 48 kHz stereo, written to apps/android/app/src/main/res/raw/.
Usage:  python3 design/audio/gen.py [out_dir]
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt, fftconvolve

SR = 48000
RNG = np.random.default_rng(20261102)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def reverb(x, seconds=2.6, wet=0.28, seed=1):
    """Stereo reverb from exponentially decaying noise (a simple, owned impulse response)."""
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    env = np.exp(-6.9 * np.arange(n) / n)
    sos = butter(2, 5000, "low", fs=SR, output="sos")
    irs = [sosfilt(sos, r.standard_normal(n)) * env for _ in range(2)]
    irs = [ir / np.sqrt(np.sum(ir ** 2)) for ir in irs]
    mono = x.mean(axis=1) if x.ndim == 2 else x
    tail = np.stack([fftconvolve(mono, ir)[: len(mono)] for ir in irs], axis=1)
    dry = x if x.ndim == 2 else np.stack([x, x], axis=1)
    return dry * (1 - wet) + tail * wet


def piano_note(m, dur, vel):
    f0 = midi_hz(m)
    n = int(SR * (dur + 2.5))
    t = np.arange(n) / SR
    out = np.zeros(n)
    B = 0.0004  # string inharmonicity
    for k in range(1, 14):
        fk = f0 * k * np.sqrt(1 + B * k * k)
        if fk > 15000:
            break
        amp = (1.0 / k ** 1.1) * (0.6 + 0.4 * vel)
        decay = 0.9 + 0.35 * k * (f0 / 261.0) ** 0.5
        for det in (-0.6, 0.6):  # two slightly detuned strings
            out += amp * np.exp(-decay * t) * np.sin(2 * np.pi * (fk + det * k * 0.15) * t + RNG.uniform(0, 6.28))
    # release: damper after the note's duration
    rel = np.ones(n)
    r0 = int(SR * dur)
    rel[r0:] = np.exp(-np.arange(n - r0) / (SR * 0.35))
    # soft hammer attack
    att = np.minimum(1.0, t / 0.004)
    hammer = np.zeros(n)
    hn = int(SR * 0.012)
    hammer[:hn] = RNG.standard_normal(hn) * np.exp(-np.arange(hn) / (SR * 0.003)) * 0.05 * vel
    return (out * rel * att + hammer) * vel * 0.18


def place(buf, sig, at, pan=0.0):
    i = int(at * SR)
    j = min(len(buf), i + len(sig))
    l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    buf[i:j, 0] += sig[: j - i] * l
    buf[i:j, 1] += sig[: j - i] * r


SCALE = [0, 2, 4, 5, 7, 9, 11]


def chord(root, quality):
    return [root, root + (3 if quality == "m" else 4), root + 7]


def piano_track(key, prog, bpm, bars, seed):
    r = np.random.default_rng(seed)
    beat = 60.0 / bpm
    total = bars * 4 * beat + 4
    buf = np.zeros((int(SR * total), 2))
    for b in range(bars):
        root, q = prog[b % len(prog)]
        c = chord(key + root, q)
        t0 = b * 4 * beat
        # left hand: bass + gentle arpeggio
        place(buf, piano_note(c[0] - 12, 4 * beat, 0.55), t0, -0.3)
        arp = [c[0], c[1], c[2], c[1] + 12 if r.random() < 0.5 else c[2]]
        for i, m in enumerate(arp):
            place(buf, piano_note(m, beat * 1.6, 0.32 + 0.08 * r.random()), t0 + i * beat, -0.1)
        # right hand: sparse melody from the scale, mostly chord tones
        for i in range(4):
            if r.random() < 0.55:
                deg = r.choice(SCALE + [c[1] - key, c[2] - key])
                m = key + 12 + int(deg) % 12 + 12 * (r.random() < 0.3)
                place(buf, piano_note(m, beat * (1 + r.integers(0, 2)), 0.42 + 0.1 * r.random()), t0 + i * beat + r.choice([0, beat / 2]), 0.25)
    return buf


def strings_voice(m, dur, vel, seed):
    r = np.random.default_rng(seed)
    f0 = midi_hz(m)
    n = int(SR * (dur + 2.0))
    t = np.arange(n) / SR
    out = np.zeros(n)
    for det in (-0.12, 0.0, 0.13):  # small ensemble
        vib = 1 + 0.004 * np.sin(2 * np.pi * (5.0 + r.uniform(-0.4, 0.4)) * t + r.uniform(0, 6.28)) * np.minimum(1, t / 1.2)
        phase = 2 * np.pi * f0 * (1 + det / 100) * np.cumsum(vib) / SR
        for k in range(1, 18):
            if f0 * k > 9000:
                break
            out += np.sin(k * phase) / k
    env = np.minimum(1.0, t / 1.4)
    r0 = int(SR * dur)
    env[r0:] *= np.exp(-np.arange(n - r0) / (SR * 0.9))
    sos = butter(2, 2800, "low", fs=SR, output="sos")
    return sosfilt(sos, out * env) * vel * 0.05


def strings_track(key, prog, bpm, bars, seed):
    r = np.random.default_rng(seed)
    beat = 60.0 / bpm
    total = bars * 4 * beat + 4
    buf = np.zeros((int(SR * total), 2))
    for b in range(bars):
        root, q = prog[b % len(prog)]
        c = chord(key + root, q)
        t0 = b * 4 * beat
        place(buf, strings_voice(c[0] - 12, 4 * beat, 0.8, seed + b), t0, -0.4)
        place(buf, strings_voice(c[1], 4 * beat, 0.55, seed + 100 + b), t0, -0.1)
        place(buf, strings_voice(c[2], 4 * beat, 0.55, seed + 200 + b), t0, 0.15)
        if b % 2 == 0:
            m = key + 12 + int(r.choice(SCALE))
            place(buf, strings_voice(m, 7 * beat, 0.5, seed + 300 + b), t0 + beat * r.choice([0, 1]), 0.35)
    return buf


def rain(seconds, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    out = np.zeros((n, 2))
    for ch in range(2):
        w = r.standard_normal(n)
        hiss = sosfilt(butter(2, [400, 7000], "band", fs=SR, output="sos"), w) * 0.18
        body = sosfilt(butter(2, 900, "low", fs=SR, output="sos"), r.standard_normal(n)) * 0.25
        swell = 0.85 + 0.15 * np.sin(2 * np.pi * np.arange(n) / SR / 23 + ch)
        out[:, ch] = (hiss + body) * swell
    # droplets
    for _ in range(int(seconds * 14)):
        at = r.integers(0, n - 4000)
        ln = r.integers(200, 1600)
        f = r.uniform(1800, 5200)
        tt = np.arange(ln) / SR
        d = np.sin(2 * np.pi * f * tt) * np.exp(-tt * r.uniform(60, 160)) * r.uniform(0.02, 0.09)
        ch = r.integers(0, 2)
        out[at:at + ln, ch] += d
        out[at:at + ln, 1 - ch] += d * 0.5
    return out


def loopable(x, fade_s=4.0):
    """Crossfade the end into the start so the file repeats without a seam."""
    f = int(SR * fade_s)
    a = np.linspace(0, 1, f)[:, None]
    head = x[:f] * a + x[-f:] * (1 - a)
    return np.concatenate([head, x[f:-f]])


def finish(x, peak=0.7, fade_in=1.5, fade_out=5.0, loop=False):
    if not loop:
        fi, fo = int(SR * fade_in), int(SR * fade_out)
        x[:fi] *= np.linspace(0, 1, fi)[:, None]
        x[-fo:] *= np.linspace(1, 0, fo)[:, None]
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def write(x, path, kbps=64):
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        wavfile.write(tmp.name, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libopus", "-b:a", f"{kbps}k", "-map_metadata", "-1", path], check=True)
    print(path, f"{os.path.getsize(path) // 1024} KB")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "apps/android/app/src/main/res/raw"
    os.makedirs(out, exist_ok=True)
    I, ii, iii, IV, V, vi = (0, ""), (2, "m"), (4, "m"), (5, ""), (7, ""), (9, "m")
    pianos = [
        ("piano_1", 60, [I, vi, IV, V], 66, 40),
        ("piano_2", 57, [vi, IV, I, V], 60, 36),
        ("piano_3", 62, [I, iii, vi, IV], 70, 42),
    ]
    for name, key, prog, bpm, bars in pianos:
        write(finish(reverb(piano_track(key, prog, bpm, bars, sum(map(ord, name))))), f"{out}/{name}.ogg")
    strings = [
        ("strings_1", 55, [I, vi, IV, V], 54, 30),
        ("strings_2", 60, [vi, IV, I, V], 50, 28),
        ("strings_3", 58, [I, IV, vi, V], 56, 30),
    ]
    for name, key, prog, bpm, bars in strings:
        write(finish(reverb(strings_track(key, prog, bpm, bars, sum(map(ord, name))), seconds=3.2, wet=0.35)), f"{out}/{name}.ogg")
    write(finish(loopable(rain(300, 7)), peak=0.6, loop=True), f"{out}/rain_1.ogg", kbps=56)


if __name__ == "__main__":
    main()
