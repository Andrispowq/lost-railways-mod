# Lost Railways — Minecraft Fabric Mod Specification

**Document version:** 1.0  
**Status:** Proposed implementation specification  
**Target:** Minecraft Java Edition 26.3 (stable as of 2026-10-28)  
**Loader:** Fabric  
**Mod ID:** `lost_railways`  
**Initial mod version:** `0.1.0`  
**Audience:** Developers, technical designers, AI coding agents

## 1. Objective and product principles

Implement a vanilla-style, configurable transport and exploration mod that (1) makes minecarts useful for repeat travel, (2) generates sparse but logically interconnected abandoned rail infrastructure that players can repair, and (3) optionally adjusts sustained rocket-powered Elytra flight without destroying gliding. The experience must create emergent goals rather than quests.

**Core principles:**
- **Discover, restore, reuse.** The reward for finding a line is the lasting utility of repairing it.
- **Latent connectivity, incomplete visibility.** Most track remnants belong to one coherent underlying network, but may appear disconnected because tracks are missing, buried or obscured.
- **Wilderness remains wilderness.** No universal coverage. Mountain peaks, dramatic caves, deep underground biomes, and other sensitive landscapes remain largely unaffected.
- **Biome-driven character.** Plains routes are relatively visible/intact, forests are overgrown, deserts and jungles mostly host through-lines without stations except at appropriate settlements.
- **Player choice.** No forced tasks, cutscenes, custom progression requirements, or mandatory overhaul of existing worlds.
- **Vanilla-first.** Reuse vanilla blocks, rails, carts, structures and redstone. Add custom blocks/entities only where strictly needed.

## 2. Technical baseline

| Concern | Requirement |
|---|---|
| Game | Minecraft Java 26.3; do not target snapshots |
| Java | JDK 25 or higher, per target game runtime |
| Mod loader | Fabric; initial baseline Loader 0.19.5; verify stable versions when initializing |
| Fabric API | Compatible 26.3 build; resolve/pin an actually published version rather than relying on an example |
| Build | Gradle + Fabric Loom, with version-compatible Gradle/Loom release pinned in version catalog or properties |
| Source language | Java |
| Mapping/API | Follow official 26.3 Fabric guidance and current unobfuscated/named game APIs; verify symbol names against installed sources |
| Sides | Works in single-player and dedicated Fabric servers; server is authoritative |
| Data | Datapack-friendly tags/configured structures where feasible; persistent deterministic routing state only when necessary |
| Distribution | Standard Fabric mod JAR; Fabric API declared dependency |

Loader popularity is not assumed universal across all historical versions; Fabric is chosen specifically for target 26.3. Do not hardcode historical Yarn class or method names into implementation based only on this document.

## 3. Scope and priorities

Priority nomenclature: **P0** = initial playable release, **P1** = next complete release, **P2** = optional expansion.

| Feature | Priority |
|---|---|
| Configurable improved minecart acceleration/top speed | P0 |
| Sparse surface railway networks with deterministic connectivity | P0 |
| Procedural damage / missing track sections | P0 |
| Plains and forest styling; basic terrain avoidance | P0 |
| Local stations/junctions and repairable routes | P0 |
| Server config, generation toggle, tests/debug visualization | P0 |
| Underground mining branches and terrain-aware bridges/tunnels | P1 |
| Desert/jungle/taiga/swamp styles and biome-specific rules | P1 |
| Regional long-distance corridors; stronger cross-region graph | P1 |
| Optional Elytra rocket-flight endurance balancing | P1 |
| Rare stronghold-adjacent railway branches | P2 |
| Found railway route diagrams / incomplete station maps | P2 |
| Improved furnace minecarts / linked cargo convoys | P2 |
| New station decorations or specialty rails | P2 only if essential |

**Explicit non-goals:** fully simulated train engines, schedules, signals, fuel economies, compulsory rail upkeep, automatic quest markers, total Elytra replacement, direct portal-room delivery, large universal rail coverage, retroactive rewriting of player-built terrain.

## 4. Rail transport mechanics

### 4.1 Speed and acceleration

Implement increased **maximum attainable speed**, not an instant jump to top speed. Actual travel must respect cart acceleration, curves, collisions, unpowered rails, slopes and powered-rail availability.

| Rail behavior | Proposed default | Configurable |
|---|---:|---|
| Ordinary rail cruising speed cap | 16 blocks/s | Yes |
| Powered rail target maximum | 28 blocks/s | Yes |
| Fully sustained powered route cap | 35 blocks/s | Yes |
| Existing vanilla behavior | Available with speed overhaul disabled | Yes |

The distinction between powered and fully sustained routes should be implemented via **consecutive powered-rail acceleration and momentum**, not by introducing a mandatory new track type. A continuous adequately powered route can reach 35 blocks/s; sparse powered sections need not. Exact acceleration constants must be playtested, not inferred solely from speed caps.

**Safety/compatibility:**
- Server controls speed and movement. Client interpolation must remain smooth.
- Ensure high-velocity cart motion correctly detects tracks, curves, transitions, obstacles and entities; avoid tunneling/desync, especially at chunk borders.
- Handling corners, inclines and collisions must be safe and consistent; apply appropriate speed reductions if necessary.
- Vanilla redstone powering and minecart interactions must continue working.
- Existing player-made tracks receive the improved physics when this option is on; generated railways have no privileged physics.
- Rails cannot require a custom block to be fast; repair must work with familiar vanilla materials.
- If target-version vanilla experimental minecart movement already offers similar functionality, prefer integrating or adapting it rather than duplicating a physics engine.

### 4.2 Cargo

Preserve vanilla chest/hopper minecarts and normal automation. High-speed entity and container handling must not duplicate or lose contents. Multi-cart locomotive systems are optional P2 and must not block base implementation.

## 5. Procedural network model

### 5.1 Generate a logical graph before placing rail blocks

Represent regional networks as a deterministic graph `G=(V,E)` derived from world seed, dimension, configurable region size and stable feature salt:

- **Nodes:** stations, junctions, settlements, mines, portals to underground branches, optional network endpoints.
- **Edges:** latent intended corridors or local branches between nodes.
- **Physical rail:** a discontinuous and damaged realization of graph edges.
- **Connectivity invariant:** at least 90% of generated route segments must map to an edge connected to a local or larger graph; exceptions are explicit stubs/ruins. The **logical** network can be connected even when physical track sections are missing.
- Each generated local segment should generally connect to at least one other route/node at the graph level, even when that connection is not immediately visible.
- Avoid forced global connectivity across every continent, ocean or inaccessible terrain. Use multiple connected regional components where appropriate.

### 5.2 Region-scale planning

Use deterministic, bounded regional planning, preferably world-seed-keyed macro-cells with stable neighbor-edge negotiation:

1. Determine biome/terrain suitability and exclusion masks.
2. Select a small set of candidate nodes anchored to plausible points of interest or appropriate terrain.
3. Connect nodes with terrain-weighted pathfinding and proximity/graph constraints.
4. Add optional branches and rare longer connectors between neighboring regions.
5. Derive physical segment templates and damage mask from fixed seed+edge IDs.
6. Materialize placements only when relevant chunks generate, with no dependency on chunk generation order.

**Critical correctness requirements:** identical seed/config produces identical placement independent of traversal order and thread timing; crossing chunk borders must not leave discontinuities except intentionally generated damage. Do not synchronously generate or load faraway chunks to plan or build an entire line. Never use nondeterministic global RNG or mutate other loaded chunks from generation callbacks.

### 5.3 Distribution targets (tunable, not guaranteed per world)

These are **initial balance hypotheses** to calibrate through seed sampling; frequencies refer to railway-friendly terrain, not uniform Euclidean map coverage:

| Feature | Suggested spacing/size |
|---|---|
| Short fragment | ~100–400 blocks long |
| Typical local rail segment | ~600–1,200 blocks between encounters in suitable terrain |
| Local junction or minor station | ~1,000–2,000 blocks between candidates |
| Regional station | ~2,500–4,000 blocks between candidates |
| Major corridor | Rare, potentially 2,000–5,000+ blocks across connected regions |
| Underground branch | Uncommon; generally anchored to a surface line |

Density should be **clustered** rather than perfectly uniform: a large uninhabited mountain region might have no rails; adjacent plains can have several lines and a junction. The player should be able to choose to settle near a line without rails becoming common visual clutter.

### 5.4 Route integrity and damage

Apply damage *after* generating continuous route intent:

- Missing individual rails and longer gaps; collapsed/unfinished bridges; obstructed tunnels; landslides; vegetated or partially buried tracks.
- Some routes near villages or in plains are nearly usable; other routes require significant repair.
- At least one substantial subset of generated segments should be practically repairable without extensive terraforming.
- Avoid excessive destruction that obliterates the clue that a railway once existed.
- Do not leave automatically powered rails or functional transportation everywhere; player restoration and powering must meaningfully improve routes.
- Damage is deterministic and biome-/feature-dependent.

## 6. Biome policy

Use configurable biome tags and a data-driven `biome_policy` profile, not hardcoded checks only. Suggested defaults:

| Environment | Placement and character | Stations |
|---|---|---|
| Plains / gentle open terrain | Primary network backbone; most visible and intact tracks | Allowed; higher frequency |
| Forest / birch forest | Overgrown, interrupted, obscured by trees and leaf cover | Occasional small stations |
| Taiga | Rustic spruce-oriented support style, scattered damage | Occasional |
| Desert | Sparse transit corridors; wind-buried track and sandstone supports | Only at appropriate existing settlement/POI |
| Jungle | Very rare transit routes near passable edges; deeply overgrown | Only at appropriate existing settlement/POI |
| Swamp | Avoid core wetlands; rare raised stretches on suitable edges | Very rare |
| High hills / mountains / sharp peaks | Strongly penalized or excluded; do not cut through dominant landforms | No new isolated stations |
| Oceans | No routine long rail bridges; exceptional crossings only if explicitly enabled | No |
| Deep caves / underground special biomes | Protected from intrusive route generation | No |

Biomes should influence both **whether** a route is attempted and **how** its intact remnants look. Avoid gratuitously replacing natural terrain and preserve dramatic sightlines.

## 7. Underground railways

### 7.1 General rules

- Local railways may descend gently from surface routes to become natural mine entrances.
- Underground branches may connect to existing caves, ravines, abandoned mineshafts or mining-related landmarks.
- Exits, stations, entrances and bridges must appear plausible and use biome/underground-appropriate vanilla materials.
- Underground segments should be uncommon and discoverable through natural surface exploration, not cover the entire cave system.
- Preserve cave openness and major formations; route around or terminate rather than carving through huge caverns or underground biomes.

### 7.2 Depth constraint

Default: prohibit new railway placement below `Y=12`, configurable; prefer endpoints at `Y=12..20`. This avoids generation in the deepslate transition and deeper world. Minecraft's stone-to-deepslate transition is gradual; `Y=12` is a conservative gameplay rule, not a claim that all deepslate begins exactly there. Optionally also reject replacement of deepslate-tagged material regardless of elevation.

Never cut across ancient cities, deep dark, or designated underground-biome protection masks. If a destination would violate depth/biome constraints, terminate the branch above the boundary or discard it.

### 7.3 Destination probabilities

- **Common underground target:** normal cave entrance, short abandoned mining tunnel, small quarry termination.
- **Less common:** existing mineshaft or dramatic traversable ravine edge.
- **Extremely rare P2:** a route approaching—but **not directly revealing or entering**—a stronghold. Never touch the portal room or bypass intended End progression. Stop a substantial distance away and preserve Eyes of Ender relevance.
- Some endings should be dead ends, rubble or missing passages rather than guaranteed treasure.

## 8. Terrain, structures, and placement safety

- Favor gentle slopes, valley floors, forest edges, natural passes and existing settlement approaches.
- Use modest embankments, wooden/stone bridges, cuttings and occasional tunnels; prefer route changes over massive excavation.
- Preserve existing vanilla/modded structures by using structure-aware exclusion buffers where feasible. A settlement association does **not** authorize destroying its buildings.
- Do not replace block entities, chests, spawners, portals, rare blocks, or player-owned builds.
- Perform structural conflict validation before block placement; abort or reroute the affected piece safely on conflict.
- Do not cut trees indiscriminately across large corridors; forest rail remnants should look plausibly reclaimed.
- Prefer placement in new chunks only; enabling the mod on an existing save must not overwrite already generated chunks by default.
- Mod removal should leave ordinary vanilla blocks/rails in-world when possible; avoid unsafe hidden dependencies on custom rails.

## 9. Stations, junctions and landmarks

Use small, restrained vanilla-style stations as graph anchors and narrative clues. Components can include a short platform, wooden/stone shelter, worn signage, incomplete powered sections, storage remnants and simple junctions. Loot must be modest and balanced.

Station rules:
- Plains: higher likelihood, especially near villages or graph intersections.
- Forest/taiga: infrequent and sometimes mostly hidden.
- Desert/jungle: no standalone station unless anchored to existing settlement/appropriate POI.
- No obligatory custom villager professions, NPC interactions or quests.
- Graphical identity should signal route purpose without requiring UI labels.

**P2 map discovery:** Stations may rarely contain an approximate, incomplete network diagram or map fragment showing relative direction of nearby junctions and destinations. It must not act as a universal GPS, reveal all routes or replace exploration. Route diagrams must derive from actual graph data, never invented phantom destinations unless explicitly marked as ruined or uncertain.

## 10. Elytra balancing (optional, P1)

**Design intent:** Rail excels at repeated high-throughput point-to-point travel; Elytra excels at freeform exploration, vertical navigation and short-range traversal.

Do **not** impose a hard limit on normal gliding. Default P1 implementation may leave Elytra completely vanilla unless opted into balancing. If enabled:
- Consume stamina **only during sustained firework rocket propulsion**, not ordinary gliding.
- Recover stamina while unpowered gliding and faster while grounded.
- Exhaustion must reduce or block additional rocket boosts safely, **without abruptly canceling flight or forcing a landing**.
- Do not use an absolute 100–150-block flight cutoff; test a soft endurance budget of several hundred blocks of sustained powered flight.
- Display a minimal stamina indicator when relevant; ensure server authority and client synchronization.
- Provide config: `elytra_balance.enabled=false` by default, stamina capacity, rocket cost, regeneration rates and exhaustion effect.
- Handle water/oceans, mountains, void risks, dimension changes, reconnects, status effects, and other mods that modify Elytra or fireworks.

**Acceptance principle:** Players can still cross oceans and mountainous regions safely by gliding and can explore freely. The rail system should remain compelling even with Elytra balancing disabled.

## 11. World creation and configuration

Implement a server-authoritative config. World-specific generation settings should be **snapshotted at world creation/first initialization** and protected against accidental mutation that changes latent graph placement in already explored regions.

User-facing presets:
- `off`: no railway world generation; optionally keep minecart overhaul active.
- `standard` (default): sparse connected regional networks and considerable untouched wilderness.
- `expanded`: denser networks/junctions for infrastructure-focused worlds, still honoring exclusions.

Illustrative config contract (names and values provisional):

```json
{
  "schema_version": 1,
  "generation": {
    "enabled": true,
    "preset": "standard",
    "region_size_chunks": 128,
    "density_multiplier": 1.0,
    "damage_multiplier": 1.0,
    "underground_branches": true,
    "minimum_rail_y": 12,
    "protect_deepslate": true,
    "rare_stronghold_branches": false,
    "station_maps": false
  },
  "minecarts": {
    "speed_overhaul": true,
    "ordinary_cap_blocks_per_second": 16.0,
    "powered_cap_blocks_per_second": 28.0,
    "sustained_powered_cap_blocks_per_second": 35.0
  },
  "elytra_balance": {
    "enabled": false,
    "rocket_stamina_capacity": 100.0,
    "rocket_stamina_cost": 20.0,
    "gliding_regen_per_second": 3.0,
    "grounded_regen_per_second": 10.0
  },
  "debug": {
    "log_generation": false,
    "enable_admin_graph_commands": false
  }
}
```

These config values are **proposals**, not measured game-balance constants. Actual acceleration, regen and region sizes require prototyping. Provide sensible validation and clear fallback behavior for invalid config. Users must be able to disable just generation, minecart changes, or Elytra balance separately.

## 12. Architecture

Suggested Java package layout (adapt to actual API):

```text
com.example.lostrailways/
  LostRailwaysMod.java
  config/                 ConfigCodec, WorldSettings, BiomePolicy
  network/                NetworkPlanner, Node, Edge, RegionGraph, EdgeId
  pathing/                TerrainCost, RoutePlanner, ConstraintMasks
  worldgen/               RailwayPlacement, PieceTemplates, DamageModel
  worldgen/styles/        PlainsStyle, ForestStyle, DesertStyle, ...
  integration/            StructureProtection, BiomeTags
  minecart/               CartSpeedRules, CartPhysicsHooks
  elytra/                 RocketEndurance, EnduranceSync, ClientHud
  commands/               DebugRailwayCommands
  persistence/            NetworkState (only if necessary)
  test/                   Pure planner and integration tests
```

**Recommended separation:**
1. Pure deterministic graph planning, independently testable without Minecraft runtime.
2. Pure route calculation and damage/layout decisions.
3. Chunk-scoped placement adapters using actual game worldgen hooks.
4. Server-side transport mechanics.
5. Optional client renderer/HUD and debug overlays.

Use Mixins only when Fabric/vanilla APIs cannot express required physics or worldgen behavior. Minimize target-method surface, document each injection, and add regression tests. Prefer data-driven structure pools/templates and biome tags where possible. No synchronous world scanning or long-running pathfinding on the main server thread.

### 12.1 Deterministic route algorithm (illustrative)

```text
function planRegion(seed, regionId, config):
    candidates = selectCandidates(seed, regionId, terrainAndBiomeSummary)
    nodes = filterForSuitabilityAndProtectedAreas(candidates)
    graph = connectLocally(nodes, weightedTerrainDistance)
    graph += deterministicBorderConnections(regionId, neighbors)
    for each edge in graph:
        path = routeWithinBoundedCorridor(edge, slopeAndExclusionCosts)
        if path invalid: dropOrReplanEdgeDeterministically()
        style = deriveStyleFromBiomeSegments(path)
        damage = deriveDamage(seed, edge.stableId, path, biomePolicy)
        saveOrDeriveImmutableEdgePlan(edge, path, style, damage)
    return graph

function generateChunk(chunkPos):
    candidatePlans = lookupBoundedNearbyPlans(chunkPos)
    for plan in sortedByStableId(candidatePlans):
        pieces = clipToChunk(plan.placedPieces, chunkPos)
        placeOnlyAfterSafetyChecks(pieces)
```

Edge coordination must prevent duplicate/broken cross-region links, and resource use must remain bounded under pathological seeds. The above is an architectural sketch, not directly compilable Minecraft API code.

## 13. Multiplayer, persistence and compatibility

- Dedicated server must own network generation, physics and Elytra stamina.
- Worlds remain consistent across reconnects, restarts, backup/restore and traversal order.
- Do not keep whole-world route graphs in memory. Cache bounded macro-cell plans with eviction or derive deterministically from seed.
- Entity tracking, velocity updates and client-side interpolation must tolerate 35 blocks/s without rubber-banding.
- Avoid chunk-forcing by moving carts unless explicitly enabled; carts must behave predictably at unloaded chunk borders.
- Protect performance on servers with multiple fast minecarts; implement limits/monitoring before release.
- Respect world-border, spawn protection and claims/build restrictions where integrations are available; native worldgen should never post-edit existing claimed/player areas.
- Compatibility testing: vanilla server/client contexts, common performance mods, structure/worldgen mods, and minecart/vehicle mods. If invasive physics mods conflict, detect and warn rather than silently corrupting behavior.
- Client must have the mod if custom synchronization/rendering requires it; document exact installation requirements.

## 14. Performance targets

Targets are **engineering budgets to verify**, not guaranteed measured results:
- No unbounded planning or pathfinding on worldgen/main thread.
- No dependence on generating complete 2,000–5,000-block tracks in one operation.
- Planner memory bounded to nearby regions; no permanent whole-world precomputation.
- Benchmark generation against identical seeds with feature off/on; initial target average worldgen cost under 15% increase in railway-friendly regions, with p95 spikes tracked separately.
- No sustained server tick degradation from idle railway structures.
- High-speed cart benchmark at 35 blocks/s across slopes, curves, chunk boundaries, and moderate multiplayer load.

## 15. Acceptance tests

**Determinism/worldgen**
1. Same seed and config, different chunk generation orders -> identical graph and placed blocks in same target area.
2. Two bordering regions share exactly matching corridor endpoints.
3. Default presets produce clustered but sufficiently discoverable railways across representative seed sample.
4. At least 90% of visible line fragments correspond to connected latent edges or explicit allowed isolated remnants.
5. Station biome rules respected (no standalone jungle/desert stations without qualifying POI).
6. No placement below minimum Y or into excluded deep caves/biomes; no destructive edits to protected structures.
7. Incompatible terrain causes graceful route rejection/rerouting, not worldgen crash or floating geometry.
8. Disabling generation affects only new generation; existing generated/constructed tracks remain intact.

**Gameplay**
9. Unmodified vanilla rails and generated tracks share speed rules; sustained powered test track reaches configured 35 blocks/s within reasonable acceleration distance.
10. Carts do not phase through blocks/curves, duplicate inventory or exhibit severe rubber-banding at target speed.
11. Players can restore broken railway sections using vanilla rails and power them with vanilla redstone.
12. Surface-to-cave branches create usable entrances and terminate safely above protected depth.
13. If Elytra balance disabled, vanilla flight behavior unchanged; if enabled, normal gliding unrestricted and no forced aerial stopping.
14. World settings persist across dedicated server restarts and do not re-plan existing networks unexpectedly.
15. Mod can be disabled or updated without crashing existing worlds; supply documented migration/backup guidance.

**Visual/manual QA**
16. Plains railways are predominantly visible and repairable; forest rails can be partially concealed yet recognizable.
17. Desert/jungle through-lines are rare, not filled with random stations.
18. Most natural wilderness remains visually untouched; mountains and major caves preserve their identity.
19. Restoring one branch has a realistic chance of exposing a useful destination or onward network, but does not guarantee loot.

## 16. Implementation sequence / AI agent work plan

**Phase A — Bootstrap**
- Scaffold Fabric 26.3 Gradle project; pin Java/loader/API/Loom; launch client and dedicated server; establish config and CI smoke tests.

**Phase B — Minecart speed MVP**
- Implement configurable server-side speed/acceleration with vanilla rails; verify collisions, chunk transitions, multiplayer interpolation and chest carts. Release as independently testable module.

**Phase C — Network planner**
- Implement immutable nodes/edges, deterministic macro-cell planning, regional boundary agreements, biome masks, weighted routing and unit tests. Provide debug command/overlay to inspect latent graph without placing blocks.

**Phase D — Surface placement**
- Add chunk-scoped templates, plains/forest style, stations, junctions and deterministic damage. Validate seed reproducibility and all protection rules. This + B is **P0 playable release**.

**Phase E — Full biome and underground expansion**
- Add desert/jungle/taiga/swamp policies, improved crossings/tunnels and local cave/mineshaft branches with Y/deepslate restrictions. Benchmark and test structure conflicts.

**Phase F — Optional balancing and polish**
- Implement optional Elytra rocket endurance, client HUD, route-map collectibles (if in scope), admin tools, localization and published modpack documentation.

**AI implementation instructions:** Treat P0 features and acceptance criteria as contract; do not silently implement P2 features. Verify target Fabric 26.3 APIs and build dependencies against their actual artifacts before writing integration code. Prefer a small compiling, testable vertical slice over speculative full-system scaffolding. If a design choice is underspecified, preserve the principles above and expose reasonable defaults as configuration; record decisions in `docs/DECISIONS.md`. Keep generated world structures deterministic and non-destructive as higher priority than visual density.

## 17. Definition of done (P0)

- Builds cleanly for Minecraft Java 26.3 with current stable Fabric toolchain.
- Loads on client and dedicated server and survives repeated world saves/restarts.
- Player can find a sparse plains/forest abandoned railway with a junction or station in a newly generated world.
- Most discovered fragments map to a real latent connected route; damage is repairable with vanilla blocks.
- A repaired, adequately powered stretch supports target high-speed travel without major physics failures.
- World-gen generation can be turned off at world creation; minecart overhaul is independently configurable.
- Route generation deterministic, chunk-safe, terrain-conscious and performant within measured targets.
- Unit/integration tests and a short tester guide demonstrate each P0 acceptance criterion.

## 18. References (verified 2026-10-28)

- Minecraft Java latest stable 26.3 and Java requirement: https://mcreference.com/latest
- Minecraft official release changelog collection: https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs
- Fabric for Minecraft 26.3 announcement: https://fabricmc.net/2026/09/15/263.html
- Official Fabric developer tool versions: https://fabricmc.net/develop/
- Fabric documentation: https://docs.fabricmc.net/
- Recent loader popularity evidence: https://skilles.github.io/MCModPopularity/

