# Changelog

## [1.1.0] - Loot safety net

### Fixed
- **Quick-collect (sneak + right-click) could silently delete items.** Vanilla's own
  `Inventory#add` returns success as soon as it merges *any* portion of a stack into an
  already-partially-filled inventory — not only when the stack is fully consumed. The
  mod trusted that return value to decide whether to drop the leftover, so a partial
  merge (common with a nearly-full inventory) left the remainder undropped and it was
  then discarded when the grave slot was cleared. Quick-collect now checks the actual
  leftover count on the stack and drops it on the floor, every time.

### Added
- **Loot-backup safety net.** Every grave's original contents are now snapshotted the
  moment it's created, independent of the live grave and of the existing anti-grief
  rollback history — it survives normal collection, so a bug anywhere in the
  pickup/expiry path (like the one above) still leaves a recoverable copy.
  - New config: `lootBackupRetentionDays` (default `7`). Days to keep a backup;
    recommended presets are `1`, `3`, `7`, or `14`, but any value works. `0` disables
    the feature, `-1` keeps every backup forever.
  - New command: `/graves restore <player> [index]` (operator-only) — restores a grave
    from its backup. Unlike `/graves rollback`, backups aren't consumed on restore and
    may still exist even if the player already collected the original grave, so check
    before using it to avoid duplicating loot.

> **Update immediately if you've had reports of players losing items on quick-collect** —
> this release fixes the root cause and gives you a recovery path (`/graves restore`) for
> anything already lost, as far back as your `lootBackupRetentionDays` window reaches.

## [1.0.0] - Initial release

### Added
- Death chests as packet-only markers: a floating head, hologram text, and one of seven
  epitaphs — nothing ever written into world/chunk data.
- Sneak + right-click quick-collect, with armor routed straight into empty armor slots.
- Grave placement resolver for lava, deep water, and void deaths.
- Anti-grief rollback history (`/graves rollback`) for griefed or expired graves.
- `/graves`, `/graves tp`, `/graves list`, `/graves reload` command tree.
- Full config surface for timings, capture rules, geometry, and restrictions.
- Fabric and NeoForge support for Minecraft 1.21.11 from a single `common` codebase.
