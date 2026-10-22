# Frontier Tinnitus Medical Research & Clinical Architecture in Metiq

This document synthesizes peer-reviewed neuroscience and pivotal clinical trials on subjective tinnitus (spanning foundational discoveries through **2024–2026 landmark trials**) and documents how **Metiq — Tinnitus Frontier Edition** implements these medical findings in its **5-Step Guided Psychoacoustic Calibration Wizard**, **Locked Clinical Profile**, **7 Real-Time Therapies**, and **Daily Clinical Schedule & Snooze System**.

---

## Part 1: Landmark Medical Research & Clinical Trials (With Direct Citations)

### 1. Targeted Bimodal Auditory-Somatosensory Neuromodulation & Spike-Timing-Dependent Plasticity (STDP)
- **Dr. Susan Shore's Pivotal Trial (*JAMA Network Open*, 2023):**
  - **Paper:** Jones, G. R., Martel, D. T., Riffle, T. L., Errickson, J., Souter, J. R., Basura, G. J., Stucken, E., Schacht, J., & Shore, S. E. (2023). *Reversing Synchronized Brain Circuits Using Targeted Auditory-Somatosensory Stimulation to Treat Phantom Percepts: A Randomized Clinical Trial*. **JAMA Network Open**, 6(6), e2315914.
  - **Direct Links:** [DOI: 10.1001/jamanetworkopen.2023.15914](https://doi.org/10.1001/jamanetworkopen.2023.15914) | [PubMed: 37266939](https://pubmed.ncbi.nlm.nih.gov/37266939/)
  - **Key Finding & Dosing Protocol:** Subjective and somatic tinnitus originate from hyperactive, hypersynchronized **fusiform cells in the Dorsal Cochlear Nucleus (DCN)** of the brainstem. Crucially, DCN fusiform cells are **multisensory**: they integrate auditory nerve inputs with somatosensory (touch, vibration, proprioception) inputs from the head, jaw, neck, and skin. By pairing tinnitus-matched sound bursts with somatosensory pulses separated by a strict millisecond interval ($\Delta t \approx 5\text{ ms}$ to $20\text{ ms}$, typically $12\text{ ms}$) for **30 minutes once daily**, Shore's team induced **Long-Term Depression (LTD)** via **Spike-Timing-Dependent Plasticity (STDP)**—physically weakening the hyperactive synapses and producing clinically and statistically significant reductions in tinnitus loudness and Tinnitus Functional Index (TFI) scores.

### 2. Multi-Site Bimodal Neuromodulation & Anti-Habituation Parameter Switching (TENT-A2 & TENT-A3 Trials)
- **TENT-A3 Pivotal Trial (*Nature Communications*, 2024) & TENT-A2 Trial (*Scientific Reports*, 2022):**
  - **Paper (TENT-A3):** Boedts, M., Buechner, A., Khoo, S. G., Gjini, K., et al. (2024). *Combining sound with tongue stimulation for the treatment of tinnitus: a multi-site single-arm controlled pivotal trial*. **Nature Communications**, 15, 6806.
  - **Direct Links:** [DOI: 10.1038/s41467-024-50473-z](https://doi.org/10.1038/s41467-024-50473-z) | [PubMed: 39160146](https://pubmed.ncbi.nlm.nih.gov/39160146/)
  - **Paper (TENT-A2):** Conlon, B., Hamilton, C., Meade, E., Leong, S. L., O'Connor, C., Langguth, B., Vanneste, S., Hall, D. A., Hughes, S., & Lim, H. H. (2022). *Different bimodal neuromodulation settings reduce tinnitus symptoms in a large randomized trial*. **Scientific Reports**, 12, 10845.
  - **Direct Links:** [DOI: 10.1038/s41598-022-13875-x](https://doi.org/10.1038/s41598-022-13875-x) | [PubMed: 35773272](https://pubmed.ncbi.nlm.nih.gov/35773272/)
  - **Key Finding:**
    1. In **TENT-A3**, bimodal stimulation was proven clinically superior to sound-only therapy in patients with moderate-to-severe tinnitus.
    2. In **TENT-A2**, researchers discovered that **changing stimulation parameters over time** (slowly drifting tone burst timing and frequency offsets by $\pm 3\%$) prevented **neural habituation** and drove continued neuroplastic symptom reduction long after static sound therapy would have plateaued.

### 3. Increased Spectral Energy Contrast in Tailor-Made Notched Sound Therapy (ISEC-TMNM)
- **Foundational & Enhanced Lateral Inhibition Studies (*PNAS*, 2010 & *PLOS ONE*, 2015):**
  - **Paper 1:** Okamoto, H., Stracke, H., Stoll, W., & Pantev, C. (2010). *Listening to tailor-made notched music reduces tinnitus loudness and tinnitus-related auditory cortex activity*. **Proceedings of the National Academy of Sciences (PNAS)**, 107(3), 1207–1210.
    - **Direct Links:** [DOI: 10.1073/pnas.0911268107](https://doi.org/10.1073/pnas.0911268107) | [PubMed: 20080545](https://pubmed.ncbi.nlm.nih.gov/20080545/)
  - **Paper 2:** Stein, A., Engell, A., Lau, P., Wunderlich, R., Junghoefer, M., Wollbrink, A., Bruchmann, M., Rudack, C., & Pantev, C. (2015). *Enhancing Inhibition-Induced Plasticity in Tinnitus – Spectral Energy Contrasts in Tailor-Made Notched Music Matter*. **PLOS ONE**, 10(5), e0126494.
    - **Direct Links:** [DOI: 10.1371/journal.pone.0126494](https://doi.org/10.1371/journal.pone.0126494) | [PubMed: 25951609](https://pubmed.ncbi.nlm.nih.gov/25951609/)
  - **Key Finding & Dosing Protocol:** Removing acoustic energy at the patient's tinnitus frequency ($f_T$) for **60–90 minutes daily** suppresses hyperactive auditory cortex neurons via **lateral inhibition** from neighboring frequency bands. Stein, Okamoto & Pantev demonstrated via Magnetoencephalography (MEG) that **amplifying the spectral edges ("shoulders") immediately adjacent to the notch** (creating a "Mexican-hat" spectral profile) significantly strengthens inhibition-induced cortical plasticity compared to a simple flat notch.

### 4. 10 Hz & 40 Hz Amplitude-Modulated (AM) Tones for Residual Inhibition (RI)
- **University of Regensburg & University of Zurich Residual Inhibition Trials (*Frontiers in Aging Neuroscience*, 2017 & *Trends in Hearing*, 2019):**
  - **Paper 1:** Neff, P., Michels, J., Meyer, M., Schecklmann, M., Langguth, B., & Schlee, W. (2017). *10 Hz Amplitude Modulated Sounds Induce Short-Term Tinnitus Suppression*. **Frontiers in Aging Neuroscience**, 9, 130.
    - **Direct Links:** [DOI: 10.3389/fnagi.2017.00130](https://doi.org/10.3389/fnagi.2017.00130) | [PubMed: 28579955](https://pubmed.ncbi.nlm.nih.gov/28579955/)
  - **Paper 2:** Neff, P., Zielonka, L., Meyer, M., Langguth, B., Schecklmann, M., & Schlee, W. (2019). *Comparison of Amplitude Modulated Sounds and Pure Tones at the Tinnitus Frequency: Residual Tinnitus Suppression and Stimulus Evaluation*. **Trends in Hearing**, 23, 2331216519833841.
    - **Direct Links:** [DOI: 10.1177/2331216519833841](https://doi.org/10.1177/2331216519833841) | [PubMed: 30871419](https://pubmed.ncbi.nlm.nih.gov/30871419/)
  - **Key Finding & Dosing Protocol:** **Sinusoidal Amplitude-Modulated (SAM) tones** centered at the matched tinnitus frequency ($f_T$) and modulated at **$10\text{ Hz}$ (Alpha band)** or **$40\text{ Hz}$ (Gamma band)** for short **1–3 minute bursts** produce **stronger and longer-lasting Residual Inhibition** (temporary silence/suppression of tinnitus *after* the stimulus stops) than unmodulated pure tones.

### 5. Acoustic Coordinated Reset (CR) Neuromodulation
- **Desynchronizing Pathological Auditory Synchrony (*Restorative Neurology and Neuroscience*, 2012):**
  - **Paper:** Tass, P. A., Adamchic, I., Freund, H. J., von Stackelberg, T., & Hauptmann, C. (2012). *Counteracting tinnitus by acoustic coordinated reset neuromodulation*. **Restorative Neurology and Neuroscience**, 30(2), 137–159.
  - **Direct Links:** [DOI: 10.3233/RNN-2012-110218](https://doi.org/10.3233/RNN-2012-110218) | [PubMed: 22414611](https://pubmed.ncbi.nlm.nih.gov/22414611/)
  - **Key Finding & Dosing Protocol:** Delivering short, randomized sequences of four tones spaced around $f_T$ ($0.766 f_T, 0.900 f_T, 1.100 f_T, 1.305 f_T$) in a 3-cycles-ON / 2-cycles-OFF pattern for **45–60 minutes daily** disrupts pathological neuronal synchrony in the primary auditory cortex, causing the network to "unlearn" the hyper-synchronized tinnitus state and progressively lowering the patient's Minimum Masking Level (MML).

### 6. Stochastic Resonance & Central Gain Down-Regulation
- **University of Erlangen-Nuremberg Computational & Clinical Model (*Frontiers in Neuroscience*, 2016):**
  - **Paper:** Krauss, P., Tziridis, K., Metzner, C., Schilling, A., Hoppe, U., & Schulze, H. (2016). *Stochastic Resonance Controlled Upregulation of Internal Noise after Hearing Loss as a Putative Cause of Tinnitus-Related Neuronal Hyperactivity*. **Frontiers in Neuroscience**, 10, 597.
  - **Direct Links:** [DOI: 10.3389/fnins.2016.00597](https://doi.org/10.3389/fnins.2016.00597) | [PubMed: 28082855](https://pubmed.ncbi.nlm.nih.gov/28082855/)
  - **Key Finding & Dosing Protocol:** When subtle high-frequency cochlear synaptopathy reduces auditory nerve input, the brainstem injects **internal neural noise** via **Stochastic Resonance (SR)** to lift weak signals above threshold—and that excess internal noise is perceived as tinnitus. Providing external, **near-threshold band-limited stochastic noise** around $f_T$ (at $T_{\text{hear}}$, safe for **sleep or continuous background use**) satisfies the DCN's autocorrelation detector, allowing the brain to turn **down** its internal neural noise.

### 7. Clinical Psychoacoustic Tinnitus Pitch & Loudness Matching Protocols
- **Two-Alternative Forced-Choice (2AFC) Bracketing & Octave-Confusion Prevention (*American Journal of Audiology* & *Prog. Brain Res.*):**
  - **Paper:** Henry, J. A., McMillan, G. P., Manning, C., Letowski, T., & Gist, I. D. (2013). *itud/Automated Clinical Tinnitus Pitch-Matching Methods*. **Journal of the American Academy of Audiology** / **American Journal of Audiology**.
  - **Key Finding:** Giving an untrained patient a continuous frequency dial leads to two major errors:
    1. **Auditory Adaptation & Tone Pulling**: Listening to a continuous sweeping tone for more than 2–3 seconds causes auditory cortex neurons to adapt, making the external tone seem to "blend" with the tinnitus even when the pitch is off by $1000\text{ Hz}$.
    2. **Octave Confusion**: Nearly $40\%$ of patients match a harmonic 1 octave below ($\frac{1}{2} f_T$) or 1 octave above ($2 f_T$) their actual tinnitus pitch.
  - Clinical audiologists overcome this by combining **Convergent Two-Alternative Forced-Choice (A/B) Bracketing**, **Pulsed Silent-Gap Comparison**, and an explicit **Octave Confusion Challenge**.

---

## Part 2: How Metiq Implements the 5-Step Calibration & Locked Profile Architecture

```mermaid
flowchart TD
    subgraph Tab1["Tab 1: 1. My Tinnitus Profile"]
        S1["Step 1: Ear Location & Sound Texture\n(Left/Both/Right + Pure Tone vs. Narrowband Hiss)"]
        S2["Step 2: Convergent A/B Pitch Bracketing\n(Winning Sound ★ vs. Narrowing Challenger until Coherent)"]
        S3["Step 3: Pulsed Silent-Gap Verification\n(0.6s ON / 0.4s OFF to prevent auditory adaptation)"]
        S4["Step 4: Octave Confusion Challenge\n(Compare ÷2 Octave vs. 1× Match vs. ×2 Octave)"]
        S5["Step 5: Loudness Calibration\n(Hearing Threshold T_hear & Minimum Masking Level MML)"]
        Lock["Locked Clinical Profile Card + Offline History Log\n(Protected from accidental slider changes)"]
        S1 --> S2 --> S3 --> S4 --> S5 --> Lock
    end
```

### Clinical FAQ: Design Decisions in Tab 1

#### 1. Why does Step 2 (Convergent A/B Bracketing) repeat with the Winning Sound?
In psychoacoustics, human ears are poor at absolute frequency estimation ("Is my tinnitus $5,800\text{ Hz}$?"), but extraordinarily accurate at **relative two-tone comparison** ("Is Sound A or Sound B closer to my ringing?").
- In Step 2, whichever tone you select (`Sound A` or `Sound B`) becomes the **Winning Sound (Champion ★)** and remains in the Sound A slot.
- In each subsequent round, the bracket narrows geometrically ($\sqrt{f_1 \cdot f_2}$) and pits your **Winning Sound** against a closer **Challenger Sound** until the bracket span converges to a coherent, constant frequency match.

#### 2. Why is Step 3 (Pulsed Silent-Gap Verification) necessary right after Step 2?
Step 2 and Step 3 solve two completely different neuro-audiological problems:
- **Step 2 (Convergent A/B Bracketing)** narrows down the frequency across a $100\text{–}16,000\text{ Hz}$ spectrum by comparing two external tones against each other. However, listening to steady external tones triggers **short-term auditory adaptation** (your brain begins blending the test tone with your internal tinnitus).
- **Step 3 (Pulsed Silent-Gap Verification)** switches your winning pitch into a **pulsed rhythm (`0.6s ON / 0.4s SILENT GAP`)**. During the $0.6\text{ s}$ pulse you hear the app's tone; during the $0.4\text{ s}$ silent gap your auditory nerve recovers from adaptation and you hear your **raw internal tinnitus**. This side-by-side alternating pulse lets you verify that the pitch in the pulse and the pitch in the silent gap are identical (with optional $\pm 1\text{ Hz} / \pm 5\text{ Hz}$ micro-nudges).

#### 3. Why is there no exposed manual FM tuner after completing the 5-step calibration?
Once you complete the 5-step guided calibration, **Metiq locks your profile** and hides the raw frequency dial:
- **Protecting Surgical Therapies**: Therapies like **ISEC Mexican-Hat Notch (`⌖`)** carve a $>100\text{ dB}$ spectral notch at $f_T$ while boosting the $\pm 1/3\text{ octave}$ flanking edges by $+6\text{ dB}$. If an exposed slider were accidentally bumped by even $15\%$, the $+6\text{ dB}$ boosted flanking edge would land **directly on top of your hyperactive tinnitus frequency**, stimulating the exact neurons we want to inhibit!
- **Scientific Re-Calibration & Progress Tracking**: If your tinnitus pitch shifts—or if you want to measure how much your tinnitus has improved—tapping **"Re-calibrate (5 Steps)"** guides you through the full clinical protocol and logs a new timestamped entry in your **Clinical Progress Log (Pitch & MML)**.

---

## Part 3: The 7 Frontier Therapies & Proposed Daily Clinical Schedule (Tab 2)

| Therapy Mode | DSP / Haptic Mechanism | Auto-Calibrated Level | Auto-Armed Session Timer | Recommended Daily Schedule |
| :--- | :--- | :--- | :--- | :--- |
| **1. AM Residual Inhibition (`◎`)** | $10\text{ Hz}$ Alpha or $40\text{ Hz}$ Gamma $100\%$ sinusoidal amplitude modulation at $f_T$ | **MML** | **`3 min`** | On-demand flare-up relief |
| **2. Bimodal Haptic STDP (`⚡`)** | $20\text{ ms}$ tone bursts at $f_T$ paired with phone `Vibrator` pulses ($\Delta t = 5\text{–}20\text{ ms}$) | **MML** | **`30 min`** | **Morning (`09:00`, 30m)** |
| **3. ISEC Notch Therapy (`⌖`)** | 3-stage cascaded biquad: central notch at $f_T$ + $\pm 1/3\text{ oct}$ $+6\text{ dB}$ flanking boosts | **MML** | **`60 min`** | **Afternoon (`15:00`, 60m)** |
| **4. CR Neuromodulation (`∿`)** | 4-tone quartet ($0.766, 0.900, 1.100, 1.305 f_T$), 3-ON/2-OFF + $\pm 3\%$ TENT-A2 drift | **MML** | **`60 min`** | Alternative afternoon session |
| **5. Stochastic Resonance (`◌`)** | Octave-wide band-limited noise around $f_T$ right at auditory detection threshold | **Threshold ($T_{\text{hear}}$)** | **Continuous / Sleep** | **Evening (`21:30`, 30m+)** |
| **6. Phase Cancel (`−`)** | $180^\circ$ phase-inverted waveform ($-\sin(\phi)$) with $0.25\text{ Hz}$ stereo opposition sweep | **MML** | User / Preset | Optional active mixing |
| **7. Matched Masking (`≈`)** | Continuous phase-coherent pure tone or narrowband hiss at $f_T$ | **MML** | User / Preset | Optional active mixing |
