# Third-party notices

The root MIT license applies to new application code only. These components retain their own terms.

| Component | Version/source | Terms |
|---|---|---|
| Lastbook reference design/code | `7b5fb1442a3b05475225e6a7ff90074934476470` | MIT; notice in third-party/ |
| llama.cpp / ggml | `dde879bc4f954b809892fd0d660515f42536394a` | MIT; source and license at https://github.com/ggml-org/llama.cpp |
| Mapsforge and default themes | 0.25.0 | LGPL-3.0-or-later; full source https://github.com/mapsforge/mapsforge/tree/0.25.0 |
| AndroidSVG | 1.4 | Apache-2.0 |
| Vosk Android | 0.3.75 | Apache-2.0; https://github.com/alphacep/vosk-api |
| Vosk small Russian model | `vosk-model-small-ru-0.22` | Apache-2.0 per https://alphacephei.com/vosk/models; downloaded separately |
| JNA | 5.18.1 | LGPL-2.1-or-later or Apache-2.0 |
| Qwen3 4B GGUF | official Qwen, revision `bc640142c66e1fdd12af0bd68f40445458f3869b`, Q4_K_M | Apache-2.0; downloaded separately from https://huggingface.co/Qwen/Qwen3-4B-GGUF |
| Latvia map | Mapsforge v5 download, byte/hash pin in Packs.kt | OpenStreetMap contributors, ODbL 1.0; https://www.openstreetmap.org/copyright |
| Cormorant Garamond | Google Fonts variable font | SIL Open Font License 1.1; bundled license in third-party/fonts/ |
| Golos Text | Google Fonts variable font | SIL Open Font License 1.1; bundled license in third-party/fonts/ |
| AndroidX, Compose, Material icons | versions in Gradle | Apache-2.0 |
| Gradle wrapper | 8.11.1 | Apache-2.0; https://github.com/gradle/gradle |

This repository contains complete application sources and build scripts. Recipients can modify/rebuild the application with modified Mapsforge components; no DRM or production signing restriction is added. See third-party/ for the LGPL/GPL license texts and component notices. Model weights and map datasets are not committed. The APK exposes OSM attribution on the map screen.

Original scene illustration `app/src/main/res/drawable/camp.png` was created with built-in imagegen for this project. The prompt is retained in docs/design/. The reference mockup is an AI concept, not a screenshot of this app. No Witcher characters, logos or actor voice are bundled.

The short guide text is original Russian editorial material based on cited source topics; links and limitations remain visible in the application. Medical specialist review is not claimed. Original external pages remain with their publishers and are not mirrored wholesale.
