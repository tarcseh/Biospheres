# Biospheres biome terrain design

## Goal

Make each biome sphere use terrain that follows the selected biome's vanilla terrain character while still keeping all generated terrain strictly inside the sphere volume.

The updated generator must:

- Keep the current sphere-based world layout
- Keep `the_void` outside spheres
- Preserve per-sphere biome assignment
- Let mountainous biomes produce mountainous terrain inside their spheres
- Let flatter biomes stay flatter inside their spheres
- Allow biome-appropriate lakes and similar terrain-driven features to appear inside spheres
- Prevent any terrain from spilling outside the sphere shell

## Current baseline

The current v2 generator already has deterministic sphere layout and per-sphere biome assignment, but terrain shape is still driven by a custom sphere fill algorithm in `BiospheresChunkGenerator`.

Today the generator:

- Fills each sphere with a simple thresholded noise formula
- Cuts a central cavity for a custom lake based on multi-noise sampling
- Applies biome-dependent top and under blocks in `buildSurface`
- Uses `the_void` outside the sphere band

This means the chosen biome mostly affects surface materials and later decoration, but not the large-scale terrain silhouette. A mountain biome sphere does not naturally become mountain-shaped in the same way vanilla terrain would.

## Confirmed scope

The accepted scope for this change is:

- Terrain inside a sphere should follow the selected biome's vanilla terrain tendency as closely as practical
- This applies only inside spheres
- Outside spheres stays `the_void`
- The system should support biome-driven vertical variation such as mountains, hills, flatter plains, and terrain depressions that can host lakes
- The result should remain recognizably Biospheres rather than turning into a normal continuous overworld

## Approaches considered

### 1. Recommended: mask vanilla-like terrain into the sphere volume

Use the selected biome and the vanilla noise pipeline to derive terrain shape tendencies, then intersect that terrain with the sphere volume.

Why this is preferred:

- It produces terrain that more closely matches the chosen biome without hand-writing biome rules
- It scales across many vanilla biomes instead of needing special cases for each one
- It preserves the main Biospheres constraint because the sphere boundary remains the final hard limit

### 2. Extend the custom terrain formula with biome-specific tuning

Keep the current generator shape logic and add biome tags or exceptions that increase relief for mountain biomes and enable lakes for selected biomes.

Why this was rejected:

- It would be smaller in code size, but not truly vanilla-like
- It would require ongoing manual tuning as more biomes are considered
- It would still make biome terrain behavior approximate rather than data-driven

### 3. Fully delegate terrain generation to a vanilla noise generator, then clip outside spheres

Generate a normal overworld chunk first and erase everything outside spheres.

Why this was not chosen as the primary direction:

- It is closest to vanilla behavior, but also the riskiest integration because the current generator has custom shell, bridge, and structure rules that depend on owning the terrain pass
- It is more likely to produce edge mismatches at the sphere boundary without additional containment logic

## Recommended design

Use a hybrid approach where `BiospheresChunkGenerator` remains the world generator, but its terrain population becomes a sphere-constrained interpretation of vanilla-like terrain rather than a custom solid fill formula.

The sphere stays the primary geometric container. Vanilla-like terrain shape becomes the interior content.

## Architecture

### Generator ownership

`BiospheresChunkGenerator` remains the main generator so the existing Biospheres-specific responsibilities stay in one place:

- sphere descriptor lookup
- shell containment
- bridges
- structure routing and confinement
- outside-of-sphere void behavior

The generator should stop treating every sphere as a mostly solid mass with a simple top surface. Instead, it should derive the usable terrain profile from the biome's terrain behavior and only place blocks where both conditions are true:

- the position belongs to the biome-shaped terrain body
- the position lies inside the sphere bounds

### Terrain source

The generator should reuse vanilla-compatible terrain signals wherever available through the current worldgen APIs, rather than inventing a new terrain classification system.

The important design rule is not that every internal method must literally call the same vanilla implementation, but that the resulting height and density trends come from the same kinds of biome-sensitive inputs that make vanilla mountains high and vanilla plains low.

In practice this means the generator should derive terrain height, relief, and water-hosting depressions from the biome-aware noise configuration instead of only from the current custom per-column threshold.

### Sphere masking

The final block placement must always be masked by the active sphere descriptor.

That means:

- no solid terrain outside the sphere radius
- no lakes or fluids outside the sphere radius
- no terrain continuation into neighboring void space
- no relaxing of the sphere shell just to make vanilla terrain fit

The sphere boundary is still authoritative. Vanilla-like terrain only exists where the sphere permits it.

## Data flow

The intended runtime flow is:

1. Resolve the active `BiospheresSphereDescriptor` for the current column.
2. Resolve the sphere biome through the existing biome source.
3. Sample biome-aware terrain shaping inputs from the world noise configuration.
4. Compute the terrain body that the biome would naturally tend toward for that column.
5. Intersect that terrain body with the sphere volume.
6. Write blocks only for the intersected result.
7. Apply surface materials using the resolved biome.
8. Keep all blocks outside the sphere as air and biome as `the_void`.

## Lakes and fluid behavior

The current generator has a custom central lake rule. That rule conflicts with the goal of letting lakes appear where the biome terrain naturally supports them.

The updated design should shift lake behavior toward biome-driven terrain rather than guaranteed central cavities.

Requirements:

- Biomes that can naturally support lakes or water depressions should be able to form them inside spheres
- Lava lake behavior should remain possible only where the biome or terrain logic supports it
- Any leftover custom center-lake rule should either be removed or reduced so it does not dominate the biome-shaped terrain

If a compatibility bridge is needed during migration, it should be treated as temporary and kept subordinate to the biome-driven terrain model.

## Surface behavior

`buildSurface` should continue to use the resolved sphere biome, but surface painting must align with the new terrain profile.

This means:

- top and under materials still come from biome-sensitive rules
- steeper or higher terrain inside mountain spheres should receive the same kind of biome identity as the surrounding terrain shape
- flatter biomes should not receive artificial relief just because the sphere volume allows it

If the current hand-written top and under block rules become too inaccurate once terrain becomes more vanilla-like, they can be replaced or tightened later. That is secondary to getting the height and density profile correct first.

## Interaction with existing Biospheres systems

### Sphere layout

The existing shared sphere descriptor and layout system remains the source of truth for geometry. This change should not replace sphere layout, only the way terrain is filled inside that layout.

### Structures

Existing structure confinement rules stay in place. The change here is that the supporting terrain inside eligible spheres should be more biome-appropriate, which should help large surface structures sit more naturally when they generate.

### Bridges and shell

Bridges and shell rules remain Biospheres-specific and should continue to win over vanilla-like terrain wherever they intersect. Terrain shaping should respect these constraints rather than rewriting them.

## Error handling and failure modes

Acceptable short-term behavior during migration:

- some biome terrain profiles are only approximate while the terrain source integration is being tuned
- custom center lakes remain partially active for one iteration if they do not break the final masking rules

Unacceptable runtime behavior:

- terrain appearing outside spheres
- mountain biomes still looking effectively flat because the terrain pipeline did not actually become biome-sensitive
- biome-driven lakes cutting through the sphere shell
- shell, bridge, heightmap, and actual terrain disagreeing about where the sphere ends

When there is a conflict between vanilla-like terrain and sphere containment, containment must win.

## Testing strategy

Verification should focus on geometry and biome differentiation, not just codec survival.

### Unit coverage

- terrain masking keeps all generated solid blocks inside the sphere radius
- biome-sensitive terrain calculations produce meaningfully different height profiles for contrasting biome inputs
- lake-supporting biome inputs can create interior depressions or fluid-hosting shapes without crossing the sphere boundary
- height queries remain consistent with the actual populated terrain

### Integration checks

- generate representative chunks for at least one mountainous biome sphere and one flat biome sphere, then compare resulting surface ranges
- verify that outside-of-sphere positions remain empty and still resolve to `the_void`
- verify that existing preset decoding still succeeds

### Manual validation

- inspect mountainous spheres in game to confirm visible ridges or elevation changes
- inspect flatter spheres to confirm noticeably lower relief
- inspect biomes that commonly allow lakes to confirm that lakes can appear naturally inside spheres without breaching the shell

## Out of scope

This design does not require:

- changing Nether generation
- changing End generation
- replacing the per-sphere biome assignment model
- making the world outside spheres generate as a normal overworld

## Success criteria

This change is successful when:

- sphere interiors visibly reflect the selected biome's terrain character
- mountainous biomes can generate mountainous interiors
- lake-capable biomes can generate lakes or lake-like depressions inside spheres
- all terrain remains strictly confined to sphere interiors
- `the_void` remains outside spheres
- the generator still behaves like Biospheres rather than a clipped normal overworld
