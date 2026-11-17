# Survival Kit / Путник

Native Android hiking assistant. Russian is the primary language. Latvia is the initial region; nearby Baltic countries are optional later content packs.

## Scope and ownership

This repository is owned by the primary implementation agent for this task. Changes are limited to this repository; planning originals remain in `../artifacts/survival-kit-plan-20261117/`. The same primary agent performs the user-requested GitHub push. No production deployment or messaging to third parties is part of this task.

## Product rules

- Match `docs/design/01-camp-companion-guide.png`, the selected visual reference. Other screens extend this style.
- Do not present AI mockups as implemented Android screenshots.
- Russian text, UTF-8, metric units, Europe/Riga; searchable official local placenames.
- Offline guides, search, equipment and emergency cards must not depend on a model or network.
- Distinguish downloaded, available and verified. Never fake GPS, model responses, voice availability, maps or an SOS transmission.
- Source references must resolve. Editorial draft content is not professionally reviewed first aid.
- Keep the runtime personality separate from critical procedures and warnings.
- No keys, signing stores, model weights, user coordinates or chat histories in Git.

## Verification

Use the included Gradle wrapper. `SURVIVAL_CORE_ONLY=1 ./gradlew :core:test` tests the portable assistant engine without an Android SDK. Android builds require `ANDROID_HOME` explicitly (normally `/opt/android-sdk`) or `local.properties`.

Real-device speech, GPS, battery, local-model quality and emergency calling remain unverified until exercised on hardware. CI/emulation must be identified as such.
