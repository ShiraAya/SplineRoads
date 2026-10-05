# Offline core-test support — not production code

`RoadSignCatalog` here is a test-only shim because Gson / the normal dependency chain is unavailable offline. It deliberately fails for real sign catalogue usage. The road geometry tests do not use that feature. The real source in `src/main/java` is unchanged and remains in the production build.

This directory is not part of a Gradle source set and must never be copied into the mod JAR or `src/main`. `test_ramp39.sh` explicitly compiles the shim only into its isolated test output. Passing that runner says nothing about sign rendering, Gson integration, Forge compilation or a Minecraft client.
