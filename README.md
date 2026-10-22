<br />

<p align="center">
    <img src=".github/static/logo.svg" alt="Metiq logo" width="30%" />
</p>

<br />

<p align="center">
  No-nonsense noise app for focus, sleep, study, relaxation, and <strong>tinnitus relief &amp; cancellation</strong>.
</p>

<br />

<p align="center">
    <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3">
    <img src="https://github.com/shumilica/tinitus-metiq-android-app/actions/workflows/ci.yml/badge.svg" alt="CI">
</p>

# Metiq — Tinnitus Edition

This is a fork of [Metiq (`metiq-xyz/android-app`)](https://github.com/metiq-xyz/android-app) that adds a **4th real-time sound generator category: Tinnitus Matcher & Canceller**, alongside Colored Noise, Ambient Sounds, and Binaural Beats.

---

## Tinnitus Matcher & Canceller (4th Generator)

Tinnitus varies widely from person to person in pitch, texture, and ear lateralization. The **Tinnitus** generator provides an interactive **FM-Radio Pitch Matcher** paired with four real-time digital signal processing (DSP) relief and cancellation modes inside `AudioEngine`:

### 1. FM Pitch Matcher & Sound Shaping
- **Logarithmic FM-Radio Frequency Dial (`100 Hz – 16,000 Hz`)**: Sweep smoothly across human pitch perception to locate the exact frequency where the external sound matches your internal tinnitus.
- **Tap-to-Edit Exact Frequency**: Tap the digital Hz/kHz readout to type an exact integer frequency (`100`–`16000 Hz`).
- **Fine-Tuning & Anti-Octave-Confusion Controls**:
  - Step by **`−10 Hz`**, **`−1 Hz`**, **`+1 Hz`**, or **`+10 Hz`** to lock onto your exact resonant frequency.
  - Check **`÷2 Oct`** and **`×2 Oct`** to prevent *octave confusion* (matching one octave below or above your actual tinnitus pitch).
- **Live Test Tone (`Test tone`)**: Audition your frequency live while sweeping the FM dial before adding a relief mode to your mix.
- **Sound Character (`Pure tone` vs. `Narrowband hiss`) & Band Sharpness**: Match tonal ringing with a phase-continuous sine wave or match hissing/sizzling tinnitus with a real-time 2nd-order Biquad Band-Pass filter with adjustable sharpness ($Q \in [2, 24]$).
- **Left / Right Ear Balance**: Constant-power stereo panning (`L 100%` $\leftrightarrow$ `Both ears` $\leftrightarrow$ `R 100%`) to target unilateral or asymmetric tinnitus without fatiguing the unaffected ear.

### 2. Four Real-Time Relief & Cancellation Modes
Once you match your tinnitus pitch, you can add it to your mix using any of four modes:
1. **Matched Masking (`≈`) — As-Is Pitch Blend**: Plays your matched frequency and waveform directly in the mix to blend with your tinnitus and trigger *residual inhibition*.
2. **Phase Cancel (`−`) — 180° Inverted Wave**: Synthesizes a $180^\circ$ phase-inverted waveform ($-\sin(\phi)$) with a gentle $0.25\text{ Hz}$ antiphase stereo opposition sweep.
3. **Notch Therapy (`⌖`) — Tailor-Made Notched Sound Therapy**: Carves a real-time 2nd-order Biquad Band-Stop (Notch) Filter centered at your exact tinnitus frequency directly out of active **Noise** and **Ambient** layers (plus a soft notched pink-noise companion bed), using lateral inhibition in the auditory cortex to suppress hyperactive tinnitus neurons.
4. **Neuromodulation (`∿`) — Acoustic Coordinated Reset (CR) Tones**: Plays gentle, Hanning-windowed tone bursts cycling randomly across the four clinical CR harmonic ratios ($0.766 f_T, 0.900 f_T, 1.100 f_T, 1.305 f_T$) to desynchronize hyperactive neural clusters.

All tuned tinnitus parameters (frequency, sound character, sharpness, ear balance, volume, and active relief mode) are automatically persisted on-device and can be saved into one-tap **Quick Mixes** on the Home screen.

---

## Core Metiq Features

- **Colored noise playback** — pre-rendered, seamless loops (Pink, Brown, White, Grey) at -22 LUFS so every color sounds equally loud, with an adjustable Warmth low-pass filter.
- **Ambient sounds and mixes** — sea waves, rain, fire crackling, birds chirping, café ambience, wind, crickets, and stream. Layer them at independent levels, start from a preset, or save your own custom mixes.
- **Binaural beats** — layer brainwave tones (Delta, Theta, Alpha, Beta, Gamma) over any noise, ambient, or tinnitus layer. Headphones needed.
- **Sleep timer** — set hours, minutes, and seconds, or pick from your saved presets. Audio fades out gracefully when the timer expires.
- **Stays out of your way** — no accounts, no cloud, no tracking, no ads. The app runs 100% offline.
- **Light on your battery** — a lean, native audio pipeline built on Android's `AudioTrack` at 48 kHz, with a small memory and CPU footprint.
- **Familiar media controls** — lock screen, notification, and Bluetooth headset transport buttons all work the way you'd expect.

## Getting the APK

Every push to `main` automatically builds debug APKs (`app-fdroid-debug` and `app-play-debug`) via **[GitHub Actions](https://github.com/shumilica/tinitus-metiq-android-app/actions)**. Open the latest workflow run and download the APK from the **Artifacts** section at the bottom of the run page.

Metiq targets Android 9 (API 28) and newer.

## Contributing & Upstream Credits

- Upstream project: **[metiq-xyz/android-app](https://github.com/metiq-xyz/android-app)**
- Architecture and development setup: see **[CONTRIBUTING.md](CONTRIBUTING.md)**.
- Built with Jetpack Compose, Material 3, AndroidX Media3, the Kotlin standard library, and the Inter typeface by Rasmus Andersson.

## License

Metiq is licensed under the [GNU General Public License v3.0 or later](LICENSE).
See `LICENSE` for the full text.
