# 🛡️ VisorX

> **Real-time Android protection against shoulder surfing using front-camera computer vision and on-device privacy detection.**

## 📌 Overview

**VisorX** is an Android privacy-protection application designed to defend smartphone users against **shoulder surfing** and visual eavesdropping in public or semi-public environments.

When a user accesses sensitive information such as messages, passwords, banking details, personal documents, or private content, a nearby person may be able to view the screen over the user's shoulder.

VisorX addresses this problem by using the smartphone's **front-facing camera** to detect nearby faces, identify the likely primary user, estimate whether another person is facing the screen, and calculate a real-time shoulder-surfing risk score.

When the detected risk remains sufficiently high, VisorX automatically activates a **privacy protection overlay** to prevent sensitive information from remaining visible.

The proposed architecture is based on research into lightweight mobile face detection, anonymous tracking, head-pose-based attention estimation, multi-signal threat scoring, and real-time visual protection.

---

## 🎯 Problem

Traditional smartphone security mechanisms protect against unauthorized digital access, but they cannot prevent someone physically looking at the screen.

For example:

```text
                 👤 Bystander
                    👀
                   /  \
                  /    \
             ┌────────────┐
             │   PHONE    │
             │   SCREEN   │
             └────────────┘
                   👤
                  User
```

The challenge is not simply detecting another face.

> **The system must determine whether another person is actually viewing the smartphone screen.**

Therefore, VisorX combines multiple signals instead of triggering protection whenever a second face appears.

---

# 🧠 Core Concept

```text
Front Camera
     ↓
Face Detection
     ↓
Multi-Face Tracking
     ↓
Primary User Estimation
     ↓
Secondary Face Detection
     ↓
Proximity Analysis
     ↓
Head Pose Estimation
     ↓
Optional Gaze Refinement
     ↓
Temporal Persistence
     ↓
Shoulder-Surfing Risk Score
     ↓
Threat Confirmation
     ↓
Privacy Protection
```

---

# 🏗️ System Architecture

```text
                         ┌──────────────────┐
                         │   FRONT CAMERA   │
                         └────────┬─────────┘
                                  │
                                  ▼
                       ┌─────────────────────┐
                       │ CameraX Image       │
                       │ Analysis Pipeline   │
                       └──────────┬──────────┘
                                  │
                                  ▼
                       ┌─────────────────────┐
                       │ MediaPipe /         │
                       │ BlazeFace Detection │
                       └──────────┬──────────┘
                                  │
                         ┌────────┴────────┐
                         │                 │
                         ▼                 ▼
                  Primary User       Secondary Face
                   Estimation             │
                         │                ▼
                         │          Proximity Check
                         │                │
                         │                ▼
                         │           Head Pose
                         │                │
                         │                ▼
                         │          Gaze Proxy
                         │          (Optional)
                         │                │
                         └────────┬────────┘
                                  ▼
                         Temporal Tracking
                                  │
                                  ▼
                          Threat Score Engine
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                 LOW RISK                   HIGH RISK
                    │                           │
                    ▼                           ▼
                Normal UI                Privacy Overlay
                                                │
                                                ▼
                                         Protected Screen
```

---

# 🔍 Detection Pipeline

## 1. Front-Camera Capture

VisorX uses the device's built-in front-facing camera.

The Android camera pipeline is designed around:

- CameraX
- `ImageAnalysis`
- `STRATEGY_KEEP_ONLY_LATEST`
- on-device processing

The latest-frame strategy prevents old frames from accumulating when inference is slower than the camera stream.

---

## 2. Lightweight Face Detection

The recommended detector for the MVP is:

**MediaPipe Face Detection / BlazeFace-class detection**

The research favors lightweight single-stage detectors because continuous mobile computer vision requires low latency and efficient CPU/GPU usage.

BlazeFace was specifically designed for mobile camera applications and provides facial bounding boxes and keypoints that can support downstream orientation estimation.

### Why lightweight detection?

| Requirement | Lightweight Detector |
|---|---|
| Real-time processing | ✅ |
| Mobile compatibility | ✅ |
| Low latency | ✅ |
| Multiple faces | ✅ |
| Keypoint support | ✅ |
| Lower resource usage | ✅ |
| Suitable for MVP | ✅ |

**YuNet** is also a potential alternative because of its very small model size, but MediaPipe/BlazeFace is the primary MVP direction.

---

# 👤 Primary User Estimation

VisorX does **not** require facial recognition.

The system does not need to know:

> "Who is this person?"

It only needs to determine:

> "Which detected face is most likely the phone user?"

The primary user can be estimated using anonymous spatial and temporal signals:

### Face Size

The user's face is typically closer to the front camera.

```text
User:
████████████

Bystander:
██████
```

### Screen-Centered Position

The phone user is generally closer to the center of the front-camera view.

### Head Orientation

The primary user normally faces the smartphone screen.

### Temporal Persistence

The legitimate user usually remains visible across consecutive frames, while passing bystanders may appear temporarily.

Conceptually:

```text
Primary User Score =
    Face Size
  + Center Position
  + Screen Orientation
  + Temporal Persistence
```

> These are heuristic signals and must be validated experimentally, especially in crowded environments.

---

# 👀 Attention Detection

A second person's presence does not necessarily mean shoulder surfing.

VisorX therefore distinguishes between:

```text
Person nearby
      ≠
Person looking at screen
```

## Head Pose

The first-line attention signal is head orientation.

The system estimates:

- **Yaw** — left/right rotation
- **Pitch** — up/down rotation
- **Roll** — head tilt

Conceptually:

```text
Facial Landmarks
       ↓
    solvePnP
       ↓
Rotation Matrix
       ↓
 Euler Angles
       ↓
Yaw / Pitch / Roll
```

Initial experimental screen-facing thresholds can be configured around:

```text
|Yaw|   < 20°
|Pitch| < 25°
```

These are starting parameters rather than universal values and should be tuned using real-device testing.

---

# 👁️ Optional Gaze Refinement

Head pose provides coarse information about attention, but it does not guarantee that the person's eyes are looking at the screen.

VisorX therefore supports an optional second-level gaze analysis:

```text
Secondary Face
      ↓
Head Pose
      ↓
Clearly Screen-Facing?
   /             \
 NO              YES
 |                |
Ignore        Evaluate
              further
                 ↓
            Gaze Proxy
                 ↓
             Risk Engine
```

Full gaze estimation is considered an advanced feature because monocular mobile gaze estimation can be affected by:

- lighting
- head movement
- eye occlusion
- viewing angle
- calibration

The MVP therefore prioritizes head pose.

---

# ⚠️ Shoulder-Surfing Risk Engine

VisorX uses a **multi-signal threat score** instead of a simple face-count trigger.

Conceptually:

```text
Risk =
    w₁ × Presence
  + w₂ × Proximity
  + w₃ × Head Pose
  + w₄ × Gaze
  + w₅ × Persistence
```

### Signals

| Signal | Question |
|---|---|
| Presence | Is another face detected? |
| Proximity | Is the person close enough to potentially read the screen? |
| Head Pose | Is the person's head oriented toward the screen? |
| Gaze | Do the eyes appear directed toward the screen? |
| Persistence | Has the behavior continued long enough? |

This approach helps reduce false positives from people who simply pass through the camera's field of view.

---

# ⏱️ Temporal Confirmation

VisorX does not immediately activate privacy mode when a face appears.

A potential threat must persist for a short period.

### Activation

Initial design target:

```text
Risk > threshold
       ↓
300–500 ms persistence
       ↓
Privacy Mode ON
```

### Recovery

When the threat disappears:

```text
Risk falls below threshold
       ↓
800–1000 ms recovery
       ↓
Privacy Mode OFF
```

This temporal hysteresis helps prevent privacy protection from rapidly switching on and off because of momentary tracking fluctuations.

---

# 🔒 Privacy Protection

## MVP: Full-Screen Privacy Overlay

When a shoulder-surfing threat is confirmed:

```text
┌────────────────────────────┐
│                            │
│                            │
│          🔒 PRIVATE        │
│                            │
│      SCREEN PROTECTED      │
│                            │
│                            │
└────────────────────────────┘
```

### Advantages

- Very fast
- Simple to implement
- Strong protection
- Independent of underlying application content
- Suitable for the MVP

### Limitation

The legitimate user also loses access to the protected content while the overlay is active.

---

# 🚀 Advanced Protection

## Selective Sensitive-Content Masking

Instead of hiding the complete screen:

```text
Account: ************
Balance: ₹**,***
```

Only sensitive fields can be hidden.

This provides better usability but is considerably more difficult to generalize across arbitrary third-party Android applications.

---

## Eye-Shield-Style Protection

A future version could implement spatial visual protection that attempts to preserve readability for a close primary user while reducing readability for distant observers.

This is considered an **advanced production direction**, not an MVP dependency.

---

# 🔋 Adaptive Processing

Continuous computer vision can consume significant battery and computational resources.

VisorX therefore uses adaptive processing.

```text
                 SINGLE USER
                     │
                     ▼
                ~10 FPS
              Baseline Analysis
                     │
                     │
              Secondary Face
                Detected
                     │
                     ▼
               THREAT STATE
                     │
                     ▼
                 ~30 FPS
              Detailed Analysis
```

The application can process fewer frames during normal operation and increase analysis frequency when a potential bystander is detected.

This allows the system to balance:

- responsiveness
- battery life
- CPU/GPU utilization
- thermal performance

---

# 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Platform | Android |
| Language | Kotlin |
| Camera | CameraX |
| Camera Processing | ImageAnalysis |
| Face Detection | MediaPipe / BlazeFace |
| Face Tracking | Temporal tracking |
| Head Pose | Facial landmarks + OpenCV `solvePnP` |
| Optional Attention | Gaze proxy |
| ML | On-device inference |
| Computer Vision | MediaPipe / OpenCV |
| Protection | Android privacy overlay |
| External Hardware | None |
| Cloud Processing | None |

---

# 🔐 Privacy by Design

Privacy is a fundamental requirement of VisorX.

The application is designed to:

- ✅ Process camera information locally
- ✅ Avoid uploading camera frames
- ✅ Avoid storing captured images
- ✅ Avoid facial recognition
- ✅ Avoid persistent face embeddings
- ✅ Use anonymous tracking
- ✅ Discard frames after analysis
- ✅ Require no external hardware

The purpose of the vision pipeline is **behavioral threat detection**, not identity recognition.

---

# 📊 Decision Matrix

| Technology / Method | Speed | Complexity | Privacy | MVP |
|---|---:|---:|---:|---:|
| MediaPipe / BlazeFace | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ |
| YuNet | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ |
| MTCNN | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ❌ |
| Heavy CNN | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ❌ |
| Facial Recognition | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ❌ |
| Head Pose | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ |
| Full Gaze Estimation | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | 🔄 |
| Privacy Overlay | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ✅ |
| Eye-Shield Style | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 🔄 |

---

# 🧪 Testing Plan

VisorX should be tested in realistic scenarios.

### Scenario 1 — Single User

```text
User
 ↓
Phone

Expected:
🟢 SAFE
Normal screen
```

### Scenario 2 — Nearby Person Looking Away

```text
Bystander ←     User
                  ↓
                 📱

Expected:
🟢 SAFE
```

### Scenario 3 — Brief Glance

```text
Bystander → 📱
     ↓
Short duration

Expected:
🟢 SAFE / Monitoring
```

### Scenario 4 — Sustained Shoulder Surfing

```text
Bystander → 📱
     ↓
Close
     ↓
Screen-facing
     ↓
Persistent
     ↓
Risk threshold exceeded

Expected:
🔴 PRIVACY MODE
```

### Scenario 5 — Bystander Leaves

```text
Bystander disappears
       ↓
Risk decreases
       ↓
Recovery period
       ↓
Normal mode
```

### Scenario 6 — Multiple Bystanders

```text
User + Person A + Person B

Expected:
Evaluate each secondary face
and trigger when threat conditions are satisfied.
```

---

# 📈 Evaluation Metrics

## Detection

- Face detection accuracy
- Multi-face detection accuracy
- Primary-user classification accuracy
- Attention classification accuracy

## Security

- Shoulder-surfing detection rate
- False-positive rate
- False-negative rate
- Protection activation latency

## Performance

- FPS
- Model inference latency
- CPU usage
- GPU usage
- RAM usage
- Battery consumption
- Device temperature

## Usability

- Overlay response time
- False-trigger frequency
- Recovery time
- User readability
- Screen interaction disruption

---

# 📁 Project Structure

```text
VisorX/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── .../
│           │       ├── camera/
│           │       ├── detection/
│           │       ├── tracking/
│           │       ├── pose/
│           │       ├── risk/
│           │       ├── privacy/
│           │       └── ui/
│           │
│           ├── res/
│           │   ├── layout/
│           │   ├── drawable/
│           │   └── values/
│           │
│           └── AndroidManifest.xml
│
├── docs/
│   ├── architecture/
│   ├── research/
│   └── testing/
│
├── screenshots/
│
├── README.md
├── LICENSE
└── .gitignore
```

---

# 🗺️ Development Roadmap

## Phase 1 — Camera

- [ ] Create Android project
- [ ] Configure CameraX
- [ ] Request camera permission
- [ ] Display front-camera preview
- [ ] Configure `ImageAnalysis`

## Phase 2 — Face Detection

- [ ] Integrate MediaPipe / BlazeFace
- [ ] Detect multiple faces
- [ ] Draw face bounding boxes
- [ ] Extract facial keypoints

## Phase 3 — Tracking

- [ ] Track detected faces
- [ ] Assign temporary anonymous IDs
- [ ] Implement persistence tracking
- [ ] Re-run detection when tracking confidence decreases

## Phase 4 — Primary User

- [ ] Calculate face size
- [ ] Calculate center distance
- [ ] Calculate orientation
- [ ] Calculate persistence
- [ ] Select primary user

## Phase 5 — Attention

- [ ] Implement head-pose estimation
- [ ] Calculate yaw
- [ ] Calculate pitch
- [ ] Determine screen-facing state
- [ ] Add optional gaze proxy

## Phase 6 — Risk Engine

- [ ] Implement presence score
- [ ] Implement proximity score
- [ ] Implement pose score
- [ ] Implement gaze score
- [ ] Implement persistence score
- [ ] Calculate overall risk
- [ ] Tune thresholds

## Phase 7 — Privacy

- [ ] Implement privacy overlay
- [ ] Implement activation delay
- [ ] Implement recovery delay
- [ ] Test response latency

## Phase 8 — Optimization

- [ ] Implement adaptive frame sampling
- [ ] Optimize model inference
- [ ] Reduce memory usage
- [ ] Measure battery consumption
- [ ] Test on multiple Android devices

---

# 🧩 MVP

The recommended MVP deliberately avoids unnecessary complexity.

```text
Kotlin
  ↓
CameraX
  ↓
MediaPipe / BlazeFace
  ↓
Multi-Face Detection
  ↓
Anonymous Tracking
  ↓
Primary User Estimation
  ↓
Head Pose
  ↓
Proximity + Persistence
  ↓
Threat Score
  ↓
Temporal Confirmation
  ↓
Privacy Overlay
```

### MVP Goals

The first working version should demonstrate:

- [x] Front-camera monitoring
- [x] Multi-face detection
- [x] Primary-user estimation
- [x] Bystander detection
- [x] Head-pose-based attention detection
- [x] Threat scoring
- [x] Automatic privacy protection
- [x] Local processing
- [ ] Real-device performance evaluation
- [ ] Threshold tuning

---

# 🔮 Future Scope

Potential future improvements include:

- Lightweight gaze estimation
- Improved distance estimation
- Depth-aware threat detection
- Better multi-person tracking
- Personalized attention calibration
- Adaptive privacy levels
- Selective sensitive-content masking
- Eye-Shield-style visual protection
- Dedicated banking/password protection
- Hardware-accelerated inference
- Larger real-world evaluation datasets

---

# 📚 Research Basis

VisorX's proposed architecture is based on research covering:

- Mobile face detection
- BlazeFace
- YuNet
- MediaPipe
- Face tracking
- Head-pose estimation
- Gaze estimation
- Shoulder-surfing detection
- Privacy-preserving visual analysis
- Adaptive UI protection

### Key References

1. Bazarevsky et al. — **BlazeFace: Sub-millisecond Neural Face Detection on Mobile GPUs**
2. Wu et al. — **YuNet: A Tiny Millisecond-level Face Detector**
3. Corbett et al. — **ShouldAR: Detecting Shoulder Surfing Attacks Using Multimodal Eye Tracking and Augmented Reality**
4. Huang et al. — **ScreenGlint: Practical, In-situ Gaze Estimation on Smartphones**
5. Tang & Shin — **Eye-Shield: Real-Time Protection of Mobile Device Screen Information from Shoulder Surfing**
6. Lei et al. — **An End-to-End Review of Gaze Estimation and its Interactive Applications on Handheld Mobile Devices**
7. Ali et al. — **A Visual Context Aware Solution for Protecting Mobile Users from Visual Privacy Attacks**

---

# ⚠️ Limitations

VisorX is subject to several practical limitations:

- Head pose is not a perfect representation of eye gaze.
- Primary-user heuristics can fail in crowded environments.
- Low-light conditions can reduce detection quality.
- Partial face occlusion can affect pose estimation.
- Extreme viewing angles can reduce reliability.
- Full-screen protection can temporarily interrupt the legitimate user.
- Selective masking across arbitrary third-party applications is difficult.
- Exact risk thresholds require real-device testing.
- Performance varies between Android devices.

The numerical thresholds used in the initial implementation should therefore be treated as **configurable experimental parameters**, not universal guarantees.

---

# 🎯 Project Objective

VisorX aims to answer three questions in real time:

```text
1. Who is using the phone?
          ↓
2. Is another person watching?
          ↓
3. Can we protect the screen quickly enough?
```

> **Detect the observer. Estimate the threat. Protect the screen.**

---

## 📌 Project Status

🚧 **In Development**

**Target:** A functional Android APK/AAB capable of detecting potential shoulder-surfing behavior in real time and automatically activating privacy protection on a physical Android device.

---



```text
team ctrl alt delete
XO_code Hackathon / Academic Project
```

---


