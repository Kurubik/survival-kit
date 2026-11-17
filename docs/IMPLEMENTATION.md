# Implementation log

## Authorization and ownership

2026-11-17: after the plan, two concept boards, selection of the first visual direction and clarification of Latvia/modern Android devices, the owner asked whether implementation was proceeding. Implementation is now in progress under the original app-and-GitHub request. The primary agent owns this entire new repository and the final GitHub push; no other repository or deployment is in scope.

## First vertical slice

Russian text input → local guide retrieval → source-linked response with equipment-aware follow-up → Android offline TTS. Core behavior is tested on the JVM. Android speech/visual/device checks are recorded separately and never inferred from core tests.

## Initial environment

Local host is Linux arm64 with Java 21. The documented `/opt/android-sdk` is absent, as are adb and a local emulator/KVM. Android assembly and instrumentation will use a pinned GitHub Actions toolchain if local SDK tooling cannot run on arm64. This is a build-environment observation, not a product limitation.

## Publication

The user requested the destination `Kurubik/survival-kit`. Initial remote was empty. Publish only this repository's intended source, licenses and evidence; never model weights, credentials, machine profiles or source user history. Alpha limitations must remain visible in README and application readiness screens.

## Verification observations

- Portable engine: seven JUnit tests passed locally on Java 21 (Russian search/forms, equipment negation, stateful steps, emergency precedence, unknown knowledge, invalid model output and source keys).
- Vosk RU and Latvia map were downloaded into the private research directory; exact byte counts and SHA-256 are pinned in `Packs.kt`. They are not committed.
- Both bundled font files report Russian and Latvian coverage through fontconfig. Actual Android rendering is a separate CI check.
- First CI run `44061080421` stopped in the redundant `android-actions/setup-android` step before compilation. The workflow now uses the SDK already present on the pinned Ubuntu runner and installs only explicit package versions. Android verification was not passed by that failed run.
- CI diagnostics exposed that the pinned llama.cpp uses `load_mode=LLAMA_LOAD_MODE_MMAP`, not the historical `use_mmap` member; bridge updated to the actual pinned header.
- Linux Vosk RU smoke test with four eSpeak-generated synthetic utterances failed expected keywords in all four. This is a negative synthetic-input result, not a measurement of human speech. Voice input now becomes an editable draft and requires sending/confirmation; it cannot silently execute a misrecognized instruction. The failure is retained rather than described as a voice-quality pass.
