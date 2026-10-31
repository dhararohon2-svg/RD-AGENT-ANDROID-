# RDA’s Coding — Implementation Status for this source revision

## Changes in this revision
- App display name resources set to `RDA's Coding`; terminal launcher alias labels and accessibility labels updated. The all-in-one flavor application ID was moved from `com.termux` to unique `com.rda.ai`, with matching text/runtime path updates.
- Splash logo updated to the RDA's Coding logo; adaptive-icon drawable reference corrected to `@drawable/rda_launcher_foreground`.
- Added a settings page at **Settings → AI Models & API Keys** for multiple saved API provider profiles, encrypted local API-key storage using Android Keystore, `/models` probing/model discovery, and a test prompt.
- Added homepage-to-base-URL normalization for common OpenAI-compatible providers (OpenRouter, OpenAI, Groq, DeepSeek, Together, Mistral, Fireworks, Cerebras).
- Added a selectable custom OpenAI-compatible API profile route for the main AGY chat and a visible selected-provider/model label in the chat input model pill.
- Updated GitHub Actions workflow/artifact names to RDA's Coding.

## Important boundaries
- This generic API adapter requires an OpenAI-compatible `/models` and `/chat/completions` API. A random homepage cannot reliably reveal its API protocol, auth method, or model IDs. Unknown providers need their documented API base URL and model ID.
- This custom API route currently provides text chat only. It does not grant the model terminal execution, file editing, MCP, image/audio attachment support, or autonomous APK builds. Those capabilities require tool-calling adapters and approval wiring for each provider/agent.
- Native-only APIs (including provider-specific Gemini or Anthropic APIs) and CLI agents such as Codex/Claude Code require separate adapters/auth flows. This revision does not claim those adapters are complete.
- Custom API chat messages are currently held in the live chat state; durable conversation-history persistence through the existing agent trajectory database has not been completed for this route.
- The package-ID/runtime-path migration is **not device-verified**.
- Android build was **not verified in this environment**: Gradle 9.1.0 was not cached and this environment could not reach `services.gradle.org` to download it. GitHub Actions must be run to confirm compilation and produce the APK. Do not treat this source ZIP as a verified APK build.
