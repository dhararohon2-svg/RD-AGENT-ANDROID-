# RDA's Coding — API Provider Hub patch

## Implemented in this source revision
- App display label set to `RDA's Coding`; user-facing AntiGem labels in UI copy were rebranded where safe.
- Fixed adaptive launcher foreground drawable resource reference.
- Added Settings → AI Models & API Keys.
- Multiple named API-compatible provider profiles can be saved, selected, edited, and deleted.
- API key values are encrypted at rest with AES/GCM using an Android Keystore key.
- Provider connection test requests `/v1/models`; returned model IDs can be tapped into the model field.
- A test prompt can be sent through `/v1/chat/completions` and the response shown in the provider screen.
- APK archive base name changed to `rda-coding`.

## Endpoint compatibility (important)
This supports providers implementing the OpenAI-compatible REST shape (`GET /v1/models`, `POST /v1/chat/completions`). Use an API **base URL**, for example `https://openrouter.ai/api/v1`, not a provider's marketing/homepage URL. A website URL alone cannot reveal or generate a private API key. Native-only APIs and CLI coding agents need dedicated adapters and cannot be made compatible merely by pasting their website URL.

## Not yet implemented / not verified
- This patch does **not** route the main coding chat, filesystem tools, terminal execution, and build/test workflow through the selected custom API profile. The API screen is a working provider manager and model playground, not proof of universal coding-agent integration.
- Dedicated Codex CLI, native Anthropic API, Google/Gemini API, and arbitrary provider-specific auth formats are not implemented by this patch unless already supported by the pre-existing code.
- Build was not verified in this environment: Gradle wrapper needs to download Gradle 9.1.0, but network DNS access to `services.gradle.org` is unavailable here. Run GitHub Actions and inspect the complete build log before treating this as build-successful.
