# vademo

Read and edit the `kb-*` knowledge bases on an Android phone, synced with
their private GitHub repositories.

## Build and install

```bash
./gradlew assembleDebug
./gradlew installDebug     # phone connected with USB debugging on
```

## Install without a computer

Every push builds the debug APK (`.github/workflows/build-apk.yml`).
Pushes to `main`, and runs started by hand from the Actions tab, also
replace the `debug-latest` prerelease, whose asset is a plain `.apk`.

On the phone: open the repository's **Releases**, tap the APK under
`debug-latest`, and allow the browser to install unknown apps once.
Any other branch's build is under its run in the **Actions** tab, as a
zipped artifact.

It is signed with the standard debug key, so it never upgrades a
release-signed install (and vice versa) — uninstall first when
switching between the two.

## First run

1. Open **Settings** (gear icon).
2. Paste a GitHub personal access token with the `repo` scope (classic)
   or `Contents: Read and write` (fine-grained), and the name and email
   to put on commits.
3. The four knowledge bases are listed already; edit the list if needed
   (one HTTPS clone address per line).
4. Back on the home screen, tap **Sync** to download them.

## Syncing

Tap **Sync** to send notes edited on the phone and get changes made
elsewhere. If the same lines changed on both sides, the knowledge base
shows "Changed here and on GitHub: tap to choose"; tap it and pick the
phone's version, GitHub's, or combine them by hand.

## Tests

```bash
./gradlew testDebugUnitTest          # on this computer
./gradlew connectedDebugAndroidTest  # on an emulator or phone
```
