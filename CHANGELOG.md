# Changelog

## [1.3.0] - Full version support and loot persistence fixes

> **Minecraft 1.21.1 servers: update.** Graves containing registry-backed item data such as
> enchantments could fail to save. This release uses the world's registry context when
> saving and loading them. Keep world backups; updating cannot restore data already lost.

### Added
- Fabric and NeoForge 26.3 and 26.1.2 (Java 25), Fabric and NeoForge 1.21.8, Fabric 1.21.1, and Fabric/Forge 1.20.1.
- One release version across all twelve targets, with automatic publishing after CI succeeds.
- Automated vanilla-client tests for death capture, enchanted loot, armor/offhand items, XP,
  partial GUI collection, full-inventory overflow, water/lava/void placement, keepInventory,
  cross-dimension access denial and repeated server restarts.

### Fixed
- Reject stale grave interactions from another dimension before awarding loot or XP.
- Minecraft 1.21.1 saves and loads item enchantments with the required registry access.
- Version-specific death hooks, interaction packets, container dirty notifications and Forge
  mixin mappings are adapted for each target.

### Notes
- Existing commands and configuration remain compatible. Test server output is disposable;
  it never opens an existing player world.

## [1.2.0] - NeoForge 1.21.1 support

### Added
- **NeoForge support for Minecraft 1.21.1**, alongside the existing Fabric/NeoForge
  1.21.11 targets. Same mod, same config, same commands — just an older Minecraft
  version for servers that haven't updated yet.

### Changed
- Repo layout is now a version-suffixed monorepo (`common-<version>`,
  `fabric-<version>`, `neoforge-<version>`) to support multiple Minecraft versions
  side by side. No behavior change for existing 1.21.11 installs.

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
