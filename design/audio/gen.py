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


def quiet_piano_track(key, prog, bpm, bars, seed):
    """Sparse, soft piano: one low note and a slow broken chord per bar, no melody runs."""
    r = np.random.default_rng(seed)
    beat = 60.0 / bpm
    buf = np.zeros((int(SR * (bars * 4 * beat + 5)), 2))
    for b in range(bars):
        root, q = prog[b % len(prog)]
        c = chord(key + root, q)
        t0 = b * 4 * beat
        place(buf, piano_note(c[0] - 12, 4 * beat, 0.35), t0, -0.2)
        for i, m in enumerate([c[1], c[2], c[1] + 12][: 2 + (b % 2)]):
            place(buf, piano_note(m, 2.5 * beat, 0.22 + 0.05 * r.random()), t0 + (i + 1) * beat * 1.2, 0.15)
    return buf


def drum(kind, vel, seed):
    r = np.random.default_rng(seed)
    if kind == "kick":
        n = int(SR * 0.35); t = np.arange(n) / SR
        f = 55 + 70 * np.exp(-t * 30)
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9) * vel
    if kind == "snare":
        n = int(SR * 0.25); t = np.arange(n) / SR
        noise = sosfilt(butter(2, [1200, 6000], "band", fs=SR, output="sos"), r.standard_normal(n))
        return (noise * 0.6 + np.sin(2 * np.pi * 190 * t) * 0.3) * np.exp(-t * 18) * vel
    n = int(SR * 0.06); t = np.arange(n) / SR
    return sosfilt(butter(2, 7000, "high", fs=SR, output="sos"), r.standard_normal(n)) * np.exp(-t * 70) * vel * 0.5


def lofi_track(key, prog, bpm, bars, seed):
    """Soft chords, a lazy swung beat and a little tape hiss. Muffled on purpose."""
    r = np.random.default_rng(seed)
    beat = 60.0 / bpm
    total = bars * 4 * beat + 4
    buf = np.zeros((int(SR * total), 2))
    for b in range(bars):
        root, q = prog[b % len(prog)]
        c = chord(key + root, q) + [key + root + (10 if q == "m" else 11)]
        t0 = b * 4 * beat
        for i, m in enumerate(c):
            place(buf, piano_note(m, 3.6 * beat, 0.3), t0 + i * 0.03, -0.15 + 0.1 * i)
        place(buf, piano_note(c[0] - 12, 4 * beat, 0.4), t0, 0)
        if b >= 2:
            for k in range(4):
                sw = beat * (0.08 if k % 2 else 0)
                if k in (0, 2): place(buf, drum("kick", 0.55, seed + b * 8 + k), t0 + k * beat, 0)
                if k in (1, 3): place(buf, drum("snare", 0.22, seed + b * 8 + k), t0 + k * beat, 0.05)
                for h in range(2):
                    place(buf, drum("hat", 0.25 + 0.1 * r.random(), seed + b * 16 + k * 2 + h), t0 + k * beat + h * beat / 2 + sw * h, 0.25)
    lp = butter(2, 3200, "low", fs=SR, output="sos")
    buf = np.stack([sosfilt(lp, buf[:, 0]), sosfilt(lp, buf[:, 1])], axis=1)
    hiss = sosfilt(butter(2, [2000, 9000], "band", fs=SR, output="sos"), r.standard_normal(len(buf))) * 0.004
    return buf + hiss[:, None]


def pad_track(key, prog, seconds_per_chord, chords, seed):
    """Slow evolving pads: no rhythm, no melody."""
    buf = np.zeros((int(SR * (chords * seconds_per_chord + 8)), 2))
    for i in range(chords):
        root, q = prog[i % len(prog)]
        c = chord(key + root, q)
        for j, m in enumerate([c[0] - 12, c[1], c[2], c[0] + 12]):
            v = strings_voice(m, seconds_per_chord + 2, 0.35, seed + i * 10 + j)
            place(buf, v, i * seconds_per_chord, -0.5 + j / 3)
    return buf


def ocean(seconds, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for ch in range(2):
        w = sosfilt(butter(2, [150, 2500], "band", fs=SR, output="sos"), r.standard_normal(n))
        swell = 0.25 + 0.75 * (0.5 + 0.5 * np.sin(2 * np.pi * t / (9.0 + ch * 0.7) + ch)) ** 3
        out[:, ch] = w * swell * 0.35
    return out


def forest(seconds, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for ch in range(2):
        wind = sosfilt(butter(2, [200, 1200], "band", fs=SR, output="sos"), r.standard_normal(n))
        out[:, ch] = wind * (0.12 + 0.06 * np.sin(2 * np.pi * t / 17 + ch))
    for _ in range(int(seconds / 4)):  # a few soft, distant chirps
        at = r.integers(0, n - SR)
        ln = int(SR * r.uniform(0.08, 0.2))
        tt = np.arange(ln) / SR
        f = r.uniform(2500, 4200) + 600 * np.sin(2 * np.pi * r.uniform(8, 20) * tt)
        c = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / tt[-1]) * r.uniform(0.01, 0.03)
        for k in range(r.integers(1, 4)):
            s0 = at + int(k * SR * 0.22)
            if s0 + ln < n:
                ch = r.integers(0, 2)
                out[s0:s0 + ln, ch] += c
                out[s0:s0 + ln, 1 - ch] += c * 0.4
    return out


def brown(seconds, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    out = np.zeros((n, 2))
    for ch in range(2):
        b = np.cumsum(r.standard_normal(n))
        b = sosfilt(butter(1, 20, "high", fs=SR, output="sos"), b)
        out[:, ch] = sosfilt(butter(2, 900, "low", fs=SR, output="sos"), b)
    return out


def new_collections(out):
    I, ii, iii, IV, V, vi = (0, ""), (2, "m"), (4, "m"), (5, ""), (7, ""), (9, "m")
    for name, key, prog, bpm, bars in [("quietpiano_1", 60, [I, IV, vi, V], 52, 26), ("quietpiano_2", 57, [vi, IV, I, V], 50, 24)]:
        write(finish(reverb(quiet_piano_track(key, prog, bpm, bars, sum(map(ord, name))), seconds=3.4, wet=0.38), peak=0.5), f"{out}/{name}.ogg", kbps=56)
    for name, key, prog, bpm, bars in [("lofi_1", 62, [ii, V, I, vi], 76, 44), ("lofi_2", 57, [IV, iii, vi, V], 72, 42)]:
        write(finish(reverb(lofi_track(key, prog, bpm, bars, sum(map(ord, name))), seconds=1.8, wet=0.2), peak=0.6), f"{out}/{name}.ogg", kbps=56)
    for name, key, prog in [("ambient_1", 50, [I, vi, IV, V]), ("ambient_2", 53, [vi, IV, I, iii])]:
        write(finish(reverb(pad_track(key, prog, 14, 10, sum(map(ord, name))), seconds=4.5, wet=0.45), peak=0.5, fade_in=4), f"{out}/{name}.ogg", kbps=48)
    write(finish(loopable(ocean(240, 11), fade_s=6), peak=0.55, loop=True), f"{out}/ocean_1.ogg", kbps=48)
    write(finish(loopable(forest(240, 13), fade_s=6), peak=0.5, loop=True), f"{out}/forest_1.ogg", kbps=48)
    write(finish(loopable(brown(180, 17), fade_s=6), peak=0.5, loop=True), f"{out}/brown_1.ogg", kbps=40)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else "apps/android/app/src/main/res/raw"
    os.makedirs(out, exist_ok=True)
    new_collections(out)
    if "--new-only" in sys.argv:
        return
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
