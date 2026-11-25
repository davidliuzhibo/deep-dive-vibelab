"""深海潜航 v0.6.0 cabin sounds, synthesized (no downloads): a deep-sea ambience loop, a whale call, a hull creak,
a burst of bubbles. Writes 16-bit mono WAVs at 32 kHz into app/app/src/main/assets/audio/.
Run: python tools/make_sfx_060.py <out dir>   (needs numpy)."""
import sys, wave
import numpy as np

SR = 32000
out = sys.argv[1]
rng = np.random.default_rng(60)


def save(name, x, peak=0.85):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())
    print(name, round(len(x) / SR, 2), "s")


def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.zeros_like(x); s = 0.0
    for i, v in enumerate(x):
        s = (1 - a) * v + a * s; y[i] = s
    return y


def bandpass(x, f0, q):
    # RBJ biquad band-pass.
    w0 = 2 * np.pi * f0 / SR; al = np.sin(w0) / (2 * q)
    b0, b1, b2 = al, 0.0, -al; a0, a1, a2 = 1 + al, -2 * np.cos(w0), 1 - al
    y = np.zeros_like(x); x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        o = (b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, v, y1, o; y[i] = o
    return y


def reverb(x, tail=1.5):
    n = len(x) + int(tail * SR)
    y = np.zeros(n); y[:len(x)] = x
    for d, g in ((0.031, 0.5), (0.047, 0.45), (0.071, 0.4), (0.113, 0.35)):
        k = int(d * SR)
        for i in range(k, n):
            y[i] += g * y[i - k] * 0.6
    return y


# 1. Ambience, 12 s seamless: a low rumble of water (brown noise under 140 Hz), a slow 41 Hz pressure hum that beats
#    against 43.5 Hz, and a faint far-off hiss. Looped by crossfading the last second into the first.
n = 13 * SR
t = np.arange(n) / SR
brown = np.cumsum(rng.standard_normal(n)); brown -= np.convolve(brown, np.ones(4000) / 4000, "same")
rumble = lowpass(lowpass(brown, 140), 140)
rumble /= np.max(np.abs(rumble))
hum = 0.35 * np.sin(2 * np.pi * 41 * t) + 0.25 * np.sin(2 * np.pi * 43.5 * t) + 0.08 * np.sin(2 * np.pi * 82 * t)
hiss = lowpass(rng.standard_normal(n), 900) * 0.05
amb = rumble * (0.8 + 0.2 * np.sin(2 * np.pi * t / 6.5)) + hum * 0.6 + hiss
L = 12 * SR; f = SR
loop = amb[:L].copy()
fade = np.linspace(0, 1, f)
loop[:f] = amb[L:L + f] * (1 - fade) + amb[:f] * fade
save("deep_amb", loop, 0.7)

# 2. Whale call, ~5 s plus tail: a long moan gliding 150 -> 380 -> 230 Hz with harmonics, slow vibrato, swelling in and out.
d = 4.2; n = int(d * SR); t = np.arange(n) / SR
f0 = 150 + 230 * np.sin(np.pi * np.clip(t / 2.6, 0, 1)) ** 1.5
f0 = np.where(t > 2.6, 380 - 150 * (t - 2.6) / (d - 2.6), f0)
f0 *= 1 + 0.012 * np.sin(2 * np.pi * 5.2 * t)
ph = 2 * np.pi * np.cumsum(f0) / SR
voice = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.22 * np.sin(3 * ph) + 0.1 * np.sin(4 * ph)
env = np.sin(np.pi * t / d) ** 0.7
whale = lowpass(voice * env, 1800)
save("whale", reverb(whale, 1.6), 0.8)

# 3. Hull creak, 1.4 s: stick-slip clicks speeding up then slowing, rung through two hull resonances.
d = 1.4; n = int(d * SR); t = np.arange(n) / SR
rate = 25 + 55 * np.sin(np.pi * t / d)
ph = np.cumsum(rate) / SR
clicks = np.zeros(n); idx = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
clicks[idx] = 1.0 * (0.6 + 0.4 * rng.random(len(idx)))
creak = bandpass(clicks, 380, 6) + 0.6 * bandpass(clicks, 910, 9) + 0.3 * bandpass(clicks, 150, 3)
creak *= np.sin(np.pi * t / d) ** 0.5
save("creak", reverb(creak, 0.6), 0.75)

# 4. Bubbles, 1.6 s: a dozen bubbles, each a short rising sine chirp.
d = 1.6; n = int(d * SR); bub = np.zeros(n)
for k in range(14):
    st = int(rng.random() * (d - 0.15) * SR); ln = int((0.03 + 0.05 * rng.random()) * SR)
    tt = np.arange(ln) / SR; f = (500 + 900 * rng.random()) * (1 + 6 * tt)
    bub[st:st + ln] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 45) * (0.4 + 0.6 * rng.random())
save("bubbles", bub, 0.6)
