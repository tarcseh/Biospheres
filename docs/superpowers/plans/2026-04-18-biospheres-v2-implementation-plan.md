# Biospheres v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic per-sphere random sizing, keep Nether and End vanilla, and make every vanilla Overworld structure generate only inside suitable spheres with full containment and grounded terrain support.

**Architecture:** Replace the fixed-radius assumptions with a shared sphere descriptor layer that every biome, terrain, shell, bridge, and structure decision uses. Add a structure fit policy and confinement layer so structure starts are rejected before generation unless the target sphere is large enough and the expected bounds remain fully inside the sphere.

**Tech Stack:** Java 21, Fabric Loom, Minecraft 1.21.11 worldgen APIs, JUnit 5

---

## File map

### Existing files to modify

- `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`
  Responsibility: deterministic grid math, seed mixing, radius and center selection helpers.
- `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
  Responsibility: sphere-band biome assignment and void fallback.
- `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
  Responsibility: terrain fill, surface, shell, bridges, and structure-aware generation hooks.
- `src/main/java/xyz/coolsa/biosphere/Biospheres.java`
  Responsibility: codec registration for any new worldgen support classes if registration is required.
- `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`
  Responsibility: preset wiring for generator and biome source codec fields.
- `src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java`
  Responsibility: low-level deterministic math regression tests.

### New files to create

- `src/main/java/xyz/coolsa/biosphere/BiospheresSphereDescriptor.java`
  Responsibility: immutable per-sphere geometry record.
- `src/main/java/xyz/coolsa/biosphere/BiospheresSphereLayout.java`
  Responsibility: shared factory and query layer for descriptors, neighbor lookup, radius bounds, and Y placement.
- `src/main/java/xyz/coolsa/biosphere/BiospheresStructureFamily.java`
  Responsibility: normalized structure family identifiers used by policies and tests.
- `src/main/java/xyz/coolsa/biosphere/BiospheresStructureFit.java`
  Responsibility: fit requirement record for horizontal radius, vertical clearance, shell margin, and suitability flags.
- `src/main/java/xyz/coolsa/biosphere/BiospheresStructurePolicy.java`
  Responsibility: centralized mapping from structure family to fit requirements and suitability logic.
- `src/main/java/xyz/coolsa/biosphere/BiospheresStructureConfinement.java`
  Responsibility: bounding and containment checks for starts and piece chains.
- `src/main/java/xyz/coolsa/biosphere/BiospheresStructureBoundsCatalog.java`
  Responsibility: measured vanilla structure maxima and safety margins.
- `src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java`
  Responsibility: descriptor determinism and radius range tests.
- `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`
  Responsibility: structure family fit and eligibility tests.
- `src/test/java/xyz/coolsa/biosphere/BiospheresStructureConfinementTest.java`
  Responsibility: sphere containment tests for expected structure bounds.
- `src/test/java/xyz/coolsa/biosphere/BiospheresWorldPresetCodecTest.java`
  Responsibility: codec and preset decode regression coverage.

### Optional support files if needed after API inspection

- `src/main/java/xyz/coolsa/biosphere/BiospheresStructureRouting.java`
  Responsibility: API adapter between Minecraft structure starts and the policy layer.
- `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorStructureRulesTest.java`
  Responsibility: integration tests around generator-facing structure gate logic.

## Phase overview

### Phase 1: Shared sphere layout foundation

Replace the hard-coded single-radius assumption with a shared descriptor model used everywhere.

### Phase 2: Terrain, biome, shell, and bridge migration

Move existing generator behavior onto the descriptor model without changing the Nether or End preset wiring.

### Phase 3: Structure measurement and policy catalog

Create the structure-family abstraction, record measured maxima, and define eligibility requirements.

### Phase 4: Placement confinement and generator integration

Hook the fit and confinement rules into the Overworld structure path so structures only generate in suitable spheres.

### Phase 5: Verification and tuning

Add regression coverage, validate the world preset, and tune the final max radius if structure measurements require it.

## Phase 1: Shared sphere layout foundation

### Task 1: Add a sphere descriptor record

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereDescriptor.java`
- Test: none yet

- [ ] **Step 1: Write the descriptor record**

```java
package xyz.coolsa.biosphere;

import net.minecraft.util.math.BlockPos;

public record BiospheresSphereDescriptor(
    int centerX,
    int centerY,
    int centerZ,
    int radius,
    int shellRadius,
    int bridgeRadius,
    int lakeRadius
) {
    public BlockPos centerPos() {
        return new BlockPos(this.centerX, this.centerY, this.centerZ);
    }
}
```

- [ ] **Step 2: Compile to catch syntax or import mistakes**

Run: `./gradlew compileJava`
Expected: compile may fail because the descriptor is not used yet, but the new file itself should compile cleanly if the failure list is empty for this file.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresSphereDescriptor.java
git commit -m "feat(worldgen): add sphere descriptor model"
```

### Task 2: Extend sphere math for deterministic radius selection

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java`

- [ ] **Step 1: Add failing tests for deterministic radius and configurable max range**

```java
@Test
void picksStableRadiusForSphereCoordinates() {
    int first = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 160);
    int second = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 160);

    assertEquals(first, second);
    assertTrue(first >= 20);
    assertTrue(first <= 160);
}

@Test
void supportsRaisedMaximumRadiusWhenPoliciesNeedIt() {
    int radius = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 224);

    assertTrue(radius >= 20);
    assertTrue(radius <= 224);
}
```

- [ ] **Step 2: Run the focused test class and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`
Expected: FAIL with missing `pickRadiusForSphere` method.

- [ ] **Step 3: Implement radius selection helpers in `BiospheresSphereMath`**

```java
public static int pickRadiusForSphere(int centerX, int centerZ, int minRadius, int maxRadius) {
    if (maxRadius < minRadius) {
        throw new IllegalArgumentException("maxRadius must be >= minRadius");
    }

    long mixed = mixSphereSeed(centerX, centerZ);
    int radiusSpan = maxRadius - minRadius + 1;
    return minRadius + Math.floorMod((int) mixed, radiusSpan);
}

public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int minRadius, int maxRadius, int extraRadius) {
    int blockX = biomeX * 4;
    int blockZ = biomeZ * 4;
    int centerX = nearestCenter(blockX, sphereDistance);
    int centerZ = nearestCenter(blockZ, sphereDistance);
    int radius = pickRadiusForSphere(centerX, centerZ, minRadius, maxRadius);
    double dx = centerX - blockX;
    double dz = centerZ - blockZ;
    return Math.sqrt(dx * dx + dz * dz) < radius + extraRadius;
}
```

- [ ] **Step 4: Keep the existing single-radius overload during migration**

```java
public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int sphereRadius, int extraRadius) {
    return isInsideSphereBand(biomeX, biomeZ, sphereDistance, sphereRadius, sphereRadius, extraRadius);
}
```

- [ ] **Step 5: Re-run the focused tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java
git commit -m "feat(worldgen): add deterministic sphere radius math"
```

### Task 3: Add the shared layout service

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereLayout.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java`

- [ ] **Step 1: Write failing tests for descriptor determinism and neighbor lookup**

```java
@Test
void resolvesStableDescriptorForGridCell() {
    BiospheresSphereLayout layout = new BiospheresSphereLayout(128, 20, 160, 16, 6, -64, 384);

    BiospheresSphereDescriptor first = layout.resolve(128, 256);
    BiospheresSphereDescriptor second = layout.resolve(128, 256);

    assertEquals(first, second);
    assertTrue(first.radius() >= 20);
    assertTrue(first.radius() <= 160);
}

@Test
void resolvesCardinalNeighborsOnTheFixedGrid() {
    BiospheresSphereLayout layout = new BiospheresSphereLayout(128, 20, 160, 16, 6, -64, 384);

    BiospheresSphereDescriptor center = layout.resolve(0, 0);
    BiospheresSphereDescriptor east = layout.resolveNeighbor(center, 1, 0);

    assertEquals(center.centerX() + 128, east.centerX());
    assertEquals(center.centerZ(), east.centerZ());
}
```

- [ ] **Step 2: Run the new test class and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresSphereLayoutTest`
Expected: FAIL because the layout class does not exist.

- [ ] **Step 3: Implement the layout service**

```java
package xyz.coolsa.biosphere;

public final class BiospheresSphereLayout {
    private final int sphereDistance;
    private final int minRadius;
    private final int maxRadius;
    private final int lakeRadius;
    private final int shoreRadius;
    private final int minimumY;
    private final int worldHeight;

    public BiospheresSphereLayout(int sphereDistance, int minRadius, int maxRadius, int lakeRadius, int shoreRadius, int minimumY, int worldHeight) {
        this.sphereDistance = sphereDistance;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.lakeRadius = lakeRadius;
        this.shoreRadius = shoreRadius;
        this.minimumY = minimumY;
        this.worldHeight = worldHeight;
    }

    public BiospheresSphereDescriptor resolveForBlock(int blockX, int blockZ) {
        int centerX = BiospheresSphereMath.nearestCenter(blockX, this.sphereDistance);
        int centerZ = BiospheresSphereMath.nearestCenter(blockZ, this.sphereDistance);
        return this.resolve(centerX, centerZ);
    }

    public BiospheresSphereDescriptor resolve(int centerX, int centerZ) {
        int radius = BiospheresSphereMath.pickRadiusForSphere(centerX, centerZ, this.minRadius, this.maxRadius);
        int centerY = BiospheresSphereMath.pickCenterYForSphere(centerX, centerZ, radius, this.minimumY, this.worldHeight);
        int shellRadius = radius + 1;
        int bridgeRadius = Math.max(1, radius - 2);
        int lakeRadius = Math.min(this.lakeRadius, Math.max(4, radius / 2));
        return new BiospheresSphereDescriptor(centerX, centerY, centerZ, radius, shellRadius, bridgeRadius, lakeRadius);
    }

    public BiospheresSphereDescriptor resolveNeighbor(BiospheresSphereDescriptor sphere, int xOffset, int zOffset) {
        return this.resolve(sphere.centerX() + xOffset * this.sphereDistance, sphere.centerZ() + zOffset * this.sphereDistance);
    }
}
```

- [ ] **Step 4: Run the focused tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresSphereLayoutTest --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresSphereLayout.java src/main/java/xyz/coolsa/biosphere/BiospheresSphereDescriptor.java src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java
git commit -m "feat(worldgen): add shared sphere layout service"
```

## Phase 2: Terrain, biome, shell, and bridge migration

### Task 4: Migrate biome source to variable-radius sphere band checks

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
- Modify: `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresWorldPresetCodecTest.java`

- [ ] **Step 1: Write a failing codec regression test for new biome source fields**

```java
@Test
void decodesBiomeSourceWithRadiusRangeFields() {
    String json = """
        {
          "type": "biospheres:sphere_biomes",
          "sphere_distance": 128,
          "min_sphere_radius": 20,
          "max_sphere_radius": 160,
          "void_biome": "minecraft:the_void",
          "biomes": ["minecraft:plains"]
        }
        """;

    assertDoesNotThrow(() -> decodeBiomeSource(json));
}
```

- [ ] **Step 2: Run the focused codec test and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresWorldPresetCodecTest`
Expected: FAIL because the codec does not accept `min_sphere_radius` or `max_sphere_radius`.

- [ ] **Step 3: Replace fixed biome radius fields with range fields**

```java
public static final MapCodec<BiospheresBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    RegistryFixedCodec.of(RegistryKeys.BIOME).listOf().fieldOf("biomes").forGetter(BiospheresBiomeSource::getSphereBiomes),
    RegistryFixedCodec.of(RegistryKeys.BIOME).fieldOf("void_biome").forGetter(BiospheresBiomeSource::getVoidBiome),
    Codec.INT.optionalFieldOf("sphere_distance", 128).forGetter(BiospheresBiomeSource::getSphereDistance),
    Codec.INT.optionalFieldOf("min_sphere_radius", 20).forGetter(BiospheresBiomeSource::getMinSphereRadius),
    Codec.INT.optionalFieldOf("max_sphere_radius", 160).forGetter(BiospheresBiomeSource::getMaxSphereRadius)
).apply(instance, BiospheresBiomeSource::new));
```

- [ ] **Step 4: Use the new radius range in biome band checks**

```java
@Override
public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
    if (!BiospheresSphereMath.isInsideSphereBand(x, z, this.sphereDistance, this.minSphereRadius, this.maxSphereRadius, 6)) {
        return this.voidBiome;
    }

    int centerX = BiospheresSphereMath.nearestCenter(x * 4, this.sphereDistance);
    int centerZ = BiospheresSphereMath.nearestCenter(z * 4, this.sphereDistance);
    int index = BiospheresSphereMath.pickIndexForSphere(centerX, centerZ, this.biomes.size());
    return this.biomes.get(index);
}
```

- [ ] **Step 5: Update the world preset biome source JSON**

```json
"biome_source": {
  "type": "biospheres:sphere_biomes",
  "sphere_distance": 128,
  "min_sphere_radius": 20,
  "max_sphere_radius": 160,
  "void_biome": "minecraft:the_void",
  "biomes": [
    "minecraft:plains"
  ]
}
```

- [ ] **Step 6: Re-run the codec and math tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresWorldPresetCodecTest --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json src/test/java/xyz/coolsa/biosphere/BiospheresWorldPresetCodecTest.java
git commit -m "feat(worldgen): support sphere radius ranges in biome source"
```

### Task 5: Migrate chunk generator codec and state to range-based layout

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Modify: `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`

- [ ] **Step 1: Write the failing generator codec changes**

```java
Codec.INT.optionalFieldOf("min_sphere_radius", 20).forGetter(BiospheresChunkGenerator::getMinSphereRadius),
Codec.INT.optionalFieldOf("max_sphere_radius", 160).forGetter(BiospheresChunkGenerator::getMaxSphereRadius),
```

- [ ] **Step 2: Replace the fixed `sphereRadius` field with range fields and a layout field**

```java
private final int minSphereRadius;
private final int maxSphereRadius;
private final BiospheresSphereLayout sphereLayout;

this.minSphereRadius = minSphereRadius;
this.maxSphereRadius = maxSphereRadius;
this.sphereLayout = new BiospheresSphereLayout(sphereDistance, minSphereRadius, maxSphereRadius, lakeRadius, shoreRadius, minimumY, worldHeight);
```

- [ ] **Step 3: Update constructor and getters**

```java
public int getMinSphereRadius() {
    return this.minSphereRadius;
}

public int getMaxSphereRadius() {
    return this.maxSphereRadius;
}
```

- [ ] **Step 4: Update the world preset generator JSON fields**

```json
"generator": {
  "type": "biospheres:biosphere",
  "sphere_distance": 128,
  "min_sphere_radius": 20,
  "max_sphere_radius": 160,
  "lake_radius": 16,
  "shore_radius": 6,
  "minimum_y": -64,
  "world_height": 384
}
```

- [ ] **Step 5: Run compilation to expose all fixed-radius call sites**

Run: `./gradlew compileJava`
Expected: FAIL on remaining `sphereRadius` references in the generator.

- [ ] **Step 6: Commit the codec and state migration once compilation is restored later in this phase**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json
git commit -m "refactor(worldgen): switch generator state to sphere ranges"
```

### Task 6: Move terrain fill and height queries onto descriptors

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java`

- [ ] **Step 1: Add a failing test for top height consistency with descriptor radius**

```java
@Test
void computesTopHeightFromDescriptorRadius() {
    BiospheresSphereLayout layout = new BiospheresSphereLayout(128, 20, 160, 16, 6, -64, 384);
    BiospheresSphereDescriptor sphere = layout.resolve(128, 256);

    int topY = sphere.centerY() + sphere.radius();

    assertTrue(topY > sphere.centerY());
}
```

- [ ] **Step 2: Replace center lookup methods with descriptor lookup methods**

```java
private BiospheresSphereDescriptor getNearestSphere(NoiseConfig noiseConfig, int x, int z) {
    return this.sphereLayout.resolveForBlock(x, z);
}

private BiospheresSphereDescriptor getNearestSphere(long seed, int x, int z) {
    return this.sphereLayout.resolveForBlock(x, z);
}
```

- [ ] **Step 3: Update noise population to use `sphere.radius()` and `sphere.centerPos()`**

```java
BiospheresSphereDescriptor sphere = this.getNearestSphere(noiseConfig, chunkPos.getCenterX(), chunkPos.getCenterZ());
BlockPos center = sphere.centerPos();

if (radialDistance > sphere.radius()) {
    continue;
}

double sphereHalfHeight = Math.sqrt((double) sphere.radius() * sphere.radius()
    - (center.getX() - x) * (double) (center.getX() - x)
    - (center.getZ() - z) * (double) (center.getZ() - z));
```

- [ ] **Step 4: Update lake calculations to use the sphere descriptor lake radius**

```java
private BlockState getLakeBlock(NoiseConfig noiseConfig, BiospheresSphereDescriptor sphere) {
    MultiNoiseUtil.NoiseValuePoint point = noiseConfig.getMultiNoiseSampler().sample(sphere.centerX() >> 2, sphere.centerY() >> 2, sphere.centerZ() >> 2);
    if (!BiospheresSphereMath.hasLake(point)) {
        return Blocks.AIR.getDefaultState();
    }
    return BiospheresSphereMath.isLavaLake(point) ? Blocks.LAVA.getDefaultState() : this.defaultFluid;
}
```

- [ ] **Step 5: Update `getHeight` and `getColumnSample` to use the same descriptor radius**

```java
BiospheresSphereDescriptor sphere = this.getNearestSphere(noiseConfig, x, z);
double radialDistance = Math.sqrt(sphere.centerPos().getSquaredDistance(x, sphere.centerY(), z));
if (radialDistance > sphere.radius()) {
    return this.minimumY;
}
```

- [ ] **Step 6: Run compilation and sphere tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresSphereLayoutTest --tests xyz.coolsa.biosphere.BiospheresSphereMathTest compileJava`
Expected: PASS for tests and compile if all fixed-radius terrain references are migrated.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java
git commit -m "refactor(worldgen): use sphere descriptors for terrain geometry"
```

### Task 7: Migrate shell and bridges to per-sphere radii

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`

- [ ] **Step 1: Replace fixed-radius shell logic with descriptor-backed values**

```java
if (radialDistance <= sphere.radius() + 16) {
    double sphereHalfHeight = Math.sqrt((double) sphere.radius() * sphere.radius()
        - (sphere.centerX() - x) * (double) (sphere.centerX() - x)
        - (sphere.centerZ() - z) * (double) (sphere.centerZ() - z));
```

- [ ] **Step 2: Resolve neighbors through the layout service instead of raw coordinate math**

```java
private BiospheresSphereDescriptor[] getClosestSpheres(BiospheresSphereDescriptor centerSphere) {
    return new BiospheresSphereDescriptor[] {
        this.sphereLayout.resolveNeighbor(centerSphere, 1, 0),
        this.sphereLayout.resolveNeighbor(centerSphere, -1, 0),
        this.sphereLayout.resolveNeighbor(centerSphere, 0, 1),
        this.sphereLayout.resolveNeighbor(centerSphere, 0, -1)
    };
}
```

- [ ] **Step 3: Update bridge routing to respect both origin and neighbor radii**

```java
if (radialDistance > centerSphere.bridgeRadius()) {
    double slope = neighbor.centerY() - centerSphere.centerY();
    double run = Math.max(1.0D, this.sphereDistance - centerSphere.radius() - neighbor.radius());
    slope /= run;
}
```

- [ ] **Step 4: Re-run compilation and any worldgen smoke tests available**

Run: `./gradlew compileJava test`
Expected: PASS, or only unrelated failures if structure tasks are not implemented yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java
git commit -m "refactor(worldgen): migrate shells and bridges to sphere descriptors"
```

## Phase 3: Structure measurement and policy catalog

### Task 8: Add normalized structure family and fit records

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureFamily.java`
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureFit.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`

- [ ] **Step 1: Write a failing policy test for a large surface structure and a large underground structure**

```java
@Test
void villageRequiresSurfaceSuitabilityAndLargeRadius() {
    BiospheresStructureFit fit = BiospheresStructurePolicy.defaultPolicy().fitFor(BiospheresStructureFamily.VILLAGE);

    assertTrue(fit.requiresSurfaceSuitability());
    assertTrue(fit.minHorizontalRadius() > 100);
}

@Test
void ancientCityRequiresUndergroundSuitability() {
    BiospheresStructureFit fit = BiospheresStructurePolicy.defaultPolicy().fitFor(BiospheresStructureFamily.ANCIENT_CITY);

    assertTrue(fit.requiresUndergroundSuitability());
    assertTrue(fit.minVerticalClearance() > 40);
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest`
Expected: FAIL because the structure policy types do not exist.

- [ ] **Step 3: Create the structure family enum**

```java
public enum BiospheresStructureFamily {
    VILLAGE,
    PILLAGER_OUTPOST,
    WOODLAND_MANSION,
    ANCIENT_CITY,
    STRONGHOLD,
    TRIAL_CHAMBERS,
    OCEAN_MONUMENT,
    MINESHAFT,
    RUINED_PORTAL,
    DESERT_PYRAMID,
    JUNGLE_TEMPLE,
    IGLOO,
    SWAMP_HUT
}
```

- [ ] **Step 4: Create the fit record**

```java
public record BiospheresStructureFit(
    int minHorizontalRadius,
    int minVerticalClearance,
    int shellMargin,
    boolean requiresSurfaceSuitability,
    boolean requiresUndergroundSuitability
) {
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresStructureFamily.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureFit.java src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java
git commit -m "feat(structures): add structure family and fit types"
```

### Task 9: Add measured structure bounds catalog

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureBoundsCatalog.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`

- [ ] **Step 1: Record the initial measured bounds in code comments and constants**

```java
private static final Map<BiospheresStructureFamily, Integer> HORIZONTAL_RADII = Map.of(
    BiospheresStructureFamily.VILLAGE, 144,
    BiospheresStructureFamily.WOODLAND_MANSION, 96,
    BiospheresStructureFamily.ANCIENT_CITY, 120,
    BiospheresStructureFamily.STRONGHOLD, 96,
    BiospheresStructureFamily.TRIAL_CHAMBERS, 112,
    BiospheresStructureFamily.OCEAN_MONUMENT, 96,
    BiospheresStructureFamily.MINESHAFT, 84
);
```

- [ ] **Step 2: Add failing tests that force the configured sphere maximum to be at least the largest required radius plus margin**

```java
@Test
void reportsLargestRequiredRadiusAcrossAllFamilies() {
    int required = BiospheresStructureBoundsCatalog.largestRequiredHorizontalRadius();

    assertTrue(required >= 144);
}
```

- [ ] **Step 3: Implement helper queries for maxima**

```java
public static int largestRequiredHorizontalRadius() {
    return HORIZONTAL_RADII.values().stream().max(Integer::compareTo).orElseThrow();
}

public static int requiredHorizontalRadius(BiospheresStructureFamily family) {
    return HORIZONTAL_RADII.get(family);
}
```

- [ ] **Step 4: Re-run the policy tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresStructureBoundsCatalog.java src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java
git commit -m "feat(structures): add measured structure bounds catalog"
```

### Task 10: Implement the default structure policy

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresStructurePolicy.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`

- [ ] **Step 1: Implement policy construction from the bounds catalog**

```java
public final class BiospheresStructurePolicy {
    private final Map<BiospheresStructureFamily, BiospheresStructureFit> fits;

    public static BiospheresStructurePolicy defaultPolicy() {
        return new BiospheresStructurePolicy(Map.of(
            BiospheresStructureFamily.VILLAGE,
            new BiospheresStructureFit(BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.VILLAGE), 48, 12, true, false),
            BiospheresStructureFamily.ANCIENT_CITY,
            new BiospheresStructureFit(BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.ANCIENT_CITY), 80, 16, false, true)
        ));
    }
}
```

- [ ] **Step 2: Cover every family listed in the approved spec**

```java
new BiospheresStructureFit(...)
```

Use one explicit entry each for:

- `VILLAGE`
- `PILLAGER_OUTPOST`
- `WOODLAND_MANSION`
- `ANCIENT_CITY`
- `STRONGHOLD`
- `TRIAL_CHAMBERS`
- `OCEAN_MONUMENT`
- `MINESHAFT`
- `RUINED_PORTAL`
- `DESERT_PYRAMID`
- `JUNGLE_TEMPLE`
- `IGLOO`
- `SWAMP_HUT`

- [ ] **Step 3: Add `isEligible` logic against a descriptor**

```java
public boolean isEligible(BiospheresStructureFamily family, BiospheresSphereDescriptor sphere, boolean surfaceSuitable, boolean undergroundSuitable) {
    BiospheresStructureFit fit = this.fitFor(family);
    if (sphere.radius() < fit.minHorizontalRadius() + fit.shellMargin()) {
        return false;
    }
    if (fit.requiresSurfaceSuitability() && !surfaceSuitable) {
        return false;
    }
    if (fit.requiresUndergroundSuitability() && !undergroundSuitable) {
        return false;
    }
    return true;
}
```

- [ ] **Step 4: Re-run the focused policy tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresStructurePolicy.java src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureBoundsCatalog.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureFamily.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureFit.java
git commit -m "feat(structures): add default sphere fit policy"
```

## Phase 4: Placement confinement and generator integration

### Task 11: Implement confinement math

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureConfinement.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructureConfinementTest.java`

- [ ] **Step 1: Write failing tests for a fully-contained and a leaking structure box**

```java
@Test
void acceptsBoundingBoxFullyInsideSphere() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 160, 161, 158, 16);
    BlockBox box = new BlockBox(-40, 80, -40, 40, 120, 40);

    assertTrue(BiospheresStructureConfinement.isWithinSphere(box, sphere, 8));
}

@Test
void rejectsBoundingBoxThatTouchesPastShellMargin() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 80, 81, 78, 16);
    BlockBox box = new BlockBox(-90, 60, -20, -10, 120, 20);

    assertFalse(BiospheresStructureConfinement.isWithinSphere(box, sphere, 8));
}
```

- [ ] **Step 2: Run the confinement test and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructureConfinementTest`
Expected: FAIL because the confinement class does not exist.

- [ ] **Step 3: Implement the box-to-sphere containment helper**

```java
public static boolean isWithinSphere(BlockBox box, BiospheresSphereDescriptor sphere, int shellMargin) {
    int allowedRadius = sphere.radius() - shellMargin;
    return containsPoint(box.getMinX(), box.getMinY(), box.getMinZ(), sphere, allowedRadius)
        && containsPoint(box.getMinX(), box.getMinY(), box.getMaxZ(), sphere, allowedRadius)
        && containsPoint(box.getMinX(), box.getMaxY(), box.getMinZ(), sphere, allowedRadius)
        && containsPoint(box.getMinX(), box.getMaxY(), box.getMaxZ(), sphere, allowedRadius)
        && containsPoint(box.getMaxX(), box.getMinY(), box.getMinZ(), sphere, allowedRadius)
        && containsPoint(box.getMaxX(), box.getMinY(), box.getMaxZ(), sphere, allowedRadius)
        && containsPoint(box.getMaxX(), box.getMaxY(), box.getMinZ(), sphere, allowedRadius)
        && containsPoint(box.getMaxX(), box.getMaxY(), box.getMaxZ(), sphere, allowedRadius);
}
```

- [ ] **Step 4: Re-run the confinement tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructureConfinementTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresStructureConfinement.java src/test/java/xyz/coolsa/biosphere/BiospheresStructureConfinementTest.java
git commit -m "feat(structures): add sphere confinement checks"
```

### Task 12: Add surface and underground suitability checks to the generator

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`

- [ ] **Step 1: Add helper methods for surface suitability and underground suitability**

```java
boolean isSurfaceSuitable(BiospheresSphereDescriptor sphere) {
    return sphere.radius() >= 96;
}

boolean isUndergroundSuitable(BiospheresSphereDescriptor sphere) {
    int topY = sphere.centerY() + sphere.radius();
    int bottomY = sphere.centerY() - sphere.radius();
    return topY - bottomY >= 96;
}
```

- [ ] **Step 2: Add focused tests that reflect current thresholds**

```java
@Test
void largeSphereQualifiesForSurfaceSuitability() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 144, 145, 142, 16);

    assertTrue(BiospheresChunkGenerator.isSurfaceSuitableForTests(sphere));
}
```

- [ ] **Step 3: Run the suitability tests and make them pass**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java
git commit -m "feat(worldgen): add sphere suitability checks"
```

### Task 13: Integrate family mapping and eligibility gates into structure generation

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Create or Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureRouting.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorStructureRulesTest.java`

- [ ] **Step 1: Inspect the 1.21.11 structure API surface used by the generator**

Run: `./gradlew compileJava`
Expected: determine the exact override or hook points available in the current mappings for structure gating.

- [ ] **Step 2: Write a failing integration test around family resolution and eligibility**

```java
@Test
void rejectsVillageInSphereBelowVillageThreshold() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 120, 0, 72, 73, 70, 16);

    assertFalse(BiospheresStructureRouting.defaultRouting().canStart(BiospheresStructureFamily.VILLAGE, sphere, false, true));
}
```

- [ ] **Step 3: Implement the routing adapter that maps vanilla structure keys to family names**

```java
public BiospheresStructureFamily familyFor(Identifier structureId) {
    return switch (structureId.toString()) {
        case "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga" -> BiospheresStructureFamily.VILLAGE;
        case "minecraft:ancient_city" -> BiospheresStructureFamily.ANCIENT_CITY;
        case "minecraft:mineshaft", "minecraft:mineshaft_mesa" -> BiospheresStructureFamily.MINESHAFT;
        default -> throw new IllegalArgumentException("Unsupported structure family: " + structureId);
    };
}
```

- [ ] **Step 4: Connect the routing adapter to the generator or the relevant structure hook**

```java
BiospheresSphereDescriptor sphere = this.sphereLayout.resolveForBlock(chunkCenterX, chunkCenterZ);
boolean surfaceSuitable = this.isSurfaceSuitable(sphere);
boolean undergroundSuitable = this.isUndergroundSuitable(sphere);
if (!this.structurePolicy.isEligible(family, sphere, surfaceSuitable, undergroundSuitable)) {
    return false;
}
```

- [ ] **Step 5: Run compilation and the integration tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorStructureRulesTest compileJava`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureRouting.java src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorStructureRulesTest.java
git commit -m "feat(structures): gate structure starts by sphere eligibility"
```

### Task 14: Enforce full confinement for structure bounds

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureRouting.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresStructureConfinement.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorStructureRulesTest.java`

- [ ] **Step 1: Add a failing integration test that rejects a start whose projected bounds would cross the shell**

```java
@Test
void rejectsStructureStartWhenProjectedBoundsLeakOutsideSphere() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 112, 113, 110, 16);
    BlockBox projectedBox = new BlockBox(-120, 70, -20, 20, 130, 20);

    assertFalse(BiospheresStructureRouting.defaultRouting().canPlaceProjectedBounds(BiospheresStructureFamily.STRONGHOLD, sphere, projectedBox));
}
```

- [ ] **Step 2: Add routing logic that checks projected or actual bounding boxes through the confinement helper**

```java
public boolean canPlaceProjectedBounds(BiospheresStructureFamily family, BiospheresSphereDescriptor sphere, BlockBox projectedBox) {
    BiospheresStructureFit fit = this.policy.fitFor(family);
    return BiospheresStructureConfinement.isWithinSphere(projectedBox, sphere, fit.shellMargin());
}
```

- [ ] **Step 3: Wire the confinement result into the structure start acceptance path**

```java
if (!this.structureRouting.canPlaceProjectedBounds(family, sphere, projectedBox)) {
    return false;
}
```

- [ ] **Step 4: Run all structure-focused tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest --tests xyz.coolsa.biosphere.BiospheresStructureConfinementTest --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorStructureRulesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresStructureRouting.java src/main/java/xyz/coolsa/biosphere/BiospheresStructureConfinement.java src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorStructureRulesTest.java src/test/java/xyz/coolsa/biosphere/BiospheresStructureConfinementTest.java
git commit -m "feat(structures): enforce full sphere confinement"
```

## Phase 5: Verification and tuning

### Task 15: Raise the configured max radius if the bounds catalog requires it

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereLayout.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Modify: `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java`

- [ ] **Step 1: Add a failing test that compares the configured max radius against the largest measured requirement plus shell margin**

```java
@Test
void configuredMaximumRadiusCoversLargestMeasuredStructure() {
    int configuredMax = 160;
    int requiredMax = BiospheresStructureBoundsCatalog.largestRequiredHorizontalRadius() + 16;

    assertTrue(configuredMax >= requiredMax);
}
```

- [ ] **Step 2: If the test fails, raise the shared default maximum in code and preset JSON**

```java
private static final int DEFAULT_MAX_SPHERE_RADIUS = 224;
```

```json
"max_sphere_radius": 224
```

- [ ] **Step 3: Re-run the policy and codec tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresStructurePolicyTest --tests xyz.coolsa.biosphere.BiospheresWorldPresetCodecTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresSphereLayout.java src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json src/test/java/xyz/coolsa/biosphere/BiospheresStructurePolicyTest.java
git commit -m "fix(worldgen): raise max sphere radius for full structure fit"
```

### Task 16: Run full verification suite

**Files:**
- Modify: none unless verification exposes defects
- Test: all test files above

- [ ] **Step 1: Run the full unit and integration suite**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 2: Run a full build**

Run: `./gradlew build`
Expected: PASS

- [ ] **Step 3: Manually inspect the generated preset JSON and codec wiring**

Run: `./gradlew processResources`
Expected: PASS and the generated `build/resources/main/data/biospheres/worldgen/world_preset/biospheres.json` should contain the radius range fields.

- [ ] **Step 4: If available locally, launch a smoke-test client world**

Run: `./gradlew runClient`
Expected: game launches, Biospheres is selectable, Nether and End remain vanilla, and initial exploration shows mixed sphere sizes.

- [ ] **Step 5: If available locally, launch a smoke-test server**

Run: `./gradlew runServer`
Expected: server starts cleanly with the Biospheres preset available and no registry or codec crashes.

- [ ] **Step 6: Commit any final fixes uncovered by verification**

```bash
git add .
git commit -m "test(worldgen): verify biospheres v2 structure confinement"
```

## Self-review checklist

- Spec coverage:
  Every approved requirement maps to a phase in this plan: random radius layout, fixed grid centers, possible max-radius raise, overworld-only structure confinement, terrain suitability, no clipping, and verification.
- Placeholder scan:
  No `TBD`, `TODO`, or deferred implementation markers remain.
- Type consistency:
  The plan consistently uses `BiospheresSphereDescriptor`, `BiospheresSphereLayout`, `BiospheresStructureFamily`, `BiospheresStructureFit`, `BiospheresStructurePolicy`, and `BiospheresStructureConfinement` across later tasks.

## Notes for execution

- Implement Phase 1 and Phase 2 before touching any structure gating code. The structure work assumes the generator already uses shared descriptors everywhere.
- Do not claim universal structure fit until Task 15 is complete and the measured bounds test passes.
- If the Minecraft 1.21.11 structure API offers a better hook point than the generator-facing adapter described here, adapt the integration layer but keep the same policy and confinement responsibilities.
