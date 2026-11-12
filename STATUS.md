# STATUS — YT Music "Wrapped" listening-log patch (ReVanced)

Session checkpoint. RE PHASE COMPLETE → next: write code. Resume here after compaction.

## GOAL
New ReVanced patch for YT Music `com.google.android.apps.youtube.music` (target **8.40.54**)
that harvests listening data into a local SQLite DB (exact datetimes, listen duration,
frequency, time-of-day, queue, source, device context) to generate a Spotify-Wrapped-style
report. MUST coexist with the default ReVanced patches already in the user's build.

## USER CONSTRAINTS
- Do NOT research legality/ToS/legal topics (user said skip; considered settled).
- User phone runs patched YT Music 8.40.54 with default ReVanced patches.
- Extracted APK: `/Users/yogesh/Desktop/Wraped/YT Music ReVanced.apk` (PATCHED build).
- revanced-manager cloned in workspace (NOT analyzed; not required — CLI patching suffices).
- User wants our patch to work seamlessly alongside other patches (no race conditions).
- Use available skills where relevant (loaded android-data-layer for DB design; adapted to
  plain SQLite — Room/Hilt/sync do NOT apply: purely-local in-process extension).

## WORKSPACE
- `/Users/yogesh/Desktop/Wraped/revanced-patches` — main patches repo (WRITE CODE HERE)
- `/Users/yogesh/Desktop/Wraped/revanced-manager` — manager source (not analyzed)
- `/Users/yogesh/Desktop/Wraped/YT Music ReVanced.apk` — user's patched build (68MB, 8 dex)
- `/Users/yogesh/Desktop/Wraped/FINDINGS.md` — **FULL verified RE findings (READ FIRST)**
- Analysis scratch: `/private/var/folders/jw/b3rw7d515n5_3hpjgngmc2f80000gn/T/opencode/ytm/`
  - `decompiled/sources/` — jadx (30104 classes) DONE
  - `classes4.dump` — dexdump -d classes4.dex (117MB; class `ie` lives here) DONE

## TOOLS
jadx, apktool, d8 at /opt/homebrew/bin; dexdump at
`~/Library/Android/sdk/build-tools/36.0.0/dexdump`. Ghidra NOT needed (Java/DEX target).

## RE PHASE — COMPLETE (all verified from APK, see FINDINGS.md for full detail)
Hook targets (class `ie` in classes4.dex; MediaSession impl, base of `ih` runtime instance):
- `ie.m(MediaMetadataCompat)` setMetadata, regs=8 ins=2 → p0=v6,p1=v7,free v0-5. Track change.
- `ie.n(PlaybackStateCompat)` setPlaybackState, regs=11 ins=2 → p0=v9,p1=v10,free v0-8.
  **FIRES EVERY ~1s while playing** (azri.m() postDelayed(1000ms)→im.k→ie.n). THE TICK.
- `ie.q(List)` setQueue, regs=8 ins=2. Queue context.
- Hierarchy `ie`(m/n/q are public FINAL, not overridden)←C0002if←ig←ih(runtime,API29+).
  Hooking `ie.m/n/q` fires for all instances + cast session. ZERO revanced refs in all
  target classes (ie, azqc, azri, im, azqq) → no collision with existing patches.

Extraction (support-lib MEMBER names are obfuscated; class names + framework methods stable):
- `ie.m` p1 Bundle carries TITLE/ARTIST/DURATION (built by azri.k() from azqc.n/o/i, keys
  are stable `android.media.metadata.*`). Read via Bundle field (type Landroid/os/Bundle;,
  unique) + stable `Bundle.getCharSequence/getLong(key)`. Guard null p1.
- `ie.n` position+state: read iget/iget-wide feeding `PlaybackState$Builder.setState(IJFJ)`
  at |0032 (state=PlaybackStateCompat.a int at |002a, position=.b long at |002c), anchored by
  stable `MediaSession.setPlaybackState` at |0085. Walk operands (robust; no hardcoded a/b).
- PlaybackStateCompat fields: a=state,b=position,c=buffered,d=speed,e=actions,h=lastUpdate.

Ruled out: `aztd.bA` (YouTube "Media progress" string exists) is NOT periodic in music
(driver ayxn.E posts once) → not the tick. azqc.p/i/m/l setters ARE called (azqq bridge:
p@254, i@279/281/316, m@314, l@178/332/337) but need const-flag fingerprints — BACKUP plan.

## PATTERNS READ (to mirror when writing code)
- Extension = static-method class, `@SuppressWarnings("unused")`, gated on `Settings.X.get()`.
  Template: `extensions/music/.../patches/HideVideoAdsPatch.java`.
- Settings: add `BooleanSetting("revanced_music_...", default, true)` to
  `extensions/music/.../settings/Settings.java` under a section (ADS/GENERAL/PLAYER/MISC).
- Patch: `bytecodePatch(name=..., description=...) { dependsOn(sharedExtensionPatch,
  settingsPatch, addResourcesPatch); compatibleWith("com.google.android.apps.youtube.music"
  ("7.29.52","8.10.52","8.37.56","8.40.54")); apply { addResources("music", <key>);
  PreferenceScreen.<SCREEN>.addPreferences(SwitchPreference(<key>)); navigate(...).to(...).
  addInstructions(...) } }`. Template: `patches/.../music/ad/video/HideVideoAds.kt`.
- PreferenceScreen object in `patches/.../music/misc/settings/SettingsPatch.kt`
  (screens: ADS, GENERAL, PLAYER, MISC). addResources key format like "ad.video.hideVideoAdsPatch".
- Strings: `patches/src/main/resources/addresources/values/strings.xml` (revanced_music_* keys).
- Fingerprint DSL: `BytecodePatchContext.x by composingFirstMethod("string") { opcodes(...) }`
  OR structural match on `returnType`, `parameters`, `accessFlags`, `strings` +
  `firstMethodDeclaratively` + `getReference<MethodReference>()` to confirm setMetadata/
  setPlaybackState reference and to walk setState operands. (Confirmed supports what we need.)

## DESIGN DECISION (LOCKED)
Hook `ie.m` + `ie.n`; pass PRIMITIVES/String/CharSequence to new extension
`app.revanced.extension.music.stats.*` (no support-lib types cross boundary, matches
existing pattern). videoId optional/later (not in MediaSession metadata; hook aztd.ic/bi later).
Settings toggle in PreferenceScreen.MISC. No new manifest activity (root-install breakage).

## NEXT STEPS (write code — RE is done)
1. Extension `extensions/music/src/main/java/app/revanced/extension/music/stats/`:
   `ListeningDatabase.java` (sqlite: tracks, listen_events, daily_stats, hourly_stats;
   WAL, batched writes off main thread via Utils.runOnBackgroundThread/submitOnBackgroundThread),
   `SessionTracker.java` (start/pause/end sessions from m+n hooks), `ContextCollector.java`,
   `WrapperReport.java` (queries). Add Settings field.
2. Patch `patches/src/main/kotlin/app/revanced/patches/music/stats/`:
   `ListeningLoggerPatch.kt` + `Fingerprints.kt`. Fingerprints: locate `ie.m`
   (MediaMetadataCompat param + V + setMetadata ref) and `ie.n` (PlaybackStateCompat param +
   V + setPlaybackState ref); walk setState operands for state/position field refs.
   Insert `invoke-static {p1,...}` at method start (index 0).
3. Strings in addresources/values/strings.xml (revanced_music_stats_* keys).
4. Build `cd revanced-patches && ./gradlew` (may need GitHub Packages creds for
   maven.pkg.github.com/revanced — fallback: local build / ask user).
5. **NEED STOCK YT Music 8.40.54 APK to test** (user only has PATCHED build; patcher needs
   original) → ASK USER for stock APK or download from APKMirror. ACTION ITEM.
6. Patch with CLI + full default patch set; install; verify DB writes + all other patches
   still work (adblock, theme, spoof, etc.).

## OPEN RISKS
- Stock 8.40.54 APK not in workspace (only patched) — blocking for testing (step 5).
- ie.m/ie.n fingerprints not yet written/tested (structural match confirmed feasible).
- Register-safe insertion at index 0 confirmed free regs (v0-5 for m, v0-8 for n).
- GitHub Packages auth may block build.

## REPO STRUCTURE (UPDATED — development now lives here)
Our own git repo: `/Users/yogesh/Desktop/Wraped/wrapped-patch/` (git-tracked, clean).
```
wrapped-patch/
├── .gitignore                  # excludes *.apk, decompiled/, *.dump, build/, .DS_Store
├── STATUS.md                   # this file (resume point)
├── FINDINGS.md                 # full verified RE findings
├── patches/src/main/kotlin/app/revanced/patches/music/stats/
│   ├── Fingerprints.kt         # DONE (locates ie.m / ie.n via setMetadata/setPlaybackState)
│   └── ListeningLoggerPatch.kt # TODO (inject smali hooks + settings toggle)
├── extensions/music/src/main/java/app/revanced/extension/music/stats/
│   ├── ListeningDatabase.java  # DONE (sqlite: tracks, sessions, report queries)
│   └── SessionTracker.java     # DONE (onTrackInfo / onPlaybackState entry points)
└── addresources/values/strings.xml  # TODO (revanced_music_listening_stats strings)
```
To BUILD: copy these files into the upstream harness
`/Users/yogesh/Desktop/Wraped/revanced-patches/` (same relative paths) then
`cd revanced-patches && ./gradlew`. The upstream clone is NOT our repo (untouched).
NOTE: a STOCK YT Music 8.40.54 APK is now present in /Users/yogesh/Desktop/Wraped/
(`com.google.android.apps.youtube.music_8.40.54-...apkmirror.com.apk`) — needed for
patch testing (step 5 of NEXT STEPS). Also present: the PATCHED build (YT Music ReVanced.apk).
