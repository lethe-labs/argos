<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" alt="Argos icon">
</p>

# Argos

**Find out whether your phone was touched while you were away.**

Argos is an offline Android app that produces an integrity report of your own
device. Leave your phone at a hotel, hand it over at a border, have it
confiscated for a few hours: when you get it back, Argos tells you what
happened to it in the meantime — and whether its own records were tampered
with.

> **Status: pre-alpha.** The core logic is unit-tested, but the app has not yet
> been validated on real devices. Do not rely on it yet.

Argos never locks, wipes or modifies your device. It only observes. It is a
sibling project of [Oblivion](https://github.com/lethe-labs/oblivion-v2), which
acts; Argos tells you whether you need to.

---

## How it works

1. **Before leaving**, tap *I'm leaving*. Argos snapshots the device state and
   shows you **three words** to remember. Nothing to write down.
2. **While you are away**, Argos logs unlock attempts, reboots and SIM
   removals in a tamper-evident journal.
3. **On return**, tap *I'm back*. Argos shows you the time you left and your
   three words, and you confirm in one tap that this is your departure. It
   then compares the current state with the departure snapshot, reads the
   journal, and gives you a verdict: *clean*, *suspicious*, or *tampered*.

## What it detects

| Event | How |
|---|---|
| Reboots | The system boot counter, readable even if Argos was not running |
| Failed and successful unlocks | Observation-only device admin (`watch-login`) |
| Apps installed while you were away | Diff of the installed apps |
| New accessibility services or device admins | Diff of system settings — the usual signature of stalkerware |
| USB debugging or developer options switched on | System settings |
| Clock changed | Wall clock compared with the monotonic system clock |
| SIM removed or inserted, even if put back before you return | SIM changes logged as they happen, plus the operator code |

## How the evidence is protected

An intruder may try to cover their tracks. Two mechanisms make that visible:

- **A hash-chained journal.** Each entry commits to the previous one, so
  editing, deleting or reordering an entry breaks the chain.
- **Three seal words.** They are derived from the departure record by HMAC,
  under a fresh Android Keystore key that cannot leave the device, and are
  recomputed on return rather than stored. If the app is reset or
  reinstalled, the key is gone and Argos says so. If an intruder resets Argos
  and starts a fake departure, it gets a new key and therefore different
  words — and a different departure time — which you notice at a glance.

How strong the words are, plainly: three words out of 256 give about 24 bits.
An intruder who already knows your PIN can read your words on the return
screen, but cannot make a fake departure produce them; they could only keep
starting departures until three random words happen to match, around 16
million attempts on the phone itself.

The app also blocks screenshots, so your words and reports never end up in the
gallery or the task switcher.

## What it cannot detect

Read this before trusting a *clean* verdict.

- **Anything done to a powered-off device**, including direct extraction of
  the memory chip or hardware modification. No software runs while the phone
  is off. If the phone is switched on, the boot counter will show it.
- **An attacker with root or a kernel exploit**, who can falsify everything
  Argos observes, and truncate the most recent journal entries.
- **A lockscreen bypass through a vulnerability**: it is not an unlock attempt,
  so it is not logged as one.
- **A new fingerprint or face enrolled**: Android does not let apps list them.
- **Which exact SIM card** is inserted: since Android 10, the SIM identifier is
  reserved to the system. Only removal and operator changes are visible.

Your own unlock when you come back is logged too. Argos marks the most recent
one as *probably you*; check its time anyway.

The seal relies on you recognising your three words. If you have forgotten
them, the departure time shown alongside is still a check: a fake departure
would show a later time, unless the intruder changed the clock — which Argos
reports.

## Permissions

| Permission | Why |
|---|---|
| Device admin, `watch-login` policy only | To be told about unlock attempts. It cannot lock or wipe the device. |
| `QUERY_ALL_PACKAGES` | To see which apps appeared while you were away |
| `RECEIVE_BOOT_COMPLETED` | To log reboots |

There is **no `INTERNET` permission**: Argos cannot open a network connection,
which you can check in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).
No account, no telemetry, no analytics.

`QUERY_ALL_PACKAGES` is restricted on Google Play; Argos targets F-Droid.

## Build

Requirements: JDK 17, Android SDK 34.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # pure-JVM unit tests, no device needed
```

The detection logic, the journal, the seal and the verdict live in `core/` and
have no Android dependency, which is what makes them testable without a
device. `platform/` reads the real device state; `ui/` is the Compose screen.

## License

[GNU Affero General Public License v3.0 or later](LICENSE).

```
Argos — device integrity reports for Android
Copyright (C) 2026-present  lethe-labs
```
