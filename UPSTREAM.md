# Upstream provenance

Reference project: https://github.com/Immortal-Protocols/lastbook

Inspected revision: `7b5fb1442a3b05475225e6a7ff90074934476470` (2026-11-17).

The Android implementation is new Kotlin/C++ code informed by Lastbook's topic/catalogue design, citation provenance, bounded read-only assistant and checked offline pack installation. The original Python runtime, Swift app, books, videos, encyclopedias, models and US map files are not copied into this application.

The original MIT notice is retained in `third-party/lastbook-LICENSE.txt`. Original survival-card editorial summaries are not translations of the full proprietary/third-party library. The initial corpus is deliberately small and labelled as an editorial alpha.

The separate llama.cpp runtime is fetched at commit `dde879bc4f954b809892fd0d660515f42536394a` (b12032) during native builds. Its upstream source is https://github.com/ggml-org/llama.cpp and its own MIT notice applies.
