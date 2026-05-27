# Android Setup

Build the debug APK with:

```bash
GRADLE_USER_HOME=/Users/bniladridas/lilt/.gradle gradle :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The modules are `app`, `core`, `domain`, `data`, `feature-auth`, `feature-home`, and `feature-chat`. The `app` module owns the Android application and FCM service. The `core` module holds shared Compose styling. The `domain` module holds models and interfaces. The `data` module owns Room, Firebase boundaries, and repository implementations. The feature modules hold the visible flows.

Firebase is connected to project `lilt-messenger-1262000` through `app/google-services.json`. The Android app uses package `com.lilt.app`, applies the Google Services Gradle plugin, and has the local debug SHA-1 and SHA-256 certificates registered for phone verification.

Phone OTP now calls Firebase Auth. In the Firebase console, open Authentication, enable the Phone provider, and add test phone numbers while developing so SMS quotas are not consumed.

The current Firebase test logins are `+15555550100` and `+15555550101`, both with OTP `123456`. They do not send SMS.

Firestore is the realtime chat backend. Outgoing messages are stored in Room first and marked `Sending`, then updated to `Sent` or `Queued`; queued messages retry on chat load or from the `Retry` action.

Incoming FCM data payloads can be saved into Room when they use this shape:

```json
{
  "threadId": "mira",
  "messageId": "msg-123",
  "senderName": "Mira",
  "body": "Hello from push"
}
```

The app writes user profiles to `users/{uid}`, stores display names and FCM tokens on that profile, loads threads from `threads` where `participantIds` contains the signed-in Firebase UID, starts one-to-one chats by looking up a phone number in `users`, and stores messages below `threads/{threadId}/messages`.
