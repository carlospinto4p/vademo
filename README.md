# vademo

Read and edit the `kb-*` knowledge bases on an Android phone, synced with
their private GitHub repositories.

## Build and install

```bash
./gradlew assembleDebug
./gradlew installDebug     # phone connected with USB debugging on
```

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
