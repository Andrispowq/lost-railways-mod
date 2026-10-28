# Development decisions

## 2026-10-28 — Phase A baseline

Use Java and Gradle (Groovy DSL), following Fabric's official template and IntelliJ IDEA's standard
Java conventions. Keep classes focused and introduce abstractions only when there is a concrete need.
Use `io.github.andrispowq.lostrailways` as the package namespace.

Verified against actual published metadata rather than the examples in the specification:

| Component | Pinned version | Evidence |
| --- | --- | --- |
| Minecraft | 26.3 stable | [Mojang manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| Java | 25 | Minecraft 26.3 version manifest `javaVersion.majorVersion` |
| Fabric Loader | 0.20.1 stable | [Fabric loader metadata](https://meta.fabricmc.net/v2/versions/loader/26.3) |
| Fabric API | 0.165.0+26.3 | [Published Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) |
| Fabric Loom | 1.19.3 stable | [Published Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) |
| Gradle | 9.8.2 stable | [Gradle release metadata](https://services.gradle.org/versions/current) |

The spec's Minecraft/Java requirements are confirmed. Its Loader 0.19.5 baseline is superseded by
0.20.1. Pin stable Loom instead of the development site's snapshot recommendation. Minecraft 26.3
is unobfuscated: use `net.fabricmc.fabric-loom` and official game symbols, with no Yarn/remapping layer.
The wrapper distribution SHA-256 is verified and pinned.

Use JUnit for pure code and Fabric's separate GameTest source set for server integration/client smoke
tests. See [Fabric automated testing](https://docs.fabricmc.net/develop/automatic-testing). Test classes
and test entrypoints must never ship in the production mod JAR. No Mixins are needed for bootstrap.

Start with a small configuration schema containing independent feature switches; do not scaffold the
full hypothetical worldgen system. The spec's JSON contract is explicitly provisional. Keep feature
parameters and per-world immutable generation settings for the phase implementing those features.

The owner accepted the Minecraft EULA for local and CI test servers on this date. Automated test runs
may write `eula=true`. Normal development worlds remain ignored and disposable test worlds live in
build output. Docker is used for an optional build/test environment, not graphical development.

CI runs on pushes and pull requests with read-only repository permissions. Release-candidate workflow
produces validated artifacts without publishing unfinished gameplay as a public release. Choose a
project license and hosting targets before enabling public deployment. No license is assumed on the
owner's behalf.

### Native verification

On macOS ARM64, the pinned toolchain compiled the production JAR, passed four configuration unit tests
and the server GameTest suite, and launched a normal dedicated server on localhost. The dedicated
server reached `Done`, then saved and exited successfully on `stop`. The client GameTest created a
single-player world, rendered terrain, saved `build/run/clientGameTest/screenshots/0000_lost-railways-bootstrap.png`,
and exited with a successful Gradle result. The JAR was inspected to confirm GameTest classes/metadata
are excluded. Both Gradle distribution and wrapper JAR checksums match official Gradle metadata.

The optional Docker build also completed on Linux ARM64: compilation, JUnit tests, server GameTests,
and artifact export all passed. GitHub workflow YAML and referenced action versions were checked;
remote GitHub Actions execution remains to be verified after pushing the changes.

## 2026-10-28 — Headless client CI graphics setup

The supplied GitHub Actions log shows the client failing before world creation: SDL could not find a
matching GLX visual for OpenGL, then Vulkan initialization reported missing `VK_KHR_surface`. The
subsequent timeout therefore does not demonstrate a GameTest or network-synchronizer failure.

Use a shared Ubuntu dependency installer and client launcher for validation and release candidates.
Install Mesa's Vulkan software driver as well as OpenGL libraries; explicitly select SDL 3's X11
backend, llvmpipe software rendering, and a 24-bit Xvfb screen. Run `glxinfo -B` and `vulkaninfo --summary`
under that same display before Minecraft so renderer problems are visible immediately. These choices
follow [SDL's video-driver setting](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_DRIVER) and
[Mesa's software-rendering settings](https://docs.mesa3d.org/envvars.html).

Bound the entire client invocation to 12 minutes with a 30-second termination grace period. Preserve
pipeline failure status through `tee`; retain the console transcript, game logs, crash reports, and
screenshots with the workflows' existing always-upload steps. Keep server/build validation separate
from the client deadline in the release workflow. Do not disable synchronization or weaken world
rendering assertions to mask a graphics initialization failure.

`Dockerfile.client-test` provides the same Ubuntu 24.04 packages and launcher locally. Native macOS
success alone is insufficient evidence for a headless Linux graphics fix. The owner's Git workflow
and CI-status checks remain owner-managed.

Local Ubuntu 24.04 ARM64 validation reproduced the OpenGL GLX-visual error even with `glxinfo`
reporting working OpenGL. With `mesa-vulkan-drivers` installed, Minecraft successfully fell back to
Mesa software Vulkan, created the X11 window, loaded a single-player world, rendered terrain, captured
a screenshot, and exited successfully. The cold Gradle run took 4m 13s. This confirms the Vulkan
fallback is the material rendering fix; an OpenGL preflight alone is not sufficient. An initial
OpenGL error may still appear before successful Vulkan selection. No Minecraft/Fabric versions or
GameTest assertions were changed.

Launcher error-path checks verified nonzero propagation for OpenGL preflight, Vulkan preflight,
Gradle, and timeout failures, plus release-version argument forwarding. Workflow YAML and shell
syntax checks passed. GitHub's x86-64 runner result remains for the owner to verify after pushing.
The finalized launcher also passed a second, warm-cache Linux ARM64 run in 1m 37s, with software
Vulkan selected, a rendered-world screenshot captured, and no runtime-directory warning. Local
evidence is retained under ignored `build/client-ci-proof/`; the two test containers were removed.

## 2026-10-28 — Phase B minecart movement

The owner confirmed Phase A CI is green and selected the feature branch. Use Minecraft 26.3's
`NewMinecartBehavior` for the enabled overhaul, including its rail-by-rail collision stepping and
`ClientboundMoveMinecartPacket` interpolation. Do not retrofit large per-tick displacements into
`OldMinecartBehavior`, and do not change world feature flags. Disabled mode leaves vanilla's selection
untouched. The server owns all numeric tuning; a versioned configuration-phase payload synchronizes
only the enabled flag before cart construction. Require capable clients when the overhaul is enabled.
Reset client mode when connecting/disconnecting so joining a different server cannot inherit it.

Two required Mixins select vanilla's movement mode and apply the speed policy. The latter replaces
the maximum-speed lookup at method entry because its original gamerule is absent in non-experimental
worlds. It adjusts powered acceleration, enforces a cap after each rail calculation (vanilla clamps
only the first iteration), and guards the upcoming collision/rail-query area using non-loading chunk
lookups. It does not replace inventory operations, collision code, entity ticking, rail pathfinding,
or interpolation. Injection matches are required so incompatible game updates fail clearly.

Defaults: ordinary 16, powered 28, sustained 35, curves 8, slopes 12 blocks/s; powered acceleration
24 blocks/s²; 60 consecutive moving powered ticks to ramp the powered cap to sustained. Vanilla drag
and water penalties remain. Powered time resets on any non-powered rail, including gaps traversed
within one tick; it is transient, not save data. A bounded 60 blocks/s configuration ceiling constrains
rail iterations and chunk checks. The unloaded-chunk guard stops rather than force-loads; restarting
may require a push after chunks load. These are conservative initial tuning decisions, subject to
playtesting. Config schema 1 remains backward compatible through defaults for missing `minecarts` fields.

Test the disabled mode in its own JVM with a real config file instead of mutating global settings
while GameTests execute in parallel. The dedicated connection/reconnect client tests supplement
headless server tests; multi-client latency/load and broader mod compatibility remain manual release
checks in `docs/TESTING.md`.

Local verification: `./gradlew build` passed with 14 JUnit tests and 10 enabled server GameTests
(including framework/bootstrap checks). The isolated disabled suite passed all 3 GameTests.
The native macOS client suite passed single-player and dedicated-server high-speed rides, matching
client movement selection, stopped-position convergence, and reconnect synchronization. The packaged
JAR includes the client initializer and required Mixins and excludes the GameTest classes.

The same client suite also passed in the local Ubuntu 24.04 ARM64 Docker environment with Xvfb and
Mesa software rendering in 5m 42s. Logs and screenshots are retained under ignored
`build/phase-b-evidence/linux/`. This verifies the shared CI launcher locally; GitHub's x86-64 run
remains for the owner to check after committing and pushing.

## 2026-10-28 — Phase C network planner

Implement an inspectable planning layer before installing world-generation placement callbacks.
Planner v1 is stateless: world seed, dimension, fixed feature salt, region coordinate, frozen settings,
and seed-derived terrain determine immutable nodes/edges/waypoints. A golden topology fixture makes
accidental changes to persisted-world routing visible. Future topology changes require an explicit
version/migration decision, rather than silently replacing the snapshot.

Use 128-chunk regions by default and a fixed 33×33 terrain grid. Select up to three well-separated
station candidates in the largest suitable connected interior area, rather than scattering candidates
across isolated biome pockets. Classify degree-three-or-higher internal nodes as junctions. Canonical
boundary keys independently select matching gateway IDs/coordinates for both neighbors, including
negative coordinates. A gateway agreement is a shared candidate, not a promise that both regions can
route to it; disconnected candidates are explicitly reported and place nothing.

Evaluate bounded, deterministic weighted shortest paths between candidate pairs and retain a
minimum-cost spanning forest. Excluded samples and excessive sampled gradients prevent traversal;
non-gateway border samples cannot become accidental crossings. At most seven nodes yield 21 searches,
each expanding at most 1089 cells. Interior connectivity classification is another bounded grid pass.
This permits disconnected components instead of forcing routes through unsuitable terrain. Routes
remain coarse intent: Phase D must refine terrain, merge shared corridors/intersections into physical
junctions, and apply structure/player-build protections before placing any block.

Biome policy is data-driven through tag-to-cost/station rules. Defaults permit plains and selected
forests; exclusion tags take precedence and unknown biomes are excluded. Positive costs choose the
lowest matching cost, with station permission intersected on equal-cost ties. Later biome/underground
expansion remains Phase E. The current planner supports Overworld vanilla noise and flat generators;
other generators fail explicitly until their sampling/thread-safety contracts are verified.

Capture generator inputs on the server thread, then use private biome sampling caches and base-height
noise queries on one background worker. Never query loaded chunk terrain or generate distant chunks.
Two queued requests and a sixteen-region LRU cache bound background work/memory; shutdown interrupts
planning and cancels outstanding requests. The command reports work counters and elapsed time.

On first server initialization, atomically write `data/lost_railways_planner.json` inside the world.
Freeze planner version, generation enablement, settings, and resolved biome policies. Load the existing
snapshot without reevaluating instance defaults or datapack tags. Strict numeric/boolean validation
rejects incomplete, malformed, or newer snapshots and disables only planning while preserving the
file. Changing the underlying seed, generator, Minecraft worldgen implementation, or worldgen
noise/biome definitions is outside this snapshot's reproducibility contract and requires separate
migration analysis. No reset/migration command is supplied in this phase.

Expose `/lostrailways graph [regionX regionZ]` to operators. It queues planning, lists nodes/routes and
unconnected candidates, and atomically exports full JSON under `data/lost_railways_debug/`. It places
no blocks. Region exports are explicit debug artifacts; the in-memory planner cache remains bounded.

Verification: the final build passed 29 JUnit tests and 12 enabled server GameTests; the separate
vanilla-minecart regression passed all 5 tests (including planner checks). The native client suite
passed, and the Ubuntu 24.04 ARM64/Xvfb software-rendered suite passed in 7m 23s. The normal-world
command test uses two-chunk render/simulation distances and waits for its graph export, avoiding an
unrelated full-view render wait that exhausted the first Linux attempt's overall timeout.

The seed-42 region (80,80) export is identical on macOS and Linux: two connected nodes, one route,
14 waypoints, 1089 terrain samples, and 36 search expansions. The client regression now requires a
connected route in this reference region; that assertion was checked against both saved exports.
Evidence is retained under ignored `build/phase-c-evidence/`. The mod JAR contains planner classes
and biome tags and excludes test classes. Existing CI tasks discover the expanded suites; GitHub
status remains for the owner to check after committing/pushing.


## 2026-10-28 — Phase D surface placement

Refine planner-v1 cardinal paths per column, merge shared geometry before deriving vanilla rail shapes,
and use coordinate-seeded wear independent of chunk order. Keep templates entirely owned by one chunk;
validate all ground/clearance/bounds against original terrain before any writes, then reject conflicting
pieces atomically. Plains/forest styles use only vanilla blocks, rare unpowered powered rails, small
weathered platforms and shelters, and junction markers. Four-way crossings deliberately omit the center
rail; tunnels, bridges, broad biome coverage, and large stations remain later work.

Minecraft 26.3 decorates features after neighboring terrain is available. A decoration feature cannot
reliably inspect untouched terrain because earlier neighbors may already have written vegetation there.
Append placement to the TERRAIN task future instead: native FEATURES waits for all neighboring TERRAIN
futures, so every railway is decided before decoration. Use two daemon workers and a sixteen-region LRU;
Minecraft controls outstanding chunk tasks. Generator base-height and private biome-cache queries need
no additional loaded chunks. Keep command planning on its separate bounded service.

Only fresh, non-retrogen ProtoChunks are eligible. Loading existing terrain follows Minecraft's loading
pipeline and never calls placement. Protect all valid structure starts/references in the native 3×3
terrain dependency area. Use a narrow natural-ground whitelist; no block entities, fluid removal, tree
clearing, ore/deepslate replacement, or writes outside the owning chunk/world border/minimum height.
Re-prime final heightmaps after placement. A persistent Fabric chunk attachment stores exact generated
cells; a generation-only WorldGenRegion write guard prevents later lakes/vegetation from erasing them,
including after a partially generated chunk is saved. Normal Level/player writes are unaffected.

Initialize world settings lazily because spawn generation precedes SERVER_STARTED. Preserve planner-v1
snapshots and freeze Phase D config/styles separately in lost_railways_surface.json. Invalid snapshots
are preserved and disable generation. The instance generation switch now additionally acts as a kill
switch after restart; the immutable world switch must also allow generation. Existing blocks remain.

Validation adds unit, Minecraft chunk, and normal-noise-world end-to-end tests. CI runs generation-enabled
and generation-disabled client suites independently, retaining block/timing proofs. Performance proof
is deliberately a small cold-load sample; representative multi-seed overhead certification remains a
release check rather than an unsupported guarantee.

Final local verification: 39 JUnit tests, 18 enabled server GameTests, and 11 disabled-minecart
server GameTests passed. Native client suites passed with generation on and off; the Ubuntu 24.04
ARM64/Xvfb Docker suite passed in two minutes. The enabled macOS/Linux proofs contain identical
141 marked blocks including 64 rails in the sampled reference chunks, with matching forward/reverse
chunk-order output. Disabled generation produced zero marked blocks. The packaged JAR excludes tests.
Evidence lives in ignored build/phase-d-evidence/. Timing samples were not a controlled performance
comparison (the disabled run overlapped Docker work), so no overhead percentage is claimed.
