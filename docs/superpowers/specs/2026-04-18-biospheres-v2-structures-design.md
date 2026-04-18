# Biospheres v2 structures design

## Goal

Extend the Biospheres overworld generator so that each sphere uses a deterministic random radius, overworld structures generate only inside spheres, and every allowed structure fully fits inside its assigned sphere without floating, clipping, or extending past the shell.

The updated generator must:

- Keep spheres on a fixed grid
- Choose a deterministic per-sphere radius from a random range that starts at `20..160`
- Allow the maximum radius to increase if measurement shows that `160` cannot guarantee full containment for every vanilla overworld structure
- Keep Nether and End generation vanilla
- Restrict overworld structures to spheres only
- Prevent any overworld structure from generating partially outside a sphere
- Ensure large surface structures such as villages generate on terrain that supports them without floating or breaking apart

## Current baseline

The current project has a single custom chunk generator and a sphere math helper:

- `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`

The current implementation assumes one global sphere radius and one global sphere distance:

- `sphereDistance = 128`
- `sphereRadius = 32`

That fixed radius is used in all important paths:

- nearest sphere center lookup
- sphere center Y selection
- noise population and terrain fill
- height queries
- shell finishing
- bridge generation

This means variable sphere sizes and structure confinement cannot be added safely as isolated conditionals. The generator needs a shared sphere layout model that all worldgen decisions use.

## Confirmed scope

The accepted scope for this design is:

- Sphere centers stay on a fixed grid
- Each sphere gets a deterministic random radius
- The initial radius range is `20..160`
- If any vanilla overworld structure cannot be guaranteed to fit inside radius `160`, the maximum radius must be raised until the guarantee is true
- Only overworld structures are affected by the Biospheres rules
- Nether and End remain vanilla
- If a structure does not fit, it must not be assigned to that sphere
- The final system must support every vanilla overworld structure in some suitable sphere

## Recommended approach

Use a shared sphere descriptor model plus a centralized structure fit policy.

This is preferred over patching the current fixed-radius generator in place because the generator already uses `sphereRadius` in terrain, height, shell, and bridge code paths. Recomputing ad hoc radii in each call site would make the system inconsistent and hard to test.

The new model should produce one canonical description per sphere cell and reuse it everywhere:

- terrain generation
- surface shaping
- height queries
- shell generation
- bridge routing
- structure eligibility
- structure confinement checks

## Architecture

The work is split into five bounded areas.

### 1. Sphere layout model

Introduce a deterministic sphere descriptor for each fixed grid cell.

Each descriptor should contain at least:

- grid-aligned center X and Z
- derived center Y
- sphere radius
- any derived values needed by structure or terrain rules

The radius must be deterministic from the world seed and grid cell. The initial random range is `20..160`, but the implementation must treat the maximum as a configurable constant so it can be raised if structure measurement requires it.

The descriptor becomes the single source of truth for all geometry decisions. Any code path still relying on a shared global `sphereRadius` after this change would be a design bug.

### 2. Terrain generation based on per-sphere radius

Update terrain generation so every calculation uses the current sphere descriptor instead of a fixed radius.

This includes:

- terrain fill radius
- center Y selection
- lake placement bounds
- top-of-sphere height sampling
- shell placement
- bridge start and end geometry

Large and small spheres must remain deterministic and visually coherent. The terrain rules can evolve slightly where needed, but the recognizable Biospheres identity should remain: solid sphere interiors, a clear shell boundary, and cardinal bridges between neighboring spheres.

### 3. Structure fit policy

Add a centralized policy layer that defines which overworld structures can use which spheres.

Each structure family needs explicit fit requirements, including:

- horizontal footprint requirements
- vertical clearance requirements
- safety margin from the shell
- surface suitability rules for surface structures
- subsurface suitability rules for underground structures

This policy is not a best-effort guess. It is the contract that decides whether a sphere is eligible for a structure at all.

The policy must cover all vanilla overworld structure families relevant to this generator, including at minimum:

- villages
- pillager outposts
- woodland mansions
- ancient cities
- strongholds
- trial chambers
- ocean monuments
- mineshafts
- ruined portals
- desert pyramids
- jungle temples
- igloos
- witch huts and other smaller placed structures if they participate in overworld structure generation on the target version

If a structure uses multiple variants with different footprints, the policy must be based on the largest variant that can appear in normal vanilla play, plus safety margin.

### 4. Placement confinement

Overworld structures must only generate when their start position and full expected bounds remain inside the assigned sphere.

The confinement rules are:

- no overworld structure start outside a sphere
- no structure start in a sphere that fails its fit policy
- no structure piece chain that extends past the allowed sphere boundary
- no reliance on post-generation clipping or cleanup to enforce containment

The intended behavior is rejection before generation, not cleanup after generation.

If a sphere does not satisfy the requirements for a given structure family, that sphere simply cannot host that structure. Another suitable sphere can.

## Surface and underground suitability

Large surface structures need more than raw radius. They also need stable usable terrain.

For large surface structures, the assigned sphere must provide:

- enough horizontal build area near the upper hemisphere
- terrain that is not so steep that the structure floats or breaks apart
- consistent support under major foundation areas

For large underground structures, the assigned sphere must provide:

- enough stone volume below the top surface
- enough vertical band depth for the full structure body
- safe clearance from lakes, shell boundaries, and void edges where relevant

This means not every sphere needs to be a candidate for every structure family. The world should contain a mix of spheres suited to different kinds of structures while still allowing every vanilla overworld structure family somewhere in the world.

## Measuring vanilla structure bounds

The guarantee that every overworld structure can fit depends on real footprint data.

Before final radius limits and fit thresholds are locked, the implementation must measure or derive the effective maximum bounds of relevant vanilla overworld structures on the target version. This includes large jigsaw-driven structures whose maximum practical footprint can exceed naive assumptions.

The design requirement is:

- if the measured worst-case structure cannot be safely contained in radius `160`, raise the configured maximum sphere radius until safe containment is possible

The project must not claim universal fit guarantees without this measurement step.

## Data flow

The intended runtime flow is:

1. Resolve the sphere descriptor for the relevant grid cell.
2. Use that descriptor for terrain fill, center height, shell, bridges, and surface calculations.
3. When overworld structures are evaluated, consult the shared structure fit policy.
4. Reject any sphere that does not meet the structure family requirements.
5. Run confinement checks so the chosen structure start and expected occupied bounds stay inside the sphere.
6. Allow generation only when both policy and confinement checks pass.

This keeps all geometry-sensitive behavior anchored to the same deterministic sphere descriptor.

## Error handling and failure modes

Acceptable failure modes during development:

- a structure family is temporarily disabled until its fit thresholds are properly measured
- tests fail because measured bounds exceed an outdated sphere maximum

Unacceptable runtime outcomes:

- overworld structures generating in the void between spheres
- structures clipped by the shell
- floating or broken large surface villages caused by unsuitable terrain
- structures assigned to spheres that are too small for their maximum variant
- inconsistent sphere geometry where terrain, shell, heightmap, and structures disagree about the active radius

The implementation should prefer explicit rejection of invalid placements over silent malformed generation.

## Testing strategy

Verification must prove the containment rules, not just spot-check visuals.

### Unit coverage

- deterministic radius selection for a given seed and grid cell
- deterministic center Y selection for different radii
- sphere descriptor consistency across repeated calls
- structure fit threshold evaluation for representative large and small structures
- confinement math for starts near the shell boundary

### Integration coverage

- generated chunks use the correct per-sphere radius in terrain and shell generation
- overworld structures do not start outside spheres
- large eligible surface structures only appear in spheres that meet surface suitability rules
- large eligible underground structures only appear in spheres that meet underground suitability rules
- neighboring systems such as bridges and height queries remain consistent with the same sphere descriptor

### Regression focus

- villages with large layouts still have solid support and remain fully inside the sphere
- ancient cities and other large underground structures remain fully inside stone volume
- mineshafts only appear in eligible underground spheres and do not leak outside the shell
- no overworld structure appears in the void gap between spheres
- raising the maximum sphere radius does not break deterministic layout behavior

## Out of scope

The following are outside this design unless needed to support the new guarantees:

- changes to Nether generation
- changes to End generation
- user-facing configuration UI for sphere size ranges
- unrelated refactors outside the Biospheres worldgen path

## Success criteria

The design is successful when a Biospheres overworld can generate spheres with deterministic random sizes, every vanilla overworld structure family appears only inside suitable spheres, no structure extends outside a sphere, and large surface structures such as villages have terrain support that keeps them grounded and fully contained.
