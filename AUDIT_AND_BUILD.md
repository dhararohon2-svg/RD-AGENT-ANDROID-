# AntiGem Android Workspace — Source Audit & Build Notes

## Scope and honesty

This pass preserves the existing Android/Jetpack Compose application and its local Linux environment. It does not replace the UI or move runtime control to a cloud PC. GitHub Actions is used only as an optional source-build runner that produces an APK artifact; the installed app remains a local Android app.

The source archive was inspected statically. A local Gradle test/build could not be completed in the inspection environment because the Gradle distribution download failed with `UnknownHostException: services.gradle.org`. Treat the GitHub Actions result as the required build verification.

## Existing capabilities found in source

- Jetpack Compose Android UI, chat view model and agent-specific backend routing.
- Antigravity integration through the bundled Go bridge and gRPC hub.
- Claude Code integration through the official CLI and stream-json bridge.
- Local terminal/PTY, editor, project browser, Git operations, background local server.
- In-app Linux/bootstrap installer and local runtime paths.
- Agent-specific MCP, skills/plugins, account/configuration UI and tool approval panels.
- Browser-extension runtime, local automation endpoints, backup/restore scripts and Android ARM64 Go build task.
- Existing Gradle wrapper and release workflow.

Presence of a screen or manager is not proof that every feature works on every Android device. In particular, Linux CLI compatibility depends on ARM64 binaries, Android's app sandbox, available libc/runtime, storage and battery restrictions.

## Changes in this source revision

1. **Exclusive active-agent preference**: older preferences that had both existing agents enabled migrate deterministically to one active agent. Switching writes the active agent explicitly and bridge flags disable the other agent's endpoint/runtime.
2. **Agent selection UI**: removed the “Both” selection so the normal UI follows the requested one-agent-at-a-time model.
3. **Task handoff snapshot**: before switching, stores a bounded recent conversation/tool-state summary locally, excluding attachment bytes and credentials. The next message to the newly selected existing agent includes a handoff context. It is intentionally described as context, not transfer of vendor-private session memory.
4. **Provider-neutral agent manifest/registry**: separates runtime, install strategy, authentication methods, tools, capabilities, configuration scope and adapter readiness. Antigravity and Claude Code are registered as current integrations. Codex CLI is registered as planned, not exposed as chat-ready until its Android ARM64 CLI, sign-in, streaming, session and approval adapter is implemented and tested.
5. **Safer release signing**: removed the hard-coded keystore password and alias from Gradle. Release signing is enabled only when explicit environment variables and a keystore path are provided.
6. **Backup safety**: disabled Android Auto Backup so app-private auth/session data is not silently copied by OS backup; use the app's explicit backup/restore tools for user-controlled backups.
7. **Android baseline**: raised `minSdk` from 24 to 26. `targetSdk` remains 28 for now because legacy storage/foreground-service flows need a dedicated migration and device testing before raising it safely.
8. **GitHub Actions build workflow**: added `.github/workflows/android-build.yml` to set up Java, Go and Android SDK; run JVM unit tests; build the standalone `termux` flavor debug APK; and upload APK/test reports as artifacts. No signing secret is needed for the debug APK.

## Important remaining blockers / follow-up work

- **Codex is not yet a working chat backend.** Registry metadata is not an implementation. The app still routes chat through the two existing backends. Codex needs a tested local CLI adapter for Android ARM64, supported ChatGPT/device login, streaming event mapping, cancellation, session resume, approvals and model controls before it can be enabled in the selector.
- **Automatic failover is not fully implemented.** There is not yet a provider quota/error classifier and safe eligibility router across multiple connected agents. Do not claim seamless failover until implemented and tested.
- **Universal skill/MCP installer is not complete.** Existing agent-specific managers should remain separate behind adapters. A generic installer still needs source trust checks, permission preview, archive/script validation, dependency resolution and rollback.
- **Task handoff is a bounded context snapshot, not exact session migration.** Agent-specific internal state and opaque session IDs are not transferable in general. The receiving agent must inspect current files and validate tool/build status.
- **Package-id caveat:** the all-in-one `termux` flavor currently uses application ID `com.termux` because multiple paths and runtime assumptions in this codebase are tied to that identity. It can conflict with the official Termux app signed by a different key. Renaming it safely requires refactoring all hard-coded `/data/data/com.termux` paths, Go defaults, intents, shell wrappers and update flavor detection; that should be handled as a dedicated tested migration rather than a blind applicationId edit.
- **Target SDK migration:** target SDK 28 is intentionally unchanged in this pass. Android 16 compatibility should be verified on-device; permission, foreground-service, storage and notification behavior must be modernized before raising target SDK.
- **Network/data accounting:** the app can account for its own downloads/requests, but cannot promise exact whole-device data usage for other apps without OS-level facilities and permissions.
- **Local build status:** not verified here due unavailable Gradle distribution network access. The new workflow must run before treating this archive as a verified build.

## Build from an Android phone using GitHub Actions

1. Upload/push this project to a GitHub repository.
2. Open **Actions → AntiGem Android Build & Test**.
3. Choose **Run workflow** (or push a commit to `main`/`master`).
4. Wait for the workflow to finish. If it fails, open the failed step and share the complete log.
5. Open the successful run's **Artifacts** section and download `AntiGem-Android-Debug-...`.
6. Extract the APK from the artifact ZIP and install it on Android. This is a debug APK; it does not require a release keystore.

The workflow builds the existing standalone `termux` flavor because the `standard` flavor is designed around an external Termux environment. The app itself does not require a cloud PC to run; GitHub Actions is only the build runner.

## Feature matrix (current source assessment)

| Area | Existing source | This pass | Remaining work before calling it complete |
|---|---|---|---|
| Android-first shell/UI | Compose screens, settings, chat, terminal, editor | Preserved; no wholesale UI rewrite | Device QA on Android 16 and accessibility/performance pass |
| Existing agents | AGY and Claude backends and bridge endpoints | Enforced one active agent in preference/UI/startup routing | Add integration tests for repeated switch, interrupted switch and process recovery |
| Shared task context | Each agent owns its own conversation/session | Added private bounded handoff snapshot and next-message context | Better structured plan/file-diff/build-state extraction and a user-visible resume preview |
| Codex CLI | No Codex chat backend found in source | Added explicit registry manifest with planned status and test | Implement official CLI installation, device/account auth, streaming, cancellation, session and approval adapter; verify Android ARM64/glibc compatibility |
| Agent registry | Existing code uses a closed `AgentKind` enum | Added vendor-neutral manifests and read-only dependency planner | Route actual runtime lifecycle through adapter interface and dynamic UI controls |
| Dependency isolation | Existing bridge flags can disable AGY hub or Claude endpoints | Selection now resolves to one active existing agent; shared tools are preserved in dependency planning | Process/resource ownership and per-agent package/config isolation tests |
| Skills and MCP | Existing AGY/Claude-specific managers and settings | Preserved; registry records capability/config scope | Unified discovery/install workflow with source trust, signatures/hashes, permission preview and rollback |
| Permission control | Existing agent tool approvals and security settings | Preserved | Unified app-wide policy (`Ask Every Time`, `Smart Approval`, `Manual`) across all adapters |
| Local environment | App-owned bootstrap/rootfs paths, terminal and local server | Preserved; minimum Android API raised to 26 | Environment inventory, disk budget, download resume/checksum and safe package reconciliation |
| Build/test | Gradle wrapper and Go build task | Added CI test/build/artifact workflow | CI must pass; local build not possible in this network-restricted session |
| Secrets | Existing preferences/config and agent-owned auth | Removed hard-coded release keystore password; disabled Android Auto Backup | Review token storage/rotation and ensure all secrets use Android Keystore-backed encrypted storage where feasible |
| Android identity | `termux` flavor is the all-in-one environment | Not renamed to avoid breaking path/runtime assumptions | Dedicated migration away from `com.termux` package identity and all hard-coded paths; see caveat above |
| Network/data controls | Existing network inspector and download progress surfaces | Preserved | Add app-owned aggregate byte accounting and user-defined Wi-Fi/mobile-data/download-size limits |
| Auto failover | No complete cross-agent router found | Not claimed implemented | Add error/quota classification, user-authorized alternative eligibility, transparent switch log and loop prevention |

## Recommended implementation order for the next revision

1. Get the new Android Actions workflow green and fix all compile/test failures it reports.
2. Add tests for exclusive agent selection, bridge flags, process restart and handoff recovery.
3. Refactor the current two implementations behind an `AgentAdapter` lifecycle interface without changing their proven protocols.
4. Implement and test the Codex CLI adapter on a real ARM64 Android device, including supported ChatGPT account login; only then mark its manifest `AVAILABLE` and expose it in the selector.
5. Add unified environment/dependency preflight and rollback before any installer is allowed to change the user's runtime.
6. Add safe auto-switch/failover and unified skill/MCP discovery after the adapter contracts are stable.
7. Migrate the `com.termux` app ID and hard-coded paths in a separate branch with install/upgrade tests so standalone AntiGem can coexist with the official Termux app.
