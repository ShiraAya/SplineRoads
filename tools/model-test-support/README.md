# Test-only model adapters — not Minecraft / not production code

The model runner compiles selected **actual production** classes against minimal adapters for their external dependencies. These adapters allow deterministic geometry, reference, serializer-schema and screen-state tests; they are not a copy of Minecraft, a working game server or an actual GUI renderer.

- CompoundTag / ListTag are typed in-memory maps/lists. Production codec read/write methods run, but Mojang NBT binary serialization does not.
- RoadData / RoadIndex expose only the in-memory records needed by the planner. Actual world writes, ownership, structure placement, networking, GPU and other unsupported operations fail fast rather than simulating success.
- Test widgets execute the production screen callbacks and resize state. They do not render text, dispatch real Minecraft input, or prove actual GUI classpath compatibility.
- The model runner explicitly lists the production world/client classes to compile. All `src/main` source files remain the real source; there is no replacement of those files in the artifact.

This directory and `tools/model-validation` are outside all formal Gradle source sets. Never include them in a mod JAR. Run `bash tools/test_ramp39_model.sh` from a Bash 4+ / JDK 17+ environment. It rebuilds the offline core first to prevent stale-class false passes.
