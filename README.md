# Lost Railways

A Minecraft Java 26.3 Fabric mod about discovering, restoring, and reusing abandoned railways.

**Status: Phase B minecart overhaul.** Configurable minecart speeds and powered acceleration are implemented.
Railway generation and Elytra balancing remain future work. See [the specification](docs/plan/lost_railways_spec.md).

## Development

Use **JDK 25** and the checked-in **Gradle 9.8.2 wrapper**. No system Gradle installation is needed.
The game requires Java 25; development and CI deliberately use that version too.

```sh
./gradlew build                 # Compile, JUnit tests, server GameTests, JARs
./gradlew test                  # Fast configuration and movement unit tests
./gradlew runGameTest           # Minecart server integration tests
./gradlew runGameTest -PvanillaMinecartTests=true # Disabled-overhaul regression in a separate JVM
./gradlew runClientGameTest     # Rendering, riding, dedicated connection/reconnect
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
to apply changes; live movement-mode changes are intentionally unsupported. Generation and Elytra
switches remain reserved for later phases. World-specific generation snapshots will be added before
world generation is implemented.

## Tests and CI

- `src/test`: pure JUnit tests for config migration/validation, acceleration, speed caps, powered-time resets, and bounded chunk safety.
- `src/gametest`: a separate test mod loaded only by Loom test runs. Server tests exercise acceleration to 35 blocks/s, curves, slopes, solid/entity collisions, braking,
  loaded/unloaded chunk boundaries, and cargo preservation. A separate JVM verifies disabled-overhaul
  vanilla behavior. Client tests exercise riding in single-player and over a dedicated-server connection,
  position synchronization, reconnecting, and rendered screenshots.
- GitHub Actions runs build/server tests and the client smoke test with Xvfb on Linux. Reports and
  screenshots are uploaded even on failures. Unit and integration results live under `build/`.
- The **Release candidate** workflow builds a supplied version after both test layers pass and retains
  downloadable JARs. Public publishing is deferred until a license and distribution destinations
  (for example Modrinth/CurseForge) are selected and credentials configured.

See [the Phase B tester guide](docs/TESTING.md) for manual coverage and remaining compatibility/performance
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
