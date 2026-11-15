# Fiw Graves

*A place for your things when you're gone.*

When you die, your items and XP settle into a grave: a floating head, a few lines of
floating text, and one of seven dry epitaphs. Walk back, crack it open, move on with
your life.

Built for Minecraft **26.1.2, 1.21.11, 1.21.8, 1.21.1 and 1.20.1**, with one release
version across every supported loader.

| Minecraft | Loaders | Java |
|---|---|---|
| 26.1.2 | Fabric | 25 |
| 1.21.11 | Fabric, NeoForge | 21 |
| 1.21.8 | Fabric, NeoForge | 21 |
| 1.21.1 | Fabric, NeoForge | 21 |
| 1.20.1 | Fabric, Forge | 17 |

## Why it's different

**The graves aren't really there.** The head and text you see are pure network
packets — no armor stand, no display entity, nothing is ever written into the world
or its chunks. If the server crashes, gets rolled back, or the mod is removed,
there is *nothing* to leak: no floating heads, no orphaned holograms, no ghost
entities to `/kill`. The save file is the single source of truth, and markers are
re-synthesized from it on every boot.

**Vanilla clients welcome.** Because everything is protocol-level, players connect
with a completely unmodified client. Install the jar on the server, done.

**Griefed? Rolled back.** Every grave that expires or gets emptied by someone else
is kept in a short per-player history. An operator can `/graves rollback <player>`
to respawn their lost loot as a fresh grave — a safety net most grave mods don't have.

**World-native persistence.** Grave data rides Minecraft's own `SavedData` pipeline,
so it saves, backs up, and restores with the world itself — not in some file bolted
on next to it.

## Using it

- **Open** — right-click the head to open the grave like a chest.
- **Quick-collect** — sneak + right-click to sweep everything into your inventory;
  armor jumps straight into empty armor slots.
- XP is returned the moment you interact.
- Die in lava, deep water, or the void and the grave settles at the nearest open,
  dry spot instead of drowning with you.
- Graves crumble after 10 minutes (configurable), dropping whatever's left.

### Commands

| Command | Who | Does |
|---|---|---|
| `/graves` | everyone | your graves + help |
| `/graves tp [index]` | ops (players via `playerGraveTeleport`) | warp to your latest (or nth) grave |
| `/graves list [player]` | ops for others | list active graves, click to warp |
| `/graves rollback <player> [index]` | ops | restore a lost grave from history |
| `/graves restore <player> [index]` | ops | restore from the loot-backup safety net (works even after the grave was collected) |
| `/graves reload` | ops | reload the config |

`/fiwgraves` works as an alias. Admin commands gate on vanilla operator level 2 —
no permission plugin required, none supported, none needed.

## Config

`config/fiwgraves/config.json` — gameplay tuning only (timings, geometry, capture
rules, restrictions). All player-facing text lives in code, not in a message file.

Highlights:

| Key | Default | |
|---|---|---|
| `despawnSeconds` | `600` | grave lifetime, `-1` = forever |
| `quickCollectEnabled` | `true` | sneak-interact sweep |
| `ownerOnlyAccess` | `false` | lock graves to their owner |
| `playerGraveTeleport` | `false` | let non-ops use `/graves tp` |
| `graveLimitPerPlayer` | `-1` | oldest grave crumbles past the cap |
| `rollbackHistoryPerPlayer` | `5` | recoverable graves per player |
| `lootBackupRetentionDays` | `7` | days a loot-backup safety-net copy is kept, independent of the grave itself; survives normal collection so a bug can't erase the only copy. `0` disables, `-1` forever |
| `storeExperience` / `storeItems` | `true` | what goes into the grave |
| `disabledDimensions` | `[]` | dimensions that never spawn graves |

## Building

```
./gradlew build
```

Build with JDK 25 available (older modules use Java 17/21 toolchains).
All nine release jars land in `<module>/build/libs`. The project is a version-suffixed multiloader monorepo with
official Mojang mappings — each `common-<version>` module compiles once against that
Minecraft version, and each loader module (`fabric-<version>`, `neoforge-<version>`)
adds only its hooks (NeoForge uses native events; Fabric carries the project's single
gameplay mixin).

## Automated verification and releases

`./gradlew playerTest -PsmokeTarget=fabric-26.1.2` boots the production jar in a disposable
`build/smoke/` world and connects a scripted vanilla client. The client checks enchanted items,
armor, offhand loot, XP, a visible grave, partial GUI withdrawal, a full-inventory quick collect,
and repeated saves/restarts. Node 22+ is required; `installPlayerTestTools` installs pinned test
dependencies. These are development tools, never runtime mod dependencies.

GitHub Actions builds and tests every target. A successful push to `main` with a new root
`version` automatically publishes all nine jars to GitHub and Modrinth and syncs the store
page. The tag workflow remains a recovery path. Keep the matching changelog section in the
same commit as a version bump. Logs and save snapshots are retained under `build/smoke/`.
