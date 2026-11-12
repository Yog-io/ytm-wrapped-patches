# RE FINDINGS — YT Music "Wrapped" patch (verified from APK, no guessing)

All facts below were pulled directly from the user's patched YT Music 8.40.54 APK
via jadx (decompiled Java) + dexdump (bytecode/registers). Analysis scratch:
`/private/var/folders/jw/b3rw7d515n5_3hpjgngmc2f80000gn/T/opencode/ytm/`
  - `decompiled/sources/` — jadx output (30104 classes)
  - `classes4.dump` — dexdump -d of classes4.dex (117 MB; `ie` lives here)
Tool note: Ghidra NOT needed (Java/DEX target; jadx + dexdump are correct).

---

## 1. MediaSession impl class hierarchy (CONFIRMED)
```
ie  implements ic            <- defines m/n/q as PUBLIC FINAL (base impl)
 └─ C0002if extends ie       (API < 28)
     └─ ig extends C0002if   (API 28)
         └─ ih extends ig    (API 29+, the runtime instance on modern phones)
```
- `im` (public final, MediaSessionCompat wrapper) constructs `this.b`:
  API>=29 -> `new ih(ctx,tag)`, API 28 -> `ig`, else `C0002if` (im.java:25-29).
- `im.j(MediaMetadataCompat)` -> `this.b.m(...)`  (im.java:100)
- `im.k(PlaybackStateCompat)` -> `this.b.n(...)`  (im.java:104)
- `ie.m/n/q` are `public final` and are NOT overridden by C0002if/ig/ih
  (grep confirmed no override) -> hooking `ie.m/n/q` fires for ALL instances.
- `im` also used for Cast session ("CastMediaSession", sav.java:335) ->
  hooking `ie.*` captures both local + cast.

## 2. Hook target methods — genuine signatures & register layout (dexdump)
All in class `Lie;` (classes4.dex).

### `ie.m(MediaMetadataCompat)`  = setMetadata (track identity)
- signature `(Landroid/support/v4/media/MediaMetadataCompat;)V`
- access: PUBLIC FINAL   |  #12 in class
- **registers = 8, ins = 2**  ->  p0(this)=v6, p1(metadata)=v7, **FREE v0..v5**
- insns size = 172 units; first instr `|0000: iput-object v7, v6, Lie;.h:...`
  (stores incoming metadata to field h) then `|0002: if-nez v7, ...` null check.
- Reads Bundle at `|0010: iget-object v1, v7, MediaMetadataCompat;.b:Landroid/os/Bundle;`
  -> **Bundle field = `b`** (type `Landroid/os/Bundle;`, unique in MediaMetadataCompat).
- Null-safe: metadata may be null (session clear) -> guard `if-eqz p1, skip`.

### `ie.n(PlaybackStateCompat)`  = setPlaybackState (tick / state / position)
- signature `(Landroid/support/v4/media/session/PlaybackStateCompat;)V`
- access: PUBLIC FINAL   |  #13 in class
- **registers = 11, ins = 2** ->  p0(this)=v9, p1(state)=v10, **FREE v0..v8**
- insns size = 141 units.
- Genuine reads feeding `PlaybackState$Builder.setState(IJFJ)`:
  - `|002a: iget v3, v10, PlaybackStateCompat;.a:I`     -> **STATE (int)**
  - `|002c: iget-wide v4, v10, PlaybackStateCompat;.b:J`  -> **POSITION (long)**
  - `|002e: iget v6, v10, PlaybackStateCompat;.d:F`      -> speed (float)
  - `|0030: iget-wide v7, v10, PlaybackStateCompat;.h:J`  -> lastUpdateTime (long)
  - `|0032: invoke-virtual/range {v2..v8}, PlaybackState$Builder.setState:(IJFJ)`
- **Stable anchor:** `|0085: invoke-virtual {v0,v10}, MediaSession.setPlaybackState:(...)V`
  -> robust fingerprint (param PlaybackStateCompat + calls setPlaybackState).
- Other PlaybackStateCompat fields (obfuscated names, for reference):
  a=state(I), b=position(J), c=buffered(J), d=speed(F), e=actions(J),
  f=errorCode(I), g=errorMessage(CS), h=lastUpdateTime(J), i=customActions(List),
  j=activeQueueId(J), k=extras(Bundle), l=cached framework PlaybackState.

### `ie.q(List)` = setQueue  (queue context)
- signature `(Ljava/util/List;)V`, PUBLIC FINAL, registers=8, ins=2 (p1=list).

## 3. Call cadence (CONFIRMED — decides which hook is the tick)
- **`ie.n` (setPlaybackState) fires EVERY ~1s while playing.**
  Path: `azri.m()` private (azri.java:121) does
  `handler.postDelayed(runnable=p, 1000L)` when `this.n`(playing)==true;
  runnable `p` (azri.java:30) calls `imVar.k(state)` -> `ie.n(...)`.
  `azri.e(boolean)` (line 170) sets playing + reschedules.
- **`ie.m` (setMetadata) fires on track/metadata change** (not periodic).
- `aztd.bA` ("Media progress reported outside media playback") is NOT a reliable
  periodic tick in music: its only callers are `aI` (on load, aztd:2060) and
  `I()` (aztd:1618), and `I()` is driven by `ayxn.E` which is `post()` ONCE
  (ayxn.java:560), not scheduled repeatedly. => do NOT use bA as the 1s tick.

## 4. azqc state-holder setters (CONFIRMED called; primitives as params)
`azqc` = all now-playing state (fields: n=title, o=artist, i=duration, j=position,
b=state, m=speed, r=art, t=audioRoute, h=repeatMode ...). Its setters take
PRIMITIVES/CharSequence directly (clean for extension — no field reads):

| setter | params | sets | notify flag | genuine call site |
|--------|--------|------|-------------|-------------------|
| `p(CharSequence,CharSequence)` | title, artist | n, o | b(32) | azqq.java:254 `this.j.p(charSequenceB, spanned)` |
| `i(long)` | duration ms | i | b(8)  | azqq:279,281 `Duration.ofSeconds(...).toMillis()`; azqq:316 `axbuVar.d`; mwi:427 |
| `m(long)` | position ms | j | b(16) | azqq:314 `azqcVar.m(axbuVar.a)` (axbu.a = progress position) |
| `l(int)`  | playback state | b | b(1)  | azqq:178 `this.j.l(8)`; azqq:332,337 `azqcVar.l(i)` |

- `azqq` is the central bridge (holds `azqc` as field `j`, azqq.java:17) that calls
  p/i/m/l. It also does some DIRECT field writes (azqq:159-161 `k=..; l=..; b(65536)`).
- `azqc.b(int)` (notify) ORs flag then iterates listeners `B` calling `azqb.eH(int)`;
  `azri implements azqb` -> state changes propagate to MediaSession.
- NOTE: `l(int)`/`i(long)`/`m(long)` signatures are common; fingerprinting them needs
  a discriminator (the `b(<flag>)` notify call with const 1/8/16, or the old!=new compare).

## 5. Metadata source for `ie.m` (CONFIRMED keys present)
- `azri.k()` (azri.java:75) builds a `MediaMetadataCompat$Builder` (obfuscated `gz`):
  - ARTIST      = `azqc.o`  (line 78)
  - ALBUM_ARTIST= `azqc.o`  (line 79)
  - TITLE       = `azqc.n`  (line 80)
  - DURATION    = `azqc.i`  (line 81, ms)
  - ALBUM_ART   = `azqc.r`  (bitmap, line 86)
- `k().a()` = build() -> `MediaMetadataCompat`, passed `im.j(...)` -> `ie.m(p1)`.
  => **`ie.m` p1 Bundle contains standard keys:**
     `android.media.metadata.TITLE` / `ARTIST` / `DURATION` (stable strings).

## 6. Support-lib member names are OBFUSCATED (critical for extraction)
- `MediaMetadataCompat`: class name stable; methods/fields obfuscated:
  `a(String)->long`(getLong), `d(String)->CharSequence`(getText),
  `e(String)->String`, field `b`=Bundle, field `c`=framework MediaMetadata,
  static `a`=key-type map. => read via **Bundle field `b` + stable
  `Bundle.getCharSequence/getLong(key)`** (framework methods, never obfuscated).
- `PlaybackStateCompat`: fields obfuscated (a=state, b=position, ...). To get
  state+position robustly across versions, **walk the `setState` operands** in `ie.n`
  (locate stable `PlaybackState$Builder.setState` invoke, read the `iget`/`iget-wide`
  feeding its arg1=state and arg2=position) instead of hardcoding `a`/`b`.

## 7. Co-existence baseline (reconfirmed)
- Target classes `ie`, `azqc`, `azri`, `im`, `azqq` contain **ZERO** `app.revanced.*`
  references (verified). Existing ReVanced hooks live in other files (MusicActivity,
  GoogleApiActivity, isz Application, aiml Cronet, obn watch-controller, azzi ads,
  whn litho, etc.). => our hooks do not collide.
- No new manifest activity (root-install breakage) -> report UI must reuse settings
  screen / WebView dialog / file export.

---

## DECISION (locked from these findings)
**Architecture: hook `ie.m` + `ie.n`** (MediaSession choke point, confirmed cadence,
robust location fingerprints via stable framework refs):
- `ie.m` -> extract TITLE/ARTIST/DURATION from p1 Bundle (stable getCharSequence/getLong
  + stable key strings). Start/identify a listen session.
- `ie.n` -> extract POSITION + STATE from p1 via setState operand-walk (robust).
  Fires every 1s -> accurate listen-duration, pause/seek detection.
- videoId: optional/enhancement (not in MediaSession metadata; hook a play-entry like
  `aztd.ic(aobl,String)`/`bi(String,aobl)` later if wanted).
- All smali passes PRIMITIVES/CharSequence/String to extension
  (`app.revanced.extension.music.stats.*`) — no support-lib types cross the boundary
  (matches existing pattern e.g. `HideVideoAds(Z)`).

Alternative (if `ie.*` extraction proves fragile): hook `azqc.p/i/m/l` (primitives in
params -> trivial extraction) but needs 4 const-flag fingerprints.

## NEXT STEPS
1. Write extension: `extensions/music/.../stats/` (ListeningDatabase sqlite,
   SessionTracker, ContextCollector, WrapperReport) + Settings field.
2. Write patch: `patches/.../music/stats/{ListeningLoggerPatch.kt, Fingerprints.kt}`.
   - fingerprint `ie.m` (MediaMetadataCompat param + calls setMetadata)
   - fingerprint `ie.n` (PlaybackStateCompat param + calls setPlaybackState), then
     walk setState operands for state/position field refs.
   - insert invoke-static at method start (index 0) using p1 (+ free v0..).
3. Strings: `addresources/values/strings.xml` (revanced_music_stats_*).
4. Build: `cd revanced-patches && ./gradlew` (may need GitHub Packages creds).
5. Need STOCK YT Music 8.40.54 APK to test (user only has PATCHED build) -> ask user.
6. Patch with CLI + default patch set; verify DB writes + all other patches still work.
