# Implementation log

## Authorization and ownership

2026-11-17: after the plan, two concept boards, selection of the first visual direction and clarification of Latvia/modern Android devices, the owner asked whether implementation was proceeding. Implementation is now in progress under the original app-and-GitHub request. The primary agent owns this entire new repository and the final GitHub push; no other repository or deployment is in scope.

## First vertical slice

Russian text input → local guide retrieval → source-linked response with equipment-aware follow-up → Android offline TTS. Core behavior is tested on the JVM. Android speech/visual/device checks are recorded separately and never inferred from core tests.

## Initial environment

Local host is Linux arm64 with Java 21. The documented `/opt/android-sdk` is absent, as are adb and a local emulator/KVM. Android assembly and instrumentation will use a pinned GitHub Actions toolchain if local SDK tooling cannot run on arm64. This is a build-environment observation, not a product limitation.

## Publication

The user requested the destination `Kurubik/survival-kit`. Initial remote was empty. Publish only this repository's intended source, licenses and evidence; never model weights, credentials, machine profiles or source user history. Alpha limitations must remain visible in README and application readiness screens.
