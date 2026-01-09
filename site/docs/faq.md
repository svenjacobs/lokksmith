# Frequently Asked Questions

## Android

### Temporal validation exceptions in emulator

> I often get temporal validation exceptions in an Android emulator

Android emulators are prone to [clock drift](https://en.wikipedia.org/wiki/Clock_drift), especially
after running for extended periods and when regularly suspended and resumed. To work around
this, there are several solutions:

- Perform a cold boot of the emulator
- Toggle the "Automatic date and time" setting off and on
- Increase the `leewaySeconds` option of the client

### Auth Tab on Motorola devices

> Authentication via [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab)
> does not work on Motorola devices

Newer Motorola devices ship with the Moto Secure app, which includes phishing protection and other
security features. Unfortunately, Moto Secure can interfere with authentication via Auth Tab. From
Lokksmith’s perspective, the authentication flow is cancelled when Moto Secure is involved. As a 
workaround, avoid using Auth Tab on Motorola devices. You can detect Motorola devices with the 
following code

```kotlin
val isMotorolaDevice = Build.MANUFACTURER.lowercase() == "motorola"
```
