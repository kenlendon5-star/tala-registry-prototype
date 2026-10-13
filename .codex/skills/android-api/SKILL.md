---
name: android-api
description: Retrofit, OkHttp, serialization, resilient networking and API errors. Use when the task concerns this Android development area.
---

# Android API & Networking Expert

## Apply when
Implementing an HTTP client, endpoints, network repositories, or API error handling.

## Practices
- Follow existing Retrofit/OkHttp/Ktor stack rather than adding a competing client without reason.
- Use typed request/response models and a consistent serializer configured for the project's API behavior.
- Model transport errors, HTTP errors, parsing errors, and domain failures separately.
- Support cancellation, sensible timeouts, pagination and idempotent retry where appropriate; don't retry unsafe writes blindly.
- Keep auth tokens out of logs and URLs; use secure credential storage and redacted HTTP logging in release builds.
- Avoid hard-coding secrets, production base URLs and personally identifiable data in code.
- Map network DTOs at repository boundaries when necessary and provide graceful offline or empty states.

## Validation
Exercise success, empty, unauthorized, rate-limit, malformed response and offline scenarios with a mock server or fakes.
