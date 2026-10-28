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
