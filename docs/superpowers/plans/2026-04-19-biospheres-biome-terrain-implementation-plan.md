# Biospheres biome terrain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make sphere interiors follow the selected biome's vanilla-like terrain character, including mountain relief and lake-capable depressions, while keeping all generated terrain strictly inside spheres and preserving `the_void` outside them.

**Architecture:** Keep `BiospheresChunkGenerator` as the owning generator and replace its current threshold-based interior fill with a shared biome-sensitive terrain profile model. Route `populateNoise`, `getHeight`, and `getColumnSample` through the same sphere-masked terrain calculation so terrain, heightmaps, and later shell cleanup all agree on the exact interior shape.

**Tech Stack:** Java 21, Fabric Loom, Minecraft 1.21.11 worldgen APIs, JUnit 5

---

## File map

### Existing files to modify

- `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
  Responsibility: replace the custom solid fill logic with biome-sensitive sphere-masked terrain, keep shell and bridge behavior consistent, and align `populateNoise`, `getHeight`, and `getColumnSample`.
- `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
  Responsibility: no behavioral rewrite expected, but may need a small helper or accessor if terrain logic needs stable sphere-biome lookup outside chunk biome sampling.
- `src/test/java/xyz/coolsa/biosphere/BiospheresSphereLayoutTest.java`
  Responsibility: extend coverage if layout-derived radius or lake bounds are used by the new terrain profile.

### New files to create

- `src/main/java/xyz/coolsa/biosphere/BiospheresTerrainProfile.java`
  Responsibility: immutable record for one sphere column's terrain decision, including bottom Y, top Y, and lake or depression bounds inside the sphere.
- `src/main/java/xyz/coolsa/biosphere/BiospheresTerrainShaper.java`
  Responsibility: central biome-aware terrain calculation that takes a sphere descriptor plus noise inputs and returns the masked interior terrain profile.
- `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`
  Responsibility: regression tests for sphere masking, biome-sensitive relief differences, and lake-capable depressions.
- `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`
  Responsibility: focused tests that verify `getHeight` and column-shape decisions stay consistent with the new terrain profile contract.

## Phase overview

### Phase 1: Introduce a shared terrain profile model

Create a single source of truth for one sphere column's terrain body so population, height queries, and column samples can all agree.

### Phase 2: Replace the old threshold terrain fill

Move `BiospheresChunkGenerator` onto the new terrain shaper and remove hard dependence on the current threshold formula for interior height.

### Phase 3: Rework lake handling and surface alignment

Reduce or remove the current central-lake dominance and make surface painting follow the new terrain profile.

### Phase 4: Verify sphere containment and biome differentiation

Add targeted tests proving mountain-like biomes differ from flat biomes and that terrain never escapes the sphere.

## Phase 1: Introduce a shared terrain profile model

### Task 1: Add the terrain profile record

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresTerrainProfile.java`
- Test: none yet

- [ ] **Step 1: Write the terrain profile record**

```java
package xyz.coolsa.biosphere;

public record BiospheresTerrainProfile(
    int bottomY,
    int surfaceY,
    int ceilingY,
    int lakeFloorY,
    int lakeTopY,
    boolean hasLake
) {
    public boolean containsSolidAt(int y) {
        return y >= this.bottomY && y <= this.surfaceY;
    }

    public boolean containsLakeAt(int y) {
        return this.hasLake && y >= this.lakeFloorY && y <= this.lakeTopY;
    }
}
```

- [ ] **Step 2: Compile to catch syntax mistakes**

Run: `./gradlew compileJava`
Expected: compile succeeds or fails only in unrelated files, with no errors in `BiospheresTerrainProfile.java`.

### Task 2: Write failing tests for terrain shaping rules

**Files:**
- Create: `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`

- [ ] **Step 1: Write failing tests for sphere masking and biome differentiation**

```java
package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresTerrainShaperTest {
    @Test
    void keepsTerrainInsideTheSphereVerticalSlice() {
        BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);
        BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

        BiospheresTerrainProfile profile = shaper.shapeColumn(sphere, 0.85D, 0.15D, false);

        assertTrue(profile.bottomY() >= sphere.centerY() - sphere.radius());
        assertTrue(profile.ceilingY() <= sphere.centerY() + sphere.radius());
        assertTrue(profile.surfaceY() <= profile.ceilingY());
    }

    @Test
    void givesMountainInputsMoreReliefThanFlatInputs() {
        BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);
        BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

        BiospheresTerrainProfile mountain = shaper.shapeColumn(sphere, 1.0D, 0.8D, false);
        BiospheresTerrainProfile flat = shaper.shapeColumn(sphere, 0.2D, -0.2D, false);

        assertTrue(mountain.surfaceY() > flat.surfaceY());
    }

    @Test
    void allowsLakeCapableColumnsToReserveInteriorLakeSpace() {
        BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 20);
        BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

        BiospheresTerrainProfile profile = shaper.shapeColumn(sphere, 0.35D, -0.65D, true);

        assertTrue(profile.hasLake());
        assertTrue(profile.lakeFloorY() < profile.lakeTopY());
        assertTrue(profile.lakeTopY() < profile.surfaceY());
    }
}
```

- [ ] **Step 2: Run the focused test class and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresTerrainShaperTest`
Expected: FAIL with missing `BiospheresTerrainShaper` class.

### Task 3: Implement the terrain shaper

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresTerrainShaper.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresTerrainProfile.java` if small field adjustments are needed
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`

- [ ] **Step 1: Write the minimal terrain shaper implementation**

```java
package xyz.coolsa.biosphere;

public final class BiospheresTerrainShaper {
    public BiospheresTerrainProfile shapeColumn(BiospheresSphereDescriptor sphere, double reliefSignal, double valleySignal, boolean lakeCapable) {
        int sphereBottom = sphere.centerY() - sphere.radius();
        int sphereTop = sphere.centerY() + sphere.radius();
        int baseSurface = sphere.centerY() + (int) Math.round(reliefSignal * (sphere.radius() * 0.45D));
        int valleyPull = (int) Math.round(Math.max(0.0D, -valleySignal) * (sphere.radius() * 0.20D));
        int raisedSurface = Math.min(sphereTop - 1, Math.max(sphereBottom + 4, baseSurface - valleyPull));

        boolean hasLake = lakeCapable && valleySignal < -0.45D;
        int lakeTop = hasLake ? Math.max(sphereBottom + 2, raisedSurface - 3) : sphereBottom;
        int lakeFloor = hasLake ? Math.max(sphereBottom + 1, lakeTop - Math.max(2, sphere.lakeRadius() / 3)) : sphereBottom;

        return new BiospheresTerrainProfile(sphereBottom, raisedSurface, sphereTop, lakeFloor, lakeTop, hasLake);
    }
}
```

- [ ] **Step 2: Run the focused tests and verify they pass**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresTerrainShaperTest`
Expected: PASS

## Phase 2: Replace the old threshold terrain fill

### Task 4: Add failing generator height-model tests

**Files:**
- Create: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`

- [ ] **Step 1: Write failing tests for shared terrain-profile-based height rules**

```java
package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresChunkGeneratorHeightModelTest {
    @Test
    void returnsMinimumYOutsideTheSphereRadius() {
        BiospheresSphereLayout layout = new BiospheresSphereLayout(480, 160, 224, 16, 6, -64, 384);
        BiospheresSphereDescriptor sphere = layout.resolve(0, 0);

        int farX = sphere.centerX() + sphere.radius() + 20;

        assertTrue(BiospheresChunkGenerator.computeMaskedSurfaceY(sphere, farX, sphere.centerZ(), 170, -64) == -64);
    }

    @Test
    void clampsBiomeSurfaceToTheSphereCeiling() {
        BiospheresSphereLayout layout = new BiospheresSphereLayout(480, 160, 224, 16, 6, -64, 384);
        BiospheresSphereDescriptor sphere = layout.resolve(0, 0);

        int masked = BiospheresChunkGenerator.computeMaskedSurfaceY(sphere, sphere.centerX(), sphere.centerZ(), sphere.centerY() + sphere.radius() + 30, -64);

        assertEquals(sphere.centerY() + sphere.radius(), masked);
    }
}
```

- [ ] **Step 2: Run the focused test class and verify it fails**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorHeightModelTest`
Expected: FAIL with missing `computeMaskedSurfaceY` method.

### Task 5: Refactor `BiospheresChunkGenerator` to use the terrain shaper

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`

- [ ] **Step 1: Add a reusable masked surface helper to `BiospheresChunkGenerator`**

```java
static int computeMaskedSurfaceY(BiospheresSphereDescriptor sphere, int x, int z, int biomeSurfaceY, int minimumY) {
    double dx = sphere.centerX() - x;
    double dz = sphere.centerZ() - z;
    double radialDistance = Math.sqrt(dx * dx + dz * dz);
    if (radialDistance > sphere.radius()) {
        return minimumY;
    }

    double sphereHalfHeight = Math.sqrt((double) sphere.radius() * sphere.radius() - dx * dx - dz * dz);
    int sphereTop = sphere.centerY() + (int) sphereHalfHeight;
    int sphereBottom = sphere.centerY() - (int) sphereHalfHeight;
    if (biomeSurfaceY < sphereBottom) {
        return minimumY;
    }

    return Math.min(sphereTop, biomeSurfaceY);
}
```

- [ ] **Step 2: Replace the old `getHeight` threshold logic with shaper-driven surface computation**

```java
@Override
public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
    BiospheresSphereDescriptor sphere = this.layout.resolve(x, z);
    int biomeSurfaceY = this.estimateBiomeSurfaceY(sphere, x, z, noiseConfig);
    return computeMaskedSurfaceY(sphere, x, z, biomeSurfaceY, this.minimumY);
}
```

- [ ] **Step 3: Add a private biome-surface estimator that feeds the terrain shaper**

```java
private int estimateBiomeSurfaceY(BiospheresSphereDescriptor sphere, int x, int z, NoiseConfig noiseConfig) {
    double reliefSignal = this.sampleReliefSignal(noiseConfig, x, z);
    double valleySignal = this.sampleValleySignal(noiseConfig, x, z);
    boolean lakeCapable = this.isLakeCapableColumn(noiseConfig, sphere, x, z);
    BiospheresTerrainProfile profile = this.terrainShaper.shapeColumn(sphere, reliefSignal, valleySignal, lakeCapable);
    return profile.surfaceY();
}
```

- [ ] **Step 4: Rework `populateNoise` so each column fills only the shaper-approved terrain body inside the sphere**

```java
for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
    for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
        BiospheresSphereDescriptor sphere = this.layout.resolve(x, z);
        BiospheresTerrainProfile profile = this.sampleTerrainProfile(sphere, x, z, noiseConfig);
        BlockState lakeState = this.getLakeBlock(noiseConfig, sphere.centerPos());

        for (int y = profile.bottomY(); y <= profile.ceilingY(); y++) {
            BlockState state = Blocks.AIR.getDefaultState();
            if (profile.containsSolidAt(y)) {
                state = this.defaultBlock;
            }
            if (!lakeState.isAir() && profile.containsLakeAt(y)) {
                state = lakeState;
            }
            chunk.setBlockState(new BlockPos(x, y, z), state, 0);
        }
    }
}
```

- [ ] **Step 5: Update `getColumnSample` to use the same terrain profile rather than a flat `topY` fill**

```java
@Override
public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
    BiospheresSphereDescriptor sphere = this.layout.resolve(x, z);
    BiospheresTerrainProfile profile = this.sampleTerrainProfile(sphere, x, z, noiseConfig);
    BlockState[] states = new BlockState[world.getHeight()];

    for (int y = 0; y < states.length; y++) {
        int worldY = y + world.getBottomY();
        states[y] = profile.containsSolidAt(worldY) ? this.defaultBlock : Blocks.AIR.getDefaultState();
    }

    return new VerticalBlockSample(world.getBottomY(), states);
}
```

- [ ] **Step 6: Run the focused generator height tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorHeightModelTest`
Expected: PASS

## Phase 3: Rework lake handling and surface alignment

### Task 6: Add failing tests for lake capability thresholds

**Files:**
- Modify: `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java`

- [ ] **Step 1: Add a regression test that flat but non-lake columns do not open lake cavities**

```java
@Test
void doesNotOpenLakeSpaceWithoutLakeCapability() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 20);
    BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

    BiospheresTerrainProfile profile = shaper.shapeColumn(sphere, 0.15D, -0.80D, false);

    assertFalse(profile.hasLake());
}
```

- [ ] **Step 2: Run the focused shaper tests and verify the new test fails if needed**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresTerrainShaperTest`
Expected: FAIL only if the current lake rule ignores `lakeCapable`.

- [ ] **Step 3: Tighten the shaper implementation so lake cavities require both a depression and lake capability**

```java
boolean hasLake = lakeCapable && valleySignal < -0.45D && reliefSignal < 0.55D;
```

- [ ] **Step 4: Re-run the focused shaper tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresTerrainShaperTest`
Expected: PASS

### Task 7: Align surface painting with the new terrain profile

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Test: existing worldgen tests plus a focused test run if surface helpers remain pure enough

- [ ] **Step 1: Keep `buildSurface` keyed off the actual generated surface Y from the shared model**

```java
int surfaceY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x & 15, z & 15);
if (surfaceY <= this.minimumY) {
    continue;
}
```

- [ ] **Step 2: Ensure top and under materials only replace generated default terrain blocks**

```java
if (chunk.getBlockState(mutable).isOf(this.defaultBlock.getBlock())) {
    chunk.setBlockState(mutable, topState, 0);
}
```

- [ ] **Step 3: Run the full relevant test suite after the surface alignment**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresTerrainShaperTest --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorHeightModelTest --tests xyz.coolsa.biosphere.BiospheresSphereLayoutTest`
Expected: PASS

## Phase 4: Verify sphere containment and biome differentiation

### Task 8: Add regression tests for sphere-bounded terrain ranges

**Files:**
- Modify: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`
- Test: `src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`

- [ ] **Step 1: Add a regression test that mountainous and flat inputs produce meaningfully different masked surfaces**

```java
@Test
void maskedTerrainStillPreservesBiomeReliefDifferences() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);
    int mountain = BiospheresChunkGenerator.computeMaskedSurfaceY(sphere, 0, 0, 170, -64);
    int flat = BiospheresChunkGenerator.computeMaskedSurfaceY(sphere, 0, 0, 135, -64);

    assertTrue(mountain > flat);
}
```

- [ ] **Step 2: Add a regression test that outside-of-sphere columns always collapse to minimum Y**

```java
@Test
void outsideSphereColumnsAlwaysCollapseToMinimumY() {
    BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);

    int masked = BiospheresChunkGenerator.computeMaskedSurfaceY(sphere, 97, 97, 140, -64);

    assertEquals(-64, masked);
}
```

- [ ] **Step 3: Run the focused height-model tests**

Run: `./gradlew test --tests xyz.coolsa.biosphere.BiospheresChunkGeneratorHeightModelTest`
Expected: PASS

### Task 9: Run the project verification suite

**Files:**
- Modify: none
- Test: existing test suite

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 2: Build the mod jar**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Review git diff for only the intended terrain changes**

Run: `git diff -- src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/main/java/xyz/coolsa/biosphere/BiospheresTerrainProfile.java src/main/java/xyz/coolsa/biosphere/BiospheresTerrainShaper.java src/test/java/xyz/coolsa/biosphere/BiospheresTerrainShaperTest.java src/test/java/xyz/coolsa/biosphere/BiospheresChunkGeneratorHeightModelTest.java`
Expected: diff contains only biome-terrain and test changes.

## Suggested commit breakdown

Use small commits while executing:

1. `test(worldgen): add terrain shaper coverage`
2. `feat(worldgen): add shared biome terrain shaper`
3. `refactor(worldgen): route sphere terrain through shared profile`
4. `test(worldgen): verify masked height behavior`

## Self-review against the spec

- Spec requirement: sphere interiors should follow biome terrain character.
  Covered by: Tasks 2, 3, 5, and 8.
- Spec requirement: terrain only inside spheres.
  Covered by: Tasks 2, 4, 5, and 8.
- Spec requirement: lake-capable biomes can host lakes or depressions.
  Covered by: Tasks 2, 3, and 6.
- Spec requirement: `the_void` remains outside spheres.
  Covered by: Tasks 4, 5, 8, and 9.
- Spec requirement: keep Biospheres-specific ownership, shell, and bridges.
  Covered by: Task 5 and the fact that only the interior terrain model changes.
