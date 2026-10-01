# Agent rules for PilzScout

## Never delete app data on the Pixel 7 Pro

The Pixel 7 Pro connected over adb is the owner's daily phone. Its PilzScout install holds real
observation history (photos, positions, results) that exists nowhere else.

- In-place updates are fine: `./gradlew :app:installBundledDebug` (or `installPlayDebug`) or
  `adb install -r` over the existing install, keeping the app's data. Both flavours share the
  applicationId and the debug key, so switching between them is also an in-place update.
- Deleting data is a no-go: never uninstall PilzScout on the Pixel, never `pm clear` it, never run
  `uninstallAll`, `uninstall*Debug`, `connectedAndroidTest` or `installDebugAndroidTest` against it,
  and never delete anything under its `files/` or `databases/` folders (DataStore settings live in
  `files/datastore`).
- A change that would make an update wipe data counts as deleting data: a Room schema or version
  change while `HistoryDatabase` or `SpeciesDatabase` fall back to destructive migration, a
  different signing key or applicationId, or a downgraded versionCode. Before installing such a
  build, back up the app data and say where the backup is:
  `adb exec-out run-as de.pilzscout.app sh -c 'tar cf - files/observations files/datastore databases 2>/dev/null' > backup.tar`
  (DataStore lives in `files/datastore`; `exec-out` and the silenced stderr keep the tar intact;
  check it with `tar tf backup.tar`)
  or test on the emulator instead.
