# Lost Railways

A Minecraft Java 26.3 Fabric mod about discovering, restoring, and reusing abandoned railways.

**Status: Phase A bootstrap.** The mod loads configuration; railway generation, minecart changes,
and Elytra balancing are not implemented yet. See [the specification](docs/plan/lost_railways_spec.md).

## Development

Use **JDK 25** and the checked-in **Gradle 9.8.2 wrapper**. No system Gradle installation is needed.
The game requires Java 25; development and CI deliberately use that version too.

```sh
./gradlew build                 # Compile, JUnit tests, server GameTests, JARs
./gradlew test                  # Fast configuration unit tests
./gradlew runGameTest           # Minecraft server integration tests
./gradlew runClientGameTest     # Client launch, single-player world, rendering screenshot
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
Fabric Loader 0.20.1+ and Fabric API 0.165.0+26.3 on Minecraft 26.3. This bootstrap has no custom
client networking; test both sides as features are added.

## Configuration

On first initialization, `config/lost_railways.json` is created in the game instance:

```json
{
  "schemaVersion": 1,
  "generationEnabled": true,
  "minecartOverhaulEnabled": true,
  "elytraBalanceEnabled": false
}
```

These are bootstrap switches only, not active gameplay features. Missing settings use defaults.
Malformed or unsupported configuration logs an error and falls back to defaults without overwriting
the original file. Restart to reload. World-specific generation snapshots will be added before any
world generation is implemented; global config must never silently re-plan existing worlds.

## Tests and CI

- `src/test`: pure JUnit tests for defaults, partial config, validation, and preservation of bad files.
- `src/gametest`: a separate test mod loaded only by Loom test runs. Server tests check initialization
  and vanilla rail placement; the client test creates a world, waits for rendering, and captures evidence.
- GitHub Actions runs build/server tests and the client smoke test with Xvfb on Linux. Reports and
  screenshots are uploaded even on failures. Unit and integration results live under `build/`.
- The **Release candidate** workflow builds a supplied version after both test layers pass and retains
  downloadable JARs. Public publishing is deferred until a license and distribution destinations
  (for example Modrinth/CurseForge) are selected and credentials configured.

The smoke tests establish the runtime harness, not acceptance of future minecart or worldgen behavior.
Extend them with deterministic graph tests, physics/placement integration tests, and multiplayer
end-to-end scenarios as those features land.

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
