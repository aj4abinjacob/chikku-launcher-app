# Contact card and communication services

Status: implemented on `feat/contact-connect` (2026-10-12), JVM-verified; not yet checked on a
device. Decision record: [ADR-0007](decisions/0007-contact-communication-services.md).

When contact search is on (opt-in, `READ_CONTACTS`), the best-matching contact appears first as a
**person card**: avatar, name and number, then one row of round buttons. By default these are
**Call**, **Message** (SMS) and **WhatsApp**. In Search › Contacts › **Contact card buttons** the
user can swap any of them for another supported action (for example Signal instead of Call), add a
fourth, remove or reorder them. A button this contact can't use is left out without an empty slot.

The first version (commit `a547e02`) put messengers in a separate **More ways to connect** panel
with per-service visibility/order settings. On device review the original one-row card was
preferred, so that panel and those settings were replaced before release.

## What is supported today

| Service | Package | Message | Voice call | Video call | Evidence |
|---|---|---|---|---|---|
| Phone | system dialer | — | **Call** opens the dialer prefilled (`ACTION_DIAL`, no `CALL_PHONE`) | — | Existing handoff, unchanged |
| SMS | default SMS app | **Message** opens the SMS composer (`ACTION_SENDTO smsto:`); nothing is sent | — | — | Existing handoff, unchanged |
| WhatsApp | `com.whatsapp` | **Message** opens the chat | Not offered | Not offered | Installed manifest, below |
| Signal | `org.thoughtcrime.securesms` | **Message** opens the chat | Not offered | Not offered | Source, below |
| Telegram | `org.telegram.messenger` | **Message** opens the chat | Not offered | Not offered | Installed manifest and source, below |
| Threema | `ch.threema.app` | **Message** opens the chat | **Threema call** starts a Threema call | None (no entry point) | Source, below |

WhatsApp, Signal and Telegram calls are absent because **those apps restrict their call entry points
to callers holding `android.permission.CALL_PHONE`**, which Chikku does not request for this
feature. Threema does not guard its call entry, so **Threema call** is offered: it is never on the
default card, appears only if the user adds it, and tapping it starts the call in Threema (Threema's
own checks apply; it shows "calls disabled" or "contact not found" itself). Nothing is faked by
opening a chat.

### Verified entry points

All entries use Android's Contacts-app convention: the app's sync adapter writes a
`ContactsContract.Data` row with its own mimetype for each contact it can reach; viewing that row
with `ACTION_VIEW` + the mimetype opens the app.

**WhatsApp 2.26.39.79** (versionCode 263907900), read on 2026-10-12 from the APK installed on a
physical Android 11 device (`dumpsys package` plus `aapt2 dump xmltree` of the manifest; read-only,
nothing was launched):

- `vnd.android.cursor.item/vnd.com.whatsapp.profile` → `com.whatsapp.accountsync.ProfileActivity`,
  exported, **no permission**. Opens the chat.
- `vnd.android.cursor.item/vnd.com.whatsapp.voip.call` and `…/vnd.com.whatsapp.video.call` →
  `com.whatsapp.accountsync.CallContactLandingActivity`, exported,
  **`android:permission="android.permission.CALL_PHONE"`**.

**Signal**, `signalapp/Signal-Android` at commit `92f5ccd3a52c4780a0c916566e26280f640ba1de`
(2026-10-08), `app/src/main/AndroidManifest.xml` and `res/xml/contactsformat.xml`:

- `vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact` →
  `SystemContactsEntrypointActivity`, exported, no permission. Resolves the row to a recipient and
  opens the conversation.
- `…securesms.call` and `…securesms.videocall` → `org.thoughtcrime.securesms.webrtc.VoiceCallShare`,
  exported, **`android:permission="android.permission.CALL_PHONE"`**.

**Telegram 13.0.2**, read on 2026-10-17 from the APK installed on a physical Android 16 Samsung
phone (`dumpsys package` plus `aapt2 dump xmltree`; read-only, nothing was launched), and
`DrKLO/Telegram` at commit `f32ffeec763f0bdbf4bec25b52d654f77bff733d` (2026-10-13),
`TMessagesProj/src/main/AndroidManifest.xml`, `res/xml/contacts.xml` and `ui/LaunchActivity.java`:

- `vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile` → `org.telegram.ui.LaunchActivity`,
  exported, no permission. Reads the row's `DATA4` (Telegram user id) and account name and opens the chat.
- `…android.call` and `…android.call.video` → the `org.telegram.ui.CallsActivity` alias, exported,
  **`android:permission="android.permission.CALL_PHONE"`**.

**Threema**, `threema-ch/threema-android` at commit `d44a2b53b2ea2c0bf1065bdaa96987570e5b4e6d`
(2026-09-23), checked 2026-10-17 (`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`,
`ContactUtil.getIdentityFromViewIntent`, `CallActionIntentActivity`, `AndroidContactUtil`); no
shipping build was available on the test phone:

- `vnd.android.cursor.item/vnd.ch.threema.app.profile` → `ch.threema.app.activities.ComposeMessageActivity`,
  exported, no permission. Reads the row's `DATA1` (Threema ID) and opens that conversation.
- `vnd.android.cursor.item/vnd.ch.threema.app.call` → `ch.threema.app.voip.activities.CallActionIntentActivity`,
  exported, **no permission**. Reads `DATA1` and starts a call (`VoipUtil.initiateCall`). Threema
  writes call rows only while its calls are enabled.
- No video-call mimetype.
- The mimetype is built from the application id in `defaultConfig`, so the Google Play and Threema
  Shop builds (`ch.threema.app`) use the values above. **Threema Libre** (`ch.threema.app.libre`) and
  the **HMS** build (`ch.threema.app.hms`) inherit the same mimetypes under another package, and
  **Threema Work** uses `vnd.ch.threema.app.work.*`. Chikku maps each mimetype to one provider, so
  Libre and HMS are not supported; Work is not added yet.

Public web sources found no documentation of WhatsApp's mimetypes; the installed manifest is the
evidence. Both apps can change these at any update, which is why support is probed at runtime
rather than trusted from this table.

## Architecture

Four separate steps, none specific to one app:

1. **Provider registry** (`core/model` `CommunicationProviders`): an app-neutral
   `CommunicationProvider(id, packageName, displayName, entryPoints)`. Each `ProviderEntryPoint`
   maps one data-row mimetype to a `ContactActionType` (`MESSAGE`, `VOICE_CALL`, `VIDEO_CALL`). The
   `id` is stable and persisted.
2. **Capability discovery** (`platform/ContactActionPlatform.kt`, `AndroidContactEntryPointProbe`):
   an entry point is offered only if `PackageManager.resolveActivity` (pinned to the provider's
   package, `MATCH_DEFAULT_ONLY`) finds exactly one exported, enabled activity whose required
   permission, if any, Chikku already holds. Probed per contact lookup, so app updates take effect
   without a restart.
3. **Contact action resolution** (`ContactsSearchSource`): for the ≤5 final hits only, one batched
   `ContactsContract.Data` query reads the primary phone number and the rows of *supported*
   mimetypes. A row is the provider's own evidence that it synced this contact; an installed app
   alone never produces a button. Unsupported mimetypes are not even queried.
4. **Selection and ordering** (`ContactServices`, pure Kotlin): a provider appears only if it has a
   visible launcher activity in the **personal** profile, is not hidden in the launcher, and is not
   turned off in Search settings; order is the user's, then built-in. `SearchViewModel` and HOME's
   overlay strip actions of any other provider before the UI sees them.
5. **Rendering** (`search/PersonCard.kt`): `ContactServices.resolve` turns the user's
   `ContactCardButton` list into the buttons this contact can use; one generic button per entry,
   showing the installed app's own label and its own monochrome glyph (the adaptive icon's
   `<monochrome>` layer, Android 13+, read unthemed through `IconProvider.monochromeIcon`), tinted
   like the Call and Message glyphs. Without one (older Android, or an app that ships none) the
   button shows a generic chat glyph. No brand assets are bundled. On 2026-10-17 the shipping
   WhatsApp 2.26.39.79 and Telegram 13.0.2 APKs and Signal's and Threema's sources (Signal commit
   `e9b569edd6b64f24d2a782deb60ac88b70471ded`) all declared a monochrome layer.
6. **Launching** (`ContactActionLauncher`, after an explicit tap only): off the main thread it
   re-reads the data row and requires the same mimetype and the same contact (id, or lookup key after
   a join), re-checks `READ_CONTACTS` and the entry point; then on the main thread asks Chikku's app
   lock for the provider package and starts the pinned intent. Results are typed:
   `Started`, `Locked` (unlock prompt, then retry), `PermissionMissing`, `ContactChanged`,
   `Unavailable` (app removed/disabled, support lost, launch rejected). Each failure shows a
   snackbar; nothing is retried silently.

### Adding a provider

1. Verify the app's entry points on a shipping build (manifest: exported, permission, mimetype) and
   record the version/revision here.
2. Add a `CommunicationProvider` to `CommunicationProviders.KNOWN` with a new, permanent `id`
   (not `phone` or `sms`, which name the built-in Call and Message buttons).
3. If the package has no launcher activity, add it to the manifest `<queries>` (state the reason).
4. Add a row to the table above and a fixture to `ContactServicesTest`.

Nothing else changes: UI, settings, backup and restore checks are keyed by `id`.

Candidates not shipped (checked 2026-10-17):

- **Threema Libre / HMS / Work**: see Threema above.
- **WhatsApp Business** (`com.whatsapp.w4b`), **Viber** (`com.viber.voip`): closed source and not
  installed on the test phone; no primary evidence of their mimetypes.
- **Google Meet** (`com.google.android.apps.tachyon`, installed build
  379.0.989469524): every contact entry (`…tachyon.phone`, `.audio`, `.meet`, `.email.meet` …) is a
  call and requires `CALL_PHONE`, so there is nothing Chikku can offer.
- **Zoom** (`us.zoom.videomeetings` 7.1.8): `…videomeetings.call`/`.text` → `JoinByURLActivity`,
  exported, no permission, but what it does with a contact row is unknown; not added without
  evidence.

### Proposed: a versioned, app-declared contract (not implemented)

Today Chikku needs a registry entry per app. A cooperating app could instead declare its actions,
for example an exported activity with
`<meta-data android:name="io.github.aj4abinjacob.chikku.contact_actions" android:resource="@xml/…"/>`
listing `version`, mimetype → action type (`message`/`voice_call`/`video_call`), and a statement
that the activity accepts the call from any app without `CALL_PHONE`. Chikku would discover it with
a `<queries>` intent, validate it exactly like step 2, and treat unknown versions as unsupported.
A generic alternative is reading each sync adapter's `android.provider.CONTACTS_STRUCTURE`
`ContactsDataKind` list, but it carries no action type, so a label would have to be the vague
"Open in …". Neither is implemented; there is no discovery of undeclared apps.

## Settings, backup and restore

Search › Contacts shows a live preview of the real card at the top (pinned above the settings,
sample contact and generic icons only, never real contacts or installed-app icons); it follows every
change below immediately. **Contact card buttons** (shown when contact search is on) lists the chosen
buttons in order with move earlier/later and remove (at least one stays), **Add a button** with
every action this phone supports (Call, Text message, and each installed app's supported actions;
today WhatsApp/Signal/Telegram/Threema message and Threema call), and **Reset to Call, Message, WhatsApp**. A chosen app action that
is not installed or no longer supported stays in the list with a note and is skipped on cards.

Stored as `SearchSettings.contactCardButtons: List<String>` of ids `"<provider>:<action>"`
(`phone:voice_call`, `sms:message`, `whatsapp:message`, …), default
`[phone:voice_call, sms:message, whatsapp:message]`. Written by `LauncherSettingsCodec`; old backups
decode to the default; malformed or duplicate ids are dropped, unknown provider ids are kept, at most
4; an empty or unusable list falls back to the default. `RestoreChecks.contactServices` raises
`CONTACT_SERVICE_APP_MISSING` (no package names) only when contact search is on, the list differs
from the default, and a chosen app is not installed; it links to Search settings and never edits
the list.

## Layout and accessibility

The card keeps the original look: a row of 44dp round icons with labels under them, each button an
equal share of the row (up to 4). Labels use `sp` and wrap instead of being truncated; each button
is one ≥48dp-tall target with `Role.Button` and one TalkBack description naming the action, app and
contact ("Open WhatsApp chat with Abin", "Call Abin in your phone app"); icons and visible labels are
excluded from semantics so nothing is read twice. App call buttons (today only Threema's)
carry a small call/video mark on the app glyph and say "Threema call" (or "<App> video"). Tapping the
name opens the contact; there is no overflow menu. The card has a stable key so focus survives
result refreshes; the results list is the only scroll container.

`PersonCardLayoutTest` renders the card with native text measurement and checks Compose's merged
semantics tree with four buttons at 320/360/412dp × 1.0/1.3/1.5/2.0 font scale, RTL and long names:
no overlap, no clipped or ellipsized text, ≥48dp targets, correct labels and order.

## Privacy

No new permission, dependency or exported component. Contact rows are read only for the final hits
of a query the user typed with contact search enabled; nothing is stored, logged, broadcast or sent
to an installed app before a tap; no registration service is queried. Only the personal profile's
contacts and apps are used. A provider app hidden in Chikku never appears; a locked one asks for
unlock before opening.

## Verification and remaining gaps

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md#contact-card-and-communication-services-2026-10-12).
Not verified: a device run of the new card, real TalkBack, font/display-size changes on a device,
and actual WhatsApp/Signal launches (no calls were placed and no messages sent during this work).
