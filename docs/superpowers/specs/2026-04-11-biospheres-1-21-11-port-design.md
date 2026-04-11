# Biospheres 1.21.11 port design

## Goal

Port the mod from Minecraft 1.16.2 Fabric to Minecraft 1.21.11 Fabric while preserving the current playable behavior for newly created worlds.

The port must:

- Build against Minecraft `1.21.11`, Yarn `1.21.11+build.4`, Fabric Loader `0.19.1`, and Fabric API `0.141.3+1.21.11`
- Expose Biospheres as a normal selectable world preset during world creation
- Generate the same style of worlds in singleplayer and on dedicated servers
- Preserve the current terrain rules, including sphere layout, biome assignment, glass shells, lakes, and bridges

The port does not need to preserve compatibility with worlds created on `1.16.x`.

## Current baseline

The current mod is a 1.16.2 Fabric project using Loom `0.4-SNAPSHOT`, Gradle `6.5`, and the old 1.16 worldgen APIs.

The existing terrain behavior is defined by two custom worldgen components:

- `BiospheresBiomeSource`
- `BiospheresChunkGenerator`

The current implementation uses the following visible rules:

- Spheres are arranged on a grid with `sphereDistance = 128`
- Sphere radius is `32`
- Sphere center height is derived from the world seed and grid cell position
- Each sphere is assigned one biome selected from a curated biome list
- The area outside the playable sphere band resolves to void
- Sphere interiors are filled with a stone-and-noise terrain mass
- A lake may appear near the center of the sphere based on the same seed-derived rules
- A glass shell is added in a later finishing pass
- Bridges are generated in the four cardinal directions to neighboring spheres

These rules define the behavior that the port must preserve.

## Recommended approach

Use a full native 1.21.11 worldgen port.

This is preferred over a compatibility shim or approximate vanilla-noise solution because the gap between 1.16 and 1.21 worldgen APIs is too large for a safe minimal patch, and the user requirement is behavior preservation rather than a rough recreation.

The port should keep the current generation math and rebuild the integration points around modern registries, codecs, and world preset wiring.

## Architecture

The work is split into four bounded areas.

### 1. Build and toolchain upgrade

Upgrade the project to the modern Fabric toolchain required for 1.21.11:

- Update Gradle wrapper to a version supported by modern Loom
- Replace Loom `0.4-SNAPSHOT` with a current Loom version compatible with 1.21.11
- Update `gradle.properties` to the approved Minecraft, Yarn, Loader, and Fabric API versions
- Update Java compatibility to the Java version required by the target stack
- Replace obsolete repository configuration such as `jcenter()` if still present

This stage only prepares the project to compile and run on the new toolchain.

### 2. Worldgen core port

Rebuild the custom worldgen classes against the modern APIs while preserving the old logic.

#### `BiospheresBiomeSource`

The biome source must:

- Keep the world seed as serialized state
- Keep the curated biome list, updated to modern biome registry access patterns
- Preserve the existing sphere-distance calculation logic used to determine whether a sample is inside a sphere band
- Preserve the per-sphere deterministic biome selection from the world seed
- Return a void biome outside the sphere area

The exact internal implementation can change to fit 1.21.11 APIs, but the selection behavior must remain equivalent.

#### `BiospheresChunkGenerator`

The chunk generator must keep the current terrain rules:

- Seeded sphere center placement on the same grid
- Sphere radius and spacing semantics
- Seed-based sphere Y placement
- Noise-influenced stone interior generation
- Lake carving and fluid selection logic
- Glass shell finishing pass
- Four-way bridge generation to neighboring spheres

The chunk generator must be defined through a codec-backed registration compatible with the modern chunk generator registry.

The implementation should preserve current tunables as explicit fields, even if they remain fixed by the preset for now. That keeps the generator state clear and makes the codec stable.

### 3. World preset and registration

The current mod registers worldgen components directly during common initialization. For 1.21.11, the generator must be integrated through the modern worldgen registration path.

The port must provide:

- Registration for the custom biome source codec
- Registration for the custom chunk generator codec
- A Biospheres world preset that appears in the normal Create World flow
- Registry wiring that works on both integrated and dedicated servers

The registration path must avoid client-only classes so the same mod jar works on servers.

### 4. Verification

The port is only complete when the following are true:

- The project builds successfully on the target toolchain
- The game starts with the mod installed
- Biospheres appears as a selectable world preset in Create World
- A newly created singleplayer Biospheres world generates the expected terrain
- A dedicated server can create or load a 1.21.11 Biospheres world using the same mod
- Sample generated chunks show the expected sphere spacing, shell behavior, bridges, lakes, and biome variation

## Data flow

The intended runtime flow is:

1. The user selects the Biospheres world preset during world creation.
2. The preset constructs the custom chunk generator using codec-backed worldgen registration.
3. The chunk generator uses the Biospheres biome source for deterministic sphere-biome selection.
4. During chunk generation, the generator creates the interior terrain mass using the preserved sphere and noise math.
5. The finishing pass adds the outer shell and bridge structures.
6. Vanilla biome features run on top of the sphere biome where the modern pipeline allows them to do so.

This keeps the custom terrain rules in the mod while still fitting into the standard world creation and server generation flow.

## Dedicated server support

Dedicated server support is an explicit requirement.

To satisfy it:

- All worldgen registrations must happen in common code
- No client-only world preset wiring can be required for actual generation
- The preset and generator must resolve through registries on the server
- Terrain generation must be fully deterministic from the world seed and generator config

If these conditions are met, the same generator logic will work in singleplayer and on a dedicated server.

## Error handling and failure modes

The port should fail fast for broken registration or codec wiring.

Acceptable failure mode:

- Startup or world creation fails with a clear registry or codec error

Unacceptable failure modes:

- Silent fallback to vanilla overworld terrain
- Client-only success while dedicated server creation fails
- World preset visible in UI but unusable at generation time

The implementation should prefer explicit errors over partial fallback behavior.

## Testing strategy

Verification should cover both build-level and runtime behavior.

### Build verification

- `gradlew build` succeeds
- Produced mod jar includes the expected metadata for the updated target versions

### Runtime verification

- Client startup succeeds
- Dedicated server startup succeeds
- Biospheres world preset is visible in Create World
- A fresh Biospheres world produces void gaps between spheres
- Spheres contain terrain and biome variation
- Spheres receive the glass shell finishing pass
- Neighboring spheres are connected by the bridge pass
- Lake behavior still occurs under the same seeded rules

### Regression focus

The port should specifically check for these likely regressions:

- Incorrect biome registry lookups causing missing or null biomes
- Generator registration mismatches causing world creation failure
- Surface or feature generation changes that remove biome decoration entirely
- Height and carving pipeline differences that break the shell or leave sphere interiors empty
- Dedicated server failures caused by client-only preset integration

## Out of scope

The following are not part of this port unless required to make the mod function on 1.21.11:

- Compatibility with previously generated 1.16 worlds
- New gameplay features
- Config systems for user-editable sphere parameters
- Major refactors unrelated to the 1.21.11 port

## Implementation notes

- Preserve behavior by porting the existing math, not by replacing it with a new noise settings approach
- Keep changes as small as practical once the modern registration path is in place
- Remove obsolete code paths and imports that only existed for the 1.16 APIs
- Update metadata such as `fabric.mod.json` and README version references as part of the port

## Success criteria

The port is successful when a user can install the updated mod on Minecraft 1.21.11 Fabric, select Biospheres during world creation, and get newly generated worlds that match the current mod's recognizable biosphere terrain behavior in both singleplayer and dedicated server environments.
