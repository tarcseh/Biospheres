# Biospheres (Fabric)

A recreation of Risugami's original Biospheres mod for modern Minecraft, with significant world generation improvements.

## Target version

Minecraft `1.21.11` on Fabric Loader `0.19.1` with Fabric API `0.141.3+1.21.11`.

## World creation

Create a new world and select `Biospheres` from the world preset list.

## Dedicated server

Set `level-type=biospheres:biospheres` in `server.properties` before generating a new world.

## Development

- Build: `./gradlew.bat build`
- Test client: `./gradlew.bat runClient`
- Test server: `./gradlew.bat runServer`

## What's new compared to the original

### Variable sphere sizes
Spheres now have randomized radii between 160 and 224 blocks (configurable). Each sphere gets a deterministic size based on its grid position and the world seed, creating visual variety while maintaining consistency.

### Glass containment shell
Each sphere is wrapped in a glass shell that separates it from the void. This creates the iconic biosphere look and prevents terrain from bleeding between spheres.

### Bridges between spheres
Adjacent spheres are connected by bridges that span the gap. Bridges feature:
- Smooth ramps that adjust for height differences between spheres
- 5x5 glass cutouts at sphere entrances for clean entryways
- Proper support structure underneath

### Structure containment system
All vanilla Overworld structures now generate properly inside spheres:
- **Villages** - Generate in plains, desert, savanna, snowy, and taiga biomes
- **Ancient Cities** - Generate in deep dark spheres (approximately 1 in 13 spheres)
- **Trial Chambers** - Underground structures that fit within sphere bounds
- **Strongholds** - Properly contained within suitable spheres
- **Woodland Mansions** - Large structures with dedicated sphere fitting
- **Trail Ruins** - Surface structures with proper biome placement
- **Ruined Portals** - All variants (desert, jungle, swamp, mountain, ocean)
- **Ocean Monuments & Ruins** - Contained within ocean biomes
- **Desert Pyramids** - Only in desert biomes
- **Swamp Huts** - Only in swamp biomes
- **Mineshafts** - Underground networks that respect sphere boundaries
- **Igloos** - Snowy biomes
- **Jungle Temples** - Jungle biomes
- **Pillager Outposts** - Various biomes

Structures will never:
- Spawn outside sphere boundaries
- Float in mid-air
- Generate in incompatible biomes

### Seed-driven variation
- Every world seed produces unique sphere layouts
- Biome distribution is deterministic per seed
- Deep dark spheres appear randomly (1 in 13) based on world seed
- Sphere sizes and positions vary by seed

### Performance improvements
- Optimized structure location lookups
- Cached noise samplers for height calculations
- Efficient bridge ramp calculations using closed-form solutions

### Biome support
All vanilla Overworld biomes are supported, including:
- Plains variants (sunflower, snowy)
- Forest variants (birch, dark, old growth, flower)
- Mountain variants (windswept hills, jagged peaks, etc.)
- Ocean variants (warm, lukewarm, cold, frozen, deep)
- Underground biomes (lush caves, dripstone caves)
- Special biomes (mushroom fields, deep dark)

## Configuration

The world preset supports these optional parameters in `biospheres.json`:

```json
{
  "sphere_distance": 480,      // Distance between sphere centers
  "min_sphere_radius": 160,    // Minimum sphere radius
  "max_sphere_radius": 224,    // Maximum sphere radius
  "lake_radius": 16,           // Lake size inside spheres
  "shore_radius": 6,           // Shore transition width
  "minimum_y": -64,            // World bottom
  "world_height": 384,         // Total world height
  "default_block": "minecraft:stone",
  "default_fluid": "minecraft:water",
  "default_bridge": "minecraft:oak_planks",
  "default_edge": "minecraft:oak_fence"
}
```

## Technical details

### Sphere layout
Spheres are arranged in a fixed grid with configurable spacing. Each sphere's properties (size, center Y position, biome) are deterministically derived from its grid coordinates and the world seed.

### Structure fitting
The mod measures vanilla structure footprints and ensures they only generate in spheres large enough to contain them completely. Structures that wouldn't fit are rejected before generation begins.

### Bridge generation
Bridges calculate the optimal path between two sphere surfaces using interpolation. Ramps are filled between different heights to create smooth transitions. Entrances are carved as 5x5 glass openings facing the sphere interior.

## License

GNU GPLv3