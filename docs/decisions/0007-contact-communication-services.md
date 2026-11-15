# ADR-0007: Contact card communication services

- **Status:** proposed; implemented on `feat/contact-connect`, awaiting device validation and orchestrator review
- **Date:** 2026-10-12
- **Scope:** search person card; does not change ADR-0001/0002/0003

## Context

The person card had a WhatsApp-only button backed by a `whatsAppDataId` field. A redesign asked for
Message, Voice call and Video call per messenger (WhatsApp, Signal, future apps). Inspection of the
shipping apps ([evidence](../CONTACT_SERVICES.md#verified-entry-points)) showed both guard their
call entry points with `android.permission.CALL_PHONE`; only chat entry points are open.

## Decision

- Model communication apps as data: `CommunicationProvider` (stable id, package, entry points
  mapping a contacts data-row mimetype to `MESSAGE`/`VOICE_CALL`/`VIDEO_CALL`). No per-app UI,
  columns or settings fields.
- Offer an entry point only when runtime resolution finds a single exported, enabled activity in
  that package that needs no permission Chikku lacks. Do **not** request `CALL_PHONE`. Never show a
  call button that opens a chat.
- Require the provider's own synced data row per contact; installation alone shows nothing.
- Only personal-profile, non-hidden provider apps; app lock applies at launch.
- Re-validate the row, contact and capability off the main thread after the tap; report typed results.
- Keep the original one-row card. Persist the ordered button list (`contactCardButtons`, ids like
  `whatsapp:message`, default Call, Message, WhatsApp, max 4) in `SearchSettings`, backed up, with a
  restore check when a changed list names an absent app. (A first version used a separate services
  panel; it was replaced after device review.)

## Consequences

Today WhatsApp and Signal can be card buttons for **Message** only. Call buttons appear without code changes if an app
drops the guard. Each new provider needs verified entry points and a registry entry; a
cooperating-app declaration contract is proposed, not implemented.
