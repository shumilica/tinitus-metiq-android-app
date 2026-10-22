<br />

<p align="center">
    <img src=".github/static/logo.svg" alt="Metiq logo" width="30%" />
</p>

<br />

<p align="center">
  No-nonsense noise app for focus, sleep, study, relaxation, and <strong>frontier clinical tinnitus calibration &amp; neuromodulation</strong>.
</p>

<br />

<p align="center">
    <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3">
    <img src="https://github.com/shumilica/tinitus-metiq-android-app/actions/workflows/ci.yml/badge.svg" alt="CI">
</p>

# Metiq — Tinnitus Frontier Edition

This is a fork of [Metiq (`metiq-xyz/android-app`)](https://github.com/metiq-xyz/android-app) that adds a **4th real-time sound generator category: Tinnitus**, combining a **5-Step Guided Psychoacoustic Calibration Wizard** with **7 real-time DSP & bimodal frontier therapies**, **automatic clinical session timers**, and a **Daily Therapy Schedule with Android Reminders & Snooze**—all 100% offline.

For full medical citations (2010–2026 clinical trials in *JAMA Network Open*, *Nature Communications*, *PNAS*, *PLOS ONE*, *Trends in Hearing*, and *Frontiers in Neuroscience*), see **[TINNITUS_FRONTIER_RESEARCH.md](TINNITUS_FRONTIER_RESEARCH.md)**.

---

## Tinnitus Category: Two-Tab Clinical Architecture

### Tab 1: `1. My Tinnitus Profile` — 5-Step Guided Calibration & Locked Profile

Clinical audiology research shows that self-directed sweeping on a raw frequency slider is prone to **auditory adaptation** (the brain temporarily blends the external test tone with the internal tinnitus) and **octave confusion** (~40% of patients match a harmonic 1 octave below or above their true tinnitus).

To solve this in plain language, **Tab 1** guides you through a **5-Step Psychoacoustic Calibration Wizard**:

1. **Step 1 — Ear Location & Sound Texture**:
   - Choose where your tinnitus is loudest (`Left Ear`, `Both / Center`, `Right Ear` + fine constant-power balance) and whether it sounds like a musical whistle/ring (`Pure Tone`) or steam/cicadas/static (`Narrowband Hiss` with adjustable band sharpness $Q \in [2, 24]$).
2. **Step 2 — Convergent A/B Pitch Bracketing (`Sound A vs. Sound B`)**:
   - Instead of sweeping a confusing dial, you compare two distinct tones: **Sound A (Current Winner ★)** vs. **Sound B (Challenger)**.
   - Each time you pick the closer sound, the **Winning Sound stays** and competes against a narrower challenger frequency from the remaining bracket until your choices converge to a **coherent, constant frequency result** (tracked live by a **Coherence Meter**).
3. **Step 3 — Pulsed Silent-Gap Verification (`0.6s ON / 0.4s OFF`)**:
   - *Why is Step 3 needed after Step 2?* Step 2 uses continuous tones for relative pitch bracketing, which triggers temporary **auditory adaptation**. Step 3 automatically switches your converged winning frequency into a **pulsed tone (`0.6s ON / 0.4s SILENT GAP`)**: you hear the app's tone during the ON pulse and your own raw internal tinnitus during the silent gap, allowing a true side-by-side verification and optional $\pm 1\text{ Hz} / \pm 5\text{ Hz}$ micro-lock.
4. **Step 4 — The "Octave Confusion" Challenge ($\div 2$ vs. $1\times$ vs. $\times 2$)**:
   - Presents three harmonically related cards—**Lower Octave ($\div 2$)**, **Converged Pitch ($1\times$)**, and **Higher Octave ($\times 2$)**—so you can verify you haven't locked onto a sub-harmonic or upper harmonic.
5. **Step 5 — Hearing Threshold ($T_{\text{hear}}$) & Minimum Masking Level ($\text{MML}$) Calibration**:
   - Calibrates two essential clinical loudness markers at your locked pitch:
     - **Hearing Threshold ($T_{\text{hear}}$)**: The volume where you can *barely* detect the app's sound (used to auto-calibrate **Stochastic Resonance**).
     - **Minimum Masking Level ($\text{MML}$)**: The volume where the sound *just* covers your internal tinnitus (used to auto-calibrate active suppression and neuromodulation therapies).

#### Why Your Tinnitus Profile Is Locked After Calibration (No Exposed Manual Slider)
Once you finish the 5 steps and tap **Save & Lock Profile**, the app locks your calibrated frequency, ear balance, $T_{\text{hear}}$, and $\text{MML}$ into a **Locked Clinical Profile Card**—without an exposed manual FM slider.
- **Clinical Rationale**: Targeted therapies such as **ISEC Mexican-Hat Notch** and **Coordinated Reset Neuromodulation** depend on placing a surgical filter or harmonic quartet around your exact tinnitus frequency ($f_T$). An exposed manual dial can easily be bumped or casually dragged while browsing, shifting the notch away from your hyperactive tinnitus neurons and invalidating the therapy.
- **Re-Calibrating & Tracking Progress**: Whenever you want to update your tinnitus sound—or check whether your tinnitus pitch and masking level have improved after weeks of therapy—tap **"Re-calibrate (5 Steps)"**. Each completed calibration is saved into your on-device **Clinical Progress Log (Pitch & MML)** so you can track reductions in your Minimum Masking Level over time.

---

### Tab 2: `2. Therapies & Schedule` — 7 Frontier Therapies + Daily Schedule & Snooze

Once your profile is locked, **Tab 2** unlocks 7 real-time DSP & bimodal therapies (auto-calibrated to your $T_{\text{hear}}$ or $\text{MML}$ and auto-armed with their clinical session durations):

1. **AM Residual Inhibition (`◎`) — $10\text{ Hz}$ Alpha / $40\text{ Hz}$ Gamma Pulse (`3 min` auto-timer)**:
   - Applies $100\%$ depth sinusoidal amplitude modulation ($10\text{ Hz}$ Alpha or $40\text{ Hz}$ Gamma) to your matched tone/hiss at $\text{MML}$ to trigger post-stimulus **Residual Inhibition** (temporary quiet window after the 3-minute session stops).
2. **Bimodal Haptic STDP (`⚡`) — Synchronized Audio + Micro-Haptic Pulses (`30 min` auto-timer)**:
   - Inspired by Dr. Susan Shore's *JAMA Network Open* (2023) and *Nature Communications* TENT-A3 (2024) trials. Pairs brief tinnitus-matched tone bursts with synchronized smartphone `Vibrator` micro-haptic pulses separated by an adjustable **STDP interval** ($\Delta t \in [5\text{ ms}, 20\text{ ms}]$, default $12\text{ ms}$). Hold the phone against your cheekbone/jaw, mastoid/neck, or fingertips during the 30-minute session.
3. **ISEC Notch Therapy (`⌖`) — "Mexican-Hat" Spectral Contrast Filter (`60 min` auto-timer)**:
   - Upgrades Tailor-Made Notched Sound Therapy (Stein, Okamoto & Pantev, *PLOS ONE*, 2015) with a **3-Stage Cascaded Biquad Filter** applied in real time to active **Noise** and **Ambient** layers (plus an internal pink-noise bed): a deep central Band-Stop Notch at $f_T$ ($>100\text{ dB}$ attenuation) flanked by two **Peaking EQ Boost filters** ($+6\text{ dB}$ at $\pm 1/3\text{ octave}$) to supercharge **lateral inhibition** in the auditory cortex.
4. **CR Neuromodulation (`∿`) — Acoustic Coordinated Reset + Anti-Habituation Drift (`60 min` auto-timer)**:
   - Cycles Hanning-windowed tone bursts across the four clinical CR harmonic ratios ($0.766 f_T, 0.900 f_T, 1.100 f_T, 1.305 f_T$) in a 3-cycles-ON / 2-cycles-OFF pattern (Tass et al., 2012), enhanced with **TENT-A2 Anti-Habituation Micro-Drift** ($\pm 3\%$ slow parameter drift so neurons never plateau).
5. **Stochastic Resonance (`◌`) — Sub-Threshold DCN Calmer (`Continuous / Sleep-safe`)**:
   - Generates a gentle octave-wide stochastic noise bed around $f_T$ automatically set to your **Hearing Threshold ($T_{\text{hear}}$)** (Krauss et al., 2016), satisfying the Dorsal Cochlear Nucleus's autocorrelation detector so the brainstem turns down its own internal neural gain.
6. **Phase Cancel (`−`) — $180^\circ$ Inverted Wave**:
   - Synthesizes a $180^\circ$ phase-inverted waveform ($-\sin(\phi)$) with a $0.25\text{ Hz}$ antiphase stereo opposition sweep at $\text{MML}$.
7. **Matched Masking (`≈`) — As-Is Pitch Blend**:
   - Blends your exact matched pitch and texture directly into any Noise or Ambient mix at $\text{MML}$.

#### Proposed Daily Therapy Schedule, Reminders & Snooze
Clinical trials emphasize **consistent daily dosing** rather than sporadic listening. Tab 2 includes a **Proposed Daily Schedule & Reminders** system:
- **3 Default Clinical Slots** (fully customizable in time, therapy mode, and session duration):
  - **09:00 (Morning)**: `Bimodal Haptic STDP • 30 min`
  - **15:00 (Afternoon)**: `ISEC Notch Therapy • 60 min`
  - **21:30 (Evening / Wind-down)**: `Stochastic Resonance • 30 min`
- **Android Notifications & Snooze (`15m` / `1h`)**:
  - Enable the **Daily Schedule Reminders** toggle to schedule offline daily alarms via Android's `AlarmManager` (survives device reboots).
  - Each reminder notification includes three interactive buttons:
    - **`Open & Start`**: Launches Metiq, activates the scheduled therapy at your calibrated volume, and automatically arms the session countdown timer.
    - **`Snooze 15m`** / **`Snooze 1h`**: Dismisses the notification and reschedules the reminder for 15 minutes or 1 hour later.

---

## Core Metiq Features

- **Colored noise playback** — pre-rendered, seamless loops (Pink, Brown, White, Grey) at -22 LUFS so every color sounds equally loud, with an adjustable Warmth low-pass filter.
- **Ambient sounds and mixes** — sea waves, rain, fire crackling, birds chirping, café ambience, wind, crickets, and stream. Layer them at independent levels, start from a preset, or save your own custom mixes.
- **Binaural beats** — layer brainwave tones (Delta, Theta, Alpha, Beta, Gamma) over any noise, ambient, or tinnitus layer. Headphones needed.
- **Sleep & clinical session timer** — set hours, minutes, and seconds, or let Tinnitus therapies auto-arm their clinical session duration. Audio fades out gracefully when the timer expires.
- **Stays out of your way** — no accounts, no cloud, no tracking, no ads. The app runs 100% offline.

## Getting the APK

Every push to `main` automatically builds debug APKs (`app-fdroid-debug` and `app-play-debug`) via **[GitHub Actions](https://github.com/shumilica/tinitus-metiq-android-app/actions)**. Open the latest workflow run and download the APK from the **Artifacts** section at the bottom of the run page.

## License

Metiq is licensed under the [GNU General Public License v3.0 or later](LICENSE).
See `LICENSE` for the full text.
