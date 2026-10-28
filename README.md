# Lost Railways

A Minecraft Java 26.3 Fabric mod about discovering, restoring, and reusing abandoned railways.

**Status: Phase E, taiga expansion.** Minecart improvements, deterministic networks, abandoned tracks,
small stations, and junctions are implemented for new plains, forest, and taiga terrain. Broader biome coverage,
tunnels/bridges, and Elytra balancing remain future work. See [the specification](docs/plan/lost_railways_spec.md).

## Development

Use **JDK 25** and the checked-in **Gradle 9.8.2 wrapper**. No system Gradle installation is needed.
The game requires Java 25; development and CI deliberately use that version too.

```sh
./gradlew build                 # Compile, JUnit tests, server GameTests, JARs
./gradlew test                  # Fast config, movement, planner, and placement tests
./gradlew runGameTest           # Minecart, planner, and placement integration tests
./gradlew runGameTest -PvanillaMinecartTests=true # Disabled-overhaul regression in a separate JVM
./gradlew runClientGameTest     # Rendering, riding, commands, and real-terrain tests
./gradlew runClient             # Interactive development client
./gradlew runServer             # Interactive dedicated server
```

For the interactive dedicated server, first create `run/server/eula.txt` containing `eula=true`
if you accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). The project owner accepted it
for local/CI test servers; Loom generates acceptance for automated GameTests. Type `stop` in
the dedicated-server console to save and shut down.

Open this folder in **IntelliJ IDEA** as a Gradle project. Select JDK 25 for both the project SDK
and Gradle JVM, use the Gradle wrapper, and delegate builds/tests to Gradle. Reload Gradle to
get Minecraft run configurations; run `./gradlew genSources` for game-source navigation.
`.editorconfig` supplies Java/IDEA conventions. Personal `.idea` settings stay untracked.

The mod JAR and source JAR are written to `build/libs/`. Install the non-sources JAR with
Fabric Loader 0.20.1+ and Fabric API 0.165.0+26.3 on Minecraft 26.3. When the overhaul is enabled, install Lost Railways on **both server and clients**: the server
synchronizes the movement mode before joining. Clients without the protocol are rejected with a clear message.
With the overhaul disabled, behavior selection remains vanilla (including existing experimental-world settings).

## Configuration

On first initialization, `config/lost_railways.json` is created in the game instance:

```json
{
  "schemaVersion": 1,
  "generationEnabled": true,
  "minecartOverhaulEnabled": true,
  "elytraBalanceEnabled": false,
  "minecarts": {
    "ordinarySpeed": 16.0,
    "poweredSpeed": 28.0,
    "sustainedSpeed": 35.0,
    "curveSpeed": 8.0,
    "slopeSpeed": 12.0,
    "poweredAcceleration": 24.0,
    "sustainedPoweredTicks": 60
  }
}
```

Speeds are blocks/second; powered acceleration is blocks/second², before vanilla drag. Caps are not
instant target velocities. The powered cap ramps from 28 to 35 over 60 consecutive moving ticks on
powered rails; crossing even a short ordinary/unpowered gap resets that ramp. Curves and slopes
apply lower caps on every rail step. Water slows the cart, unpowered rails still brake, and collision,
redstone, chest/hopper inventory, and interpolation behavior use vanilla's stepped minecart engine.
The overhaul does not enable experimental world flags or change gamerules.

Set `minecartOverhaulEnabled` to `false` and restart to restore vanilla behavior. Numeric settings are
server-owned; clients cannot override them. The supported maximum cap is 60 blocks/s, with
`ordinarySpeed <= poweredSpeed <= sustainedSpeed`; curve/slope caps cannot exceed the ordinary cap.
Acceleration accepts 1–80 and sustained powered time accepts 1–1200 ticks. Defaults are initial tuning,
not a claim that every possible rail layout or mod combination is safe at the maximum allowed settings.

The safety guard stops a cart before an unloaded chunk without generating/loading it. After the area
loads, a stopped cart may need a push or vanilla powered-rail restart arrangement. Powered time is
transient and resets after unloading/restarting; cart inventory remains vanilla persistent data.

Existing Phase A config files remain valid; missing nested settings use defaults without rewriting the
file. Invalid values log an error and fall back to defaults while preserving the original file. Restart
to apply changes; live movement-mode changes are intentionally unsupported. The generation switch is also a runtime kill switch after restart; the Elytra
switch remains reserved for a later phase.

## Inspect planned railway networks

With cheats enabled or operator permission, run:

```mcfunction
/lostrailways graph
/lostrailways graph 0 0
```

The first command inspects your current region; the second takes **region coordinates**, not block
coordinates. Defaults use 128×128 chunks (2048×2048 blocks) per region. Negative coordinates use
floor division, so block X=-1 belongs to region X=-1.

Inspection runs in a background worker and reports candidate stations/junctions, shared boundary
nodes, route endpoints, unconnected candidates, and work/time statistics. Full waypoint data is saved
under `<world>/data/lost_railways_debug/region_X_Z.json`. This command **does not place blocks or
load the inspected chunks**. Empty/disconnected graphs are valid in unsuitable terrain.

The planner currently supports Overworld vanilla noise and flat generators. Plains, selected forests, and taigas are enabled through datapack biome tags; oceans, rivers, and mountains are excluded. Unknown
biomes default to excluded. Underground networks and broader biome support remain later phases.

Add a `planner` object to the instance config to tune **new worlds** (omitted fields use defaults):

```json
"planner": {
  "regionSizeChunks": 128,
  "boundaryConnectionChance": 0.45,
  "maximumSlope": 0.25,
  "biomeRules": {
    "lost_railways:preferred": { "cost": 1, "stations": true },
    "lost_railways:forest": { "cost": 3, "stations": true },
    "lost_railways:taiga": { "cost": 3, "stations": true },
    "lost_railways:excluded": { "cost": 0, "stations": false }
  }
}
```

Region size accepts multiples of 32 from 64–256 chunks; boundary chance accepts 0–1; maximum slope
accepts values above 0 through 0.5. Biome-rule keys are datapack biome tags; cost 0 excludes, and
positive costs up to 100 weight routing. Exclusions win overlaps, otherwise the lowest cost wins;
equal-cost rules allow stations only if all allow them. Supplying `biomeRules` replaces that entire
map. Tags live under `data/lost_railways/tags/worldgen/biome/` and can be extended by datapacks.

On first initialization, `<world>/data/lost_railways_planner.json` freezes the planner version,
generation switch, numeric settings, and resolved biome policy. Later config edits or tag reloads
cannot rearrange that world's network. Set `generationEnabled: false` and restart to stop new placement
in any world; existing railways remain. Worlds created with generation disabled remain disabled even
if the instance switch is subsequently enabled. Malformed/future-version snapshots disable planning and are preserved; they
are never silently reset. Keep the snapshot with backups; automatic migration/reset is not provided.
Reproducibility assumes the same underlying seed/generator and worldgen definitions, not just the
same mod config; changing those requires separate migration analysis.

## Discover abandoned surface railways

Phase D refines coarse routes to per-block rail shapes as **new terrain** generates. Explore plains and
forests in a new world, or previously ungenerated terrain in an existing world. Already generated chunks
are never retrofitted. `/lostrailways graph` exports coordinates to help find routes; it remains read-only.
For a reproducible playtest, create a normal world with seed `42`, inspect region `80 80`, and explore
around X=164096, Z=165056 toward X=164480, Z=164608. Use spectator mode to survey the surface.

Plains use gravel ballast and weathered stone platforms; forests use mossy ballast, wooden platforms,
more missing rails, and fewer shelters. Tracks include curves, gentle slopes, occasional **unpowered**
powered rails, and deterministic gaps. Three-way junctions use vanilla curves; four-way crossings are
left visibly broken because one vanilla rail cannot connect four directions. Small roofed stations
need a flat, clear footprint; a planned node does not guarantee a building on unsuitable terrain.

All blocks are vanilla. Placement skips fluids, trees, block entities, ores, deepslate, unsafe slopes,
and chunks within a conservative one-chunk buffer of structure references. Whole station templates
are rejected on conflicts. Later world-generation features cannot overwrite generated railway cells;
players can freely repair, replace, and remove them. Gaps across unsuitable terrain are intentional:
bridges, tunnels, and extensive earthworks are outside this phase.

Optional configuration for new surface snapshots:

```json
"surface": {
  "minimumRailY": 12,
  "missingRailChance": 0.18,
  "missingBedChance": 0.035
}
```

The minimum accepts 12–256, missing rails 0–0.6, and missing ballast 0–0.15. Ground-support checks also
respect the minimum, so the lowest eligible tracks may be skipped. The world stores these settings and
resolved styles in `data/lost_railways_surface.json`. Existing Phase C planner snapshots retain their
network unchanged; surface settings freeze on first initialization with Phase D. Keep both snapshots
with backups. Changing config does not change an existing world's wear pattern.

## Tests and CI

- `src/test`: pure JUnit tests for config migration/validation, acceleration, speed caps, powered-time resets, bounded chunk safety, deterministic planning, world snapshots, and request limits.
- `src/gametest`: a separate test mod loaded only by Loom test runs. Server tests exercise acceleration to 35 blocks/s, curves, slopes, solid/entity collisions, braking,
  loaded/unloaded chunk boundaries, and cargo preservation. A separate JVM verifies disabled-overhaul
  vanilla behavior. Client tests exercise riding in single-player and over a dedicated-server connection,
  position synchronization, reconnecting, and rendered screenshots. Planner integration tests verify
  operator commands, biome tags, persisted settings, and sampling without loading remote chunks; a
  normal-world client test checks the noise generator and a connected reference route.
- Surface tests cover chunk-order determinism, seams, curves, junctions, atomic station rejection,
  protected terrain, persisted settings/protection, and actual noise-world rails.
- GitHub Actions runs build/server tests and enabled/disabled generation client suites with Xvfb on Linux. Reports and
  screenshots are uploaded even on failures. Unit and integration results live under `build/`.
- The **Release candidate** workflow builds a supplied version after both test layers pass and retains
  downloadable JARs. Public publishing is deferred until a license and distribution destinations
  (for example Modrinth/CurseForge) are selected and credentials configured.

See [the tester guide](docs/TESTING.md) for manual coverage and remaining compatibility/performance
checks. The automated suite does not certify arbitrary modpacks or multi-player server load.

## Docker

Docker is optional for reproducible Linux compilation and dedicated-server GameTests:

```sh
docker build --target artifacts --output type=local,dest=build/docker .
```

The build uses Java 25 and the same wrapper/tests as native development. Exported JARs appear in
`build/docker/`. Native Gradle/IDEA is recommended for interactive clients and debugging; CI provides
Xvfb for graphical testing. No game worlds or credentials are copied into the image.

See [development decisions](docs/DECISIONS.md) for verified versions and boundaries.

### Reproduce the Linux client CI test

```sh
docker build -f Dockerfile.client-test -t lost-railways-client-test .
docker run --name lost-railways-client-smoke \
  --mount type=volume,source=lost-railways-client-gradle,target=/root/.gradle \
  lost-railways-client-test
docker cp lost-railways-client-smoke:/workspace/build/diagnostics build/client-diagnostics
docker cp lost-railways-client-smoke:/workspace/build/run/clientGameTest/screenshots build/client-screenshots
docker rm lost-railways-client-smoke
```

On Ubuntu 24.04, the equivalent commands are `sudo bash scripts/install-client-test-deps.sh`
and `bash scripts/run-client-smoke-test.sh`. The shared script forces SDL 3 to use X11,
starts a 24-bit Xvfb display, checks Mesa's OpenGL/Vulkan software-rendering support, and bounds
client startup and testing to 12 minutes (plus a 30-second shutdown grace period). A nonzero test,
preflight, or timeout result fails the workflow. Both validation and release-candidate workflows
use this script and retain its console log, game logs, crash reports, and screenshots.
Minecraft may report a GLX visual error before successfully selecting software Vulkan under Xvfb;
the test must still complete successfully and capture the rendered world.

The Docker test uses the host's CPU architecture by default. For exact GitHub runner architecture,
add `--platform linux/amd64` to both Docker commands; emulation on an ARM Mac is slower.


### Taiga expansion

New worlds also route through regular, snowy, old-growth pine, and old-growth spruce taigas via the
`lost_railways:taiga` tag (which includes `minecraft:is_taiga`). They have the same routing cost, wear,
and occasional-station frequency as forests, with mossy cobblestone ballast and spruce platforms,
posts, roofs, and junction markers. Trees, fluids, structures, and steep terrain retain the same
protection rules. Snowy taiga is eligible; the mod does not clear snow or create powered routes.

Existing planner and surface snapshots stay unchanged, so existing worlds do **not** automatically
start generating taiga railways. Use a new world to try the expansion. If your instance config explicitly
provides a `planner.biomeRules` map, add the taiga rule shown above before creating that world: explicit
maps replace defaults. Taiga styling takes precedence over forest/plains when tags overlap; routing
exclusions still win. Bridges, tunnels, and underground branches remain later Phase E iterations.
