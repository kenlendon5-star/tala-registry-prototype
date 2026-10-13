---
name: android-security
description: Android permissions, credential storage, privacy, and secure release practices. Use when the task concerns this Android development area.
---

# Android Security & Privacy Expert

## Apply when
Working on authentication, storage, permissions, deep links, WebView, secrets or sensitive user data.

## Guardrails
- Request only required permissions and explain use at the right moment; respect denial and revocation.
- Use Android Keystore-backed mechanisms for protecting sensitive encryption keys; evaluate appropriate secure storage APIs for the project's minSdk.
- Do not embed API keys, client secrets, signing keys or passwords in the app binary as if they were private.
- Require HTTPS and prevent cleartext traffic unless a documented local/development exception is needed.
- Validate deep links/intents and component export settings; avoid exposing sensitive activities/providers.
- Disable sensitive logging, backups of secrets and unnecessary analytics data collection.
- Treat WebView content, untrusted input and exported components as security boundaries.
- Prefer server-side authorization checks; never rely solely on mobile UI restrictions.

## Review
Threat-model the specific feature and explain tradeoffs; do not claim an audit is complete without testing.
