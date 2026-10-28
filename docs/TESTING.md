# Phase B verification

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
