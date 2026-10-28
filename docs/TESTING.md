# Verification

Run `./gradlew build` for unit and enabled-overhaul server tests, then
`./gradlew runGameTest -PvanillaMinecartTests=true` for the disabled-overhaul suite.
Run `./gradlew runClientGameTest` for client rendering, riding, configuration synchronization,
dedicated-server connection, and reconnect checks. On headless Ubuntu use
`bash scripts/run-client-smoke-test.sh`; the README documents the matching Docker setup.

Server tests use their own disposable config and a 160-block test structure. The disabled suite runs
in a separate JVM/directory so parallel tests cannot observe changing global configuration. JUnit XML
for the server suites is retained under `build/test-results/gameTest` and `vanillaGameTest`.

## Automated cases

- Continuous powered rails accelerate an empty cart gradually to 35 blocks/s and cross several loaded
  chunk boundaries without derailment or exceeding the cap.
- Ordinary rails cap speed at 16 blocks/s; unpowered powered rails stop a moving cart.
- High-speed entries negotiate a 90-degree curve and an ascending slope, retaining the track.
- Carts reach solid obstacles at high speed without tunneling. Powered rails may legitimately launch
  a stopped cart back away from an adjacent wall; tests allow that vanilla rebound.
- A moving cart pushes another cart instead of passing through it.
- A chest cart reaches high speed, crosses chunks, hits a wall, and retains cargo in first/last slots.
- A real cart beside an unloaded chunk stops through the production movement hook without loading
  its destination. Unit tests also cover positive/negative coordinates and diagonal query bounds.
- A disabled-overhaul JVM selects old vanilla movement in an ordinary world and retains 8 blocks/s
  maximum displacement. No test changes the production config while another test is running.
- Client tests verify matching movement behavior, high-speed riding, bounded client/server position
  differences (including vanilla packet/interpolation delay), convergence within 0.25 blocks after
  stopping, and fresh configuration synchronization on reconnect.

## Manual playtest before release

1. Use a disposable world. Build a long vanilla powered route, then introduce gaps, curves, ascending
   and descending slopes, an unpowered braking section, and solid obstacles. Test ridden, empty,
   chest, and hopper carts. Check redstone activator/detector rails and hopper loading/unloading.
2. Repeat with the overhaul disabled, then with a few valid custom limits. Restart between changes.
   Check an existing non-experimental save as well as a world with vanilla minecart experiments enabled.
3. Put named/enriched items in cargo carts, save/restart the server, unload/reload chunks, and break
   carts normally. Verify item counts, names/components, and drops. Back up the world before removal
   or upgrades; the mod adds no custom minecart entities or inventory persistence format.
4. Connect multiple real clients to a dedicated server. Ride across chunk borders under realistic
   latency and watch for rubber-banding, collision problems, or passenger dismount issues. Check
   that a client without the mod is rejected when the overhaul is enabled.
5. Benchmark many simultaneous fast carts, then test the intended performance/worldgen/vehicle mods.
   The automated single-client checks are not a load benchmark or a compatibility certification.

World generation, railway networks, furnace locomotives/convoys, and Elytra balancing are outside Phase B.

## Phase C planner verification

The same build and client commands now include planner coverage:

- Fixed planner-v1 topology fingerprint, repeatability across visitation order and concurrent calls,
  and different seed/dimension outputs.
- Matching boundary node IDs/coordinates/heights on both axes, including negative regions.
- Connected flat-terrain graphs, immutable output, continuous coarse waypoints, forbidden-biome/cliff
  separation, weighted detours, and fixed sample/search limits.
- World snapshot round-trip with changed config/datapacks ignored; malformed/new-version files
  preserved. Configuration migration and validation remain covered.
- Background execution, cache reuse, bounded request queue, cancellation, and disabled-world behavior.
- Real server command registration/execution/export and generator sampling without loading remote chunks.
- A client submits `/lostrailways graph 80 80` in a normal seed-42 world, exercising actual noise terrain
  sampling and the command permission/packet path. Its remote region must remain unloaded. The
  test limits render/simulation distance to two chunks and does not wait for the entire view to render;
  the existing bootstrap test separately covers rendered-world startup.

Manual checks: compare adjacent region exports across a server restart; try a datapack biome-tag
change on a fresh world and confirm an existing world's policy stays frozen. Inspect multiple seeds
before adjusting density. Region timing is included in command output; this is not a world-generation
performance benchmark, since Phase C installs no placement callback. Physical placement/protection,
route refinement, and shared-corridor junctions are covered by Phase D below.


## Phase D surface placement

`./gradlew build` includes pure layout/placement tests and real Minecraft chunk integration tests.
Coverage includes positive/negative chunk seams, slopes and curves, merged junctions, deterministic
wear under reversed/concurrent traversal, chunk-owned station footprints, whole-template rejection,
protected blocks/fluids/trees, existing-terrain exclusion, structure references, immutable surface
snapshots, and persisted generation-protection attachments.

Run both end-to-end modes in separate JVMs:

```sh
./gradlew runClientGameTest
./gradlew runClientGameTest -PsurfaceGenerationDisabled=true
```

Each run uses disposable config. The surface test creates two normal seed-42 dedicated-server worlds,
generates reference-route chunks in opposite orders, compares every marked block and state, and verifies
player changes remain possible. The enabled run requires actual vanilla rails; the disabled run requires
zero placement. `build/run/clientGameTest/surface-proof.json` records coordinates, block states, rail
counts, chunk timings, mean, and p95. Copy evidence before the next run, which deletes that run directory.
CI runs both modes independently and uploads the proofs. The same property can be passed to
`scripts/run-client-smoke-test.sh` or the Docker image command.

The timings include synchronous test requests, native generation dependencies, and cold region planning.
They are a small regression sample, not certification of the spec's <15% representative generation
budget. Before release, benchmark a multi-seed exploration workload against disabled generation on the
same machine, comparing average and p95 latency after warm-up.

Manual checks: inspect fresh plains/forest routes, repair gaps and ride through curves/junctions; check
station appearance and terrain integration on several seeds. Save/restart, edit a generated station,
and explore adjacent new chunks to confirm edits stay intact. Compare a pre-existing save before/after
upgrade: only fresh terrain should gain railways. Removing the mod leaves vanilla blocks behind.


## Phase E: taiga

The standard build now checks all four vanilla taiga biomes against the loaded biome tags and routing
policy, round-trips frozen pre-taiga surface settings, verifies complete vanilla spruce station placement,
and compares taiga wear with forest wear over 100 deterministic seeds. The layout test also verifies
spruce junction markers and occasional station frequency. Existing protection/seam tests and the normal
world forward/reverse-generation client suite remain active in CI.

Manual check: use a new normal world with default biome rules, inspect a graph in taiga, and follow it
through regular, snowy, and old-growth variants. Look for mossy ballast and spruce shelters; check tree
preservation and natural snow cover. Existing worlds intentionally retain their frozen biome eligibility.


## Phase E: remaining surface biomes

The standard build verifies desert/jungle station prohibition across 100 seeds, deterministic palettes,
wetland/jungle core and ocean-edge exclusion, bounded edge sampling, raised swamp rails and their
ascending land transition. Minecraft integration tests resolve all vanilla desert/jungle/swamp variants,
retain ocean/mountain/deep-dark exclusions, round-trip the new styles, and verify a dry raised column
places while an adjacent water column remains entirely untouched. Existing conflict, snapshot, chunk
seam, and normal-world order tests continue to run.

For manual coverage, explore new-world desert corridors, jungle fringes, and dry swamp/mangrove edges.
Repair gaps and ride transitions; check that trees/roots and water stay intact. No desert/jungle stations
are expected until settlement-aware placement is implemented. Large bridges/tunnels remain later work.


## Phase E: small crossings

`./gradlew build` includes short bridge/tunnel fixtures, deterministic abandonment, deck/rail consistency,
length/depth limits, flat approaches, negative-coordinate north/south orientation, chunk-boundary
rejection, and legacy snapshot behavior. Minecraft integration tests place complete templates on real
ProtoChunks, verify level rails and three-block tunnel headroom, preserve water below bridges, and reject
entire pieces on chest/spawner/ore/deepslate/log/fluid/loose-material conflicts. A cavity in the tunnel
wall also rejects excavation. Existing native-world forward/reverse generation tests remain active.

Manual playtest on a new disposable world: find short depressions/ridges along a planned route; inspect
abutments, support frames, and damage. Repair the deck/rails, ride through from both ends, save/restart,
and check that player repairs persist. Large spans, chunk-border obstacles, rivers/oceans, and protected
terrain intentionally remain gaps. The automated fixtures are not a broad multi-seed appearance or
performance benchmark.
