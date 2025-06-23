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
