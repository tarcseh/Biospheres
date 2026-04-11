# Biospheres 1.21.11 port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the mod to Minecraft `1.21.11` Fabric and preserve the current biosphere terrain behavior, world preset integration, and dedicated server support for newly created worlds.

**Architecture:** Modernize the build first, then extract the old sphere math into a tested helper so the 1.16 logic can be reused inside the new `MapCodec`-based biome source and chunk generator. Finish by wiring the generator into a data-driven world preset, restoring surface and finishing passes, and verifying the result in both client and dedicated server flows.

**Tech Stack:** Java 21, Gradle 9.4.1, Fabric Loom `1.16-SNAPSHOT`, Minecraft `1.21.11`, Yarn `1.21.11+build.4`, Fabric Loader `0.19.1`, Fabric API `0.141.3+1.21.11`, JUnit 5

---

## File map

- Modify: `build.gradle`
  Responsibility: move the project to the modern Loom, Java 21, test, and publishing configuration.
- Modify: `gradle.properties`
  Responsibility: pin the approved Minecraft, Yarn, Loader, Loom, and Fabric API versions.
- Modify: `settings.gradle`
  Responsibility: remove obsolete repositories and keep only current plugin repositories.
- Modify: `gradle/wrapper/gradle-wrapper.properties`
  Responsibility: update the wrapper to a Gradle version supported by modern Loom.
- Modify: `src/main/resources/fabric.mod.json`
  Responsibility: update dependency metadata, the icon path, and 1.21.11 compatibility declarations.
- Modify: `README.md`
  Responsibility: document the new target version and dedicated server preset usage.
- Modify: `src/main/java/xyz/coolsa/biosphere/Biospheres.java`
  Responsibility: register the custom `MapCodec` entries in `Registries.BIOME_SOURCE` and `Registries.CHUNK_GENERATOR`.
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
  Responsibility: port biome selection to the modern `BiomeSource` API while preserving sphere-based biome choice.
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
  Responsibility: port terrain generation, shell finishing, bridges, and the server-safe generator implementation.
- Delete: `src/main/java/xyz/coolsa/biosphere/BiospheresGeneratorType.java`
  Responsibility: remove the now-unused empty file.
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`
  Responsibility: hold the shared grid, distance, seeded height, and deterministic selection helpers used by the biome source and chunk generator.
- Create: `src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java`
  Responsibility: lock down the old generator's core spacing, band, and deterministic selection rules.
- Create: `src/main/resources/assets/biospheres/lang/en_us.json`
  Responsibility: define the world preset display name.
- Create: `src/main/resources/assets/biospheres/icon.png`
  Responsibility: move the existing icon under the correct namespace for the updated metadata.
- Create: `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`
  Responsibility: expose Biospheres as a normal overworld world preset.
- Create: `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json`
  Responsibility: add the custom preset to the normal world preset list.

### Task 1: Upgrade the build and add a test harness

**Files:**
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `settings.gradle`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `src/main/resources/fabric.mod.json`

- [ ] **Step 1: Capture the current failing baseline**

Run: `./gradlew.bat build`

Expected: FAIL in `settings.gradle` with `Unsupported class file major version 65`, confirming the old Gradle 6.5 toolchain cannot run on the current Java.

- [ ] **Step 2: Update the Gradle, Loom, Java, and test configuration**

Apply these contents.

`build.gradle`

```gradle
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    testImplementation platform("org.junit:junit-bom:5.10.2")
    testImplementation "org.junit.jupiter:junit-jupiter"
}

processResources {
    inputs.property "version", project.version

    filesMatching("fabric.mod.json") {
        expand "version": inputs.properties.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 21
}

tasks.named("test", Test) {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}

publishing {
    publications {
        create("mavenJava", MavenPublication) {
            from components.java
        }
    }
}
```

`gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false

minecraft_version=1.21.11
yarn_mappings=1.21.11+build.4
loader_version=0.19.1
loom_version=1.16-SNAPSHOT

mod_version=0.0.3
maven_group=xyz.coolsa
archives_base_name=biospheres_generator

fabric_api_version=0.141.3+1.21.11
```

`settings.gradle`

```gradle
pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = "https://maven.fabricmc.net/"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

`gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: Update the mod metadata to the new dependency model**

Replace the dependency section and icon path in `src/main/resources/fabric.mod.json`.

```json
{
  "schemaVersion": 1,
  "id": "biospheres",
  "version": "${version}",
  "name": "Biospheres",
  "description": "Based on the original mod by Risugami, implemented with Fabric.",
  "authors": [
    "Coolsa"
  ],
  "contact": {
    "sources": "https://github.com/coolsa/Modfest1.16"
  },
  "license": "CC BY-SA 4.0",
  "icon": "assets/biospheres/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [
      "xyz.coolsa.biosphere.Biospheres"
    ]
  },
  "depends": {
    "fabricloader": ">=0.19.1",
    "fabric-api": "*",
    "minecraft": "1.21.11",
    "java": ">=21"
  }
}
```

- [ ] **Step 4: Regenerate the wrapper and confirm the project reaches Java compilation**

Run: `./gradlew.bat wrapper --gradle-version 9.4.1`

Expected: `BUILD SUCCESSFUL`

Run: `./gradlew.bat clean test`

Expected: FAIL in the Java sources because the old 1.16 worldgen APIs no longer exist, proving the build toolchain is fixed and the remaining work is source porting.

- [ ] **Step 5: Commit the build-system upgrade**

```bash
git add build.gradle gradle.properties settings.gradle gradle/wrapper/gradle-wrapper.properties src/main/resources/fabric.mod.json
git commit -m "build(fabric): upgrade toolchain for 1.21.11"
```

### Task 2: Extract and test the sphere math

**Files:**
- Create: `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`
- Create: `src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java`

- [ ] **Step 1: Write failing unit tests for the old generator's deterministic rules**

Create `src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java`.

```java
package xyz.coolsa.biosphere;

import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresSphereMathTest {
    @Test
    void snapsBlockCoordinatesToNearestSphereCenter() {
        assertEquals(128, BiospheresSphereMath.nearestCenter(120, 128));
        assertEquals(-128, BiospheresSphereMath.nearestCenter(-120, 128));
        assertEquals(0, BiospheresSphereMath.nearestCenter(12, 128));
    }

    @Test
    void detectsWhetherBiomeCoordinatesAreInsideTheSphereBand() {
        assertTrue(BiospheresSphereMath.isInsideSphereBand(0, 0, 128, 32, 6));
        assertFalse(BiospheresSphereMath.isInsideSphereBand(20, 20, 128, 32, 6));
    }

    @Test
    void picksAStableBiomeIndexFromASeededNoisePoint() {
        MultiNoiseUtil.NoiseValuePoint point = new MultiNoiseUtil.NoiseValuePoint(11L, 22L, 33L, 44L, 55L, 66L);

        int first = BiospheresSphereMath.pickIndex(point, 7);
        int second = BiospheresSphereMath.pickIndex(point, 7);

        assertEquals(first, second);
        assertTrue(first >= 0 && first < 7);
    }

    @Test
    void keepsSphereCentersInsideTheModernWorldHeight() {
        MultiNoiseUtil.NoiseValuePoint point = new MultiNoiseUtil.NoiseValuePoint(11L, 22L, 33L, 44L, 500_000L, 66L);

        int y = BiospheresSphereMath.pickCenterY(point, 32, -64, 384);

        assertTrue(y >= 0);
        assertTrue(y <= 256);
    }

    @Test
    void preservesTheCurrentLakeThresholds() {
        assertTrue(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 5L, 0L, 0L, 0L)));
        assertFalse(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 4L, 0L, 0L, 0L)));
    }
}
```

- [ ] **Step 2: Run the focused test to confirm the helper does not exist yet**

Run: `./gradlew.bat test --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`

Expected: FAIL with `cannot find symbol` errors for `BiospheresSphereMath`.

- [ ] **Step 3: Implement the shared helper that the modern biome source and chunk generator will use**

Create `src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java`.

```java
package xyz.coolsa.biosphere;

import net.minecraft.world.biome.source.util.MultiNoiseUtil;

public final class BiospheresSphereMath {
    private BiospheresSphereMath() {
    }

    public static int nearestCenter(int blockCoord, int sphereDistance) {
        return Math.round(blockCoord / (float) sphereDistance) * sphereDistance;
    }

    public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int sphereRadius, int extraRadius) {
        int blockX = biomeX * 4;
        int blockZ = biomeZ * 4;
        int centerX = nearestCenter(blockX, sphereDistance);
        int centerZ = nearestCenter(blockZ, sphereDistance);
        double dx = centerX - blockX;
        double dz = centerZ - blockZ;
        return Math.sqrt(dx * dx + dz * dz) < sphereRadius + extraRadius;
    }

    public static int pickIndex(MultiNoiseUtil.NoiseValuePoint point, int count) {
        long mixed = point.temperatureNoise()
            ^ Long.rotateLeft(point.humidityNoise(), 11)
            ^ Long.rotateLeft(point.continentalnessNoise(), 22)
            ^ Long.rotateLeft(point.weirdnessNoise(), 33);
        return Math.floorMod(mixed, count);
    }

    public static int pickCenterY(MultiNoiseUtil.NoiseValuePoint point, int sphereRadius, int minimumY, int worldHeight) {
        double normalized = (Math.floorMod(point.depth(), 2_000_001L) / 1_000_000.0D) - 1.0D;
        double curved = Math.pow(normalized * 0.5D, 3.0D) + 0.5D;
        int minCenter = minimumY + sphereRadius * 2;
        int maxCenter = minimumY + worldHeight - sphereRadius * 2;
        return minCenter + (int) Math.round(curved * (maxCenter - minCenter));
    }

    public static boolean hasLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.continentalnessNoise(), 10L) >= 5L;
    }

    public static boolean isLavaLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.weirdnessNoise(), 10L) == 0L;
    }
}
```

- [ ] **Step 4: Run the helper test and keep it green**

Run: `./gradlew.bat test --tests xyz.coolsa.biosphere.BiospheresSphereMathTest`

Expected: PASS

- [ ] **Step 5: Commit the tested sphere math extraction**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresSphereMath.java src/test/java/xyz/coolsa/biosphere/BiospheresSphereMathTest.java
git commit -m "test(worldgen): lock down biosphere sphere math"
```

### Task 3: Port the biome source and base chunk generator to modern APIs

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/Biospheres.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java`
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Delete: `src/main/java/xyz/coolsa/biosphere/BiospheresGeneratorType.java`

- [ ] **Step 1: Confirm the old 1.16 worldgen classes are now the only compile blocker**

Run: `./gradlew.bat compileJava`

Expected: FAIL with missing-method and missing-type errors in `Biospheres.java`, `BiospheresBiomeSource.java`, and `BiospheresChunkGenerator.java`.

- [ ] **Step 2: Replace the registration code with modern `MapCodec` registrations**

Update `src/main/java/xyz/coolsa/biosphere/Biospheres.java`.

```java
package xyz.coolsa.biosphere;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Biospheres implements ModInitializer {
    public static final String MOD_ID = "biospheres";
    public static final Identifier CHUNK_GENERATOR_ID = Identifier.of(MOD_ID, "biosphere");
    public static final Identifier BIOME_SOURCE_ID = Identifier.of(MOD_ID, "sphere_biomes");

    @Override
    public void onInitialize() {
        Registry.register(Registries.CHUNK_GENERATOR, CHUNK_GENERATOR_ID, BiospheresChunkGenerator.CODEC);
        Registry.register(Registries.BIOME_SOURCE, BIOME_SOURCE_ID, BiospheresBiomeSource.CODEC);
    }
}
```

- [ ] **Step 3: Rewrite the biome source around `MapCodec`, `RegistryEntry<Biome>`, and the tested helper**

Replace `src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java` with this structure.

```java
package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.List;
import java.util.stream.Stream;

public final class BiospheresBiomeSource extends BiomeSource {
    public static final MapCodec<BiospheresBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.listOf().fieldOf("biomes").forGetter(BiospheresBiomeSource::getBiomeIds),
        Identifier.CODEC.optionalFieldOf("void_biome", Identifier.ofVanilla("the_void")).forGetter(BiospheresBiomeSource::getVoidBiomeId),
        Codec.INT.optionalFieldOf("sphere_distance", 128).forGetter(BiospheresBiomeSource::getSphereDistance),
        Codec.INT.optionalFieldOf("sphere_radius", 32).forGetter(BiospheresBiomeSource::getSphereRadius)
    ).apply(instance, BiospheresBiomeSource::new));

    private final List<Identifier> biomeIds;
    private final Identifier voidBiomeId;
    private final int sphereDistance;
    private final int sphereRadius;
    private final List<RegistryEntry<Biome>> sphereBiomes;
    private final RegistryEntry<Biome> voidBiome;

    public BiospheresBiomeSource(List<Identifier> biomeIds, Identifier voidBiomeId, int sphereDistance, int sphereRadius) {
        this.biomeIds = List.copyOf(biomeIds);
        this.voidBiomeId = voidBiomeId;
        this.sphereDistance = sphereDistance;
        this.sphereRadius = sphereRadius;
        this.sphereBiomes = this.biomeIds.stream()
            .map(BiospheresBiomeSource::requireBiome)
            .toList();
        this.voidBiome = requireBiome(this.voidBiomeId);
    }

    private static RegistryEntry<Biome> requireBiome(Identifier id) {
        RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, id);
        return Registries.BIOME.getEntry(key)
            .orElseThrow(() -> new IllegalArgumentException("Unknown biome: " + id));
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.concat(this.sphereBiomes.stream(), Stream.of(this.voidBiome)).distinct();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        if (!BiospheresSphereMath.isInsideSphereBand(x, z, this.sphereDistance, this.sphereRadius, 6)) {
            return this.voidBiome;
        }

        int centerX = BiospheresSphereMath.nearestCenter(x * 4, this.sphereDistance);
        int centerZ = BiospheresSphereMath.nearestCenter(z * 4, this.sphereDistance);
        MultiNoiseUtil.NoiseValuePoint point = noise.sample(centerX >> 2, y, centerZ >> 2);
        int index = BiospheresSphereMath.pickIndex(point, this.sphereBiomes.size());
        return this.sphereBiomes.get(index);
    }

    public List<Identifier> getBiomeIds() {
        return this.biomeIds;
    }

    public Identifier getVoidBiomeId() {
        return this.voidBiomeId;
    }

    public int getSphereDistance() {
        return this.sphereDistance;
    }

    public int getSphereRadius() {
        return this.sphereRadius;
    }
}
```

- [ ] **Step 4: Rewrite the chunk generator with the new signatures, deterministic world-seeded random splitting, and the old geometry passes**

Replace `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java` with a modern generator that keeps the old field names and ports the old loops into the new methods.

```java
package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public final class BiospheresChunkGenerator extends ChunkGenerator {
    public static final MapCodec<BiospheresChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(BiospheresChunkGenerator::getBiomeSource),
        Codec.INT.optionalFieldOf("sphere_distance", 128).forGetter(BiospheresChunkGenerator::getSphereDistance),
        Codec.INT.optionalFieldOf("sphere_radius", 32).forGetter(BiospheresChunkGenerator::getSphereRadius),
        Codec.INT.optionalFieldOf("lake_radius", 16).forGetter(BiospheresChunkGenerator::getLakeRadius),
        Codec.INT.optionalFieldOf("shore_radius", 6).forGetter(BiospheresChunkGenerator::getShoreRadius),
        Codec.INT.optionalFieldOf("minimum_y", -64).forGetter(BiospheresChunkGenerator::getMinimumY),
        Codec.INT.optionalFieldOf("world_height", 384).forGetter(BiospheresChunkGenerator::getWorldHeight),
        Identifier.CODEC.optionalFieldOf("default_block", Identifier.ofVanilla("stone")).forGetter(BiospheresChunkGenerator::getDefaultBlockId),
        Identifier.CODEC.optionalFieldOf("default_fluid", Identifier.ofVanilla("water")).forGetter(BiospheresChunkGenerator::getDefaultFluidId),
        Identifier.CODEC.optionalFieldOf("default_bridge", Identifier.ofVanilla("oak_planks")).forGetter(BiospheresChunkGenerator::getDefaultBridgeId),
        Identifier.CODEC.optionalFieldOf("default_edge", Identifier.ofVanilla("oak_fence")).forGetter(BiospheresChunkGenerator::getDefaultEdgeId)
    ).apply(instance, BiospheresChunkGenerator::new));

    private final int sphereDistance;
    private final int sphereRadius;
    private final int lakeRadius;
    private final int shoreRadius;
    private final int minimumY;
    private final int worldHeight;
    private final Identifier defaultBlockId;
    private final Identifier defaultFluidId;
    private final Identifier defaultBridgeId;
    private final Identifier defaultEdgeId;
    private final BlockState defaultBlock;
    private final BlockState defaultFluid;
    private final BlockState defaultBridge;
    private final BlockState defaultEdge;

    public BiospheresChunkGenerator(
        BiomeSource biomeSource,
        int sphereDistance,
        int sphereRadius,
        int lakeRadius,
        int shoreRadius,
        int minimumY,
        int worldHeight,
        Identifier defaultBlockId,
        Identifier defaultFluidId,
        Identifier defaultBridgeId,
        Identifier defaultEdgeId
    ) {
        super(biomeSource, biomeEntry -> biomeEntry.value().getGenerationSettings());
        this.sphereDistance = sphereDistance;
        this.sphereRadius = sphereRadius;
        this.lakeRadius = lakeRadius;
        this.shoreRadius = shoreRadius;
        this.minimumY = minimumY;
        this.worldHeight = worldHeight;
        this.defaultBlockId = defaultBlockId;
        this.defaultFluidId = defaultFluidId;
        this.defaultBridgeId = defaultBridgeId;
        this.defaultEdgeId = defaultEdgeId;
        this.defaultBlock = Registries.BLOCK.get(defaultBlockId).getDefaultState();
        this.defaultFluid = Registries.BLOCK.get(defaultFluidId).getDefaultState();
        this.defaultBridge = Registries.BLOCK.get(defaultBridgeId).getDefaultState();
        this.defaultEdge = Registries.BLOCK.get(defaultEdgeId).getDefaultState();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    private OctavePerlinNoiseSampler createTerrainSampler(NoiseConfig noiseConfig) {
        Random random = noiseConfig.getOrCreateRandomDeriver(Identifier.of(Biospheres.MOD_ID, "terrain_noise"))
            .split("biospheres:terrain_noise");
        return OctavePerlinNoiseSampler.createLegacy(random, IntStream.rangeClosed(-3, 0));
    }

    private BlockPos getNearestSphereCenter(NoiseConfig noiseConfig, int x, int z) {
        int centerX = BiospheresSphereMath.nearestCenter(x, this.sphereDistance);
        int centerZ = BiospheresSphereMath.nearestCenter(z, this.sphereDistance);
        MultiNoiseUtil.NoiseValuePoint point = noiseConfig.getMultiNoiseSampler().sample(centerX >> 2, 0, centerZ >> 2);
        int centerY = BiospheresSphereMath.pickCenterY(point, this.sphereRadius, this.minimumY, this.worldHeight);
        return new BlockPos(centerX, centerY, centerZ);
    }

    private BlockState getLakeBlock(NoiseConfig noiseConfig, BlockPos center) {
        MultiNoiseUtil.NoiseValuePoint point = noiseConfig.getMultiNoiseSampler().sample(center.getX() >> 2, center.getY() >> 2, center.getZ() >> 2);
        if (!BiospheresSphereMath.hasLake(point)) {
            return Blocks.AIR.getDefaultState();
        }
        return BiospheresSphereMath.isLavaLake(point) ? Blocks.LAVA.getDefaultState() : this.defaultFluid;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        OctavePerlinNoiseSampler terrainNoise = this.createTerrainSampler(noiseConfig);
        ChunkPos chunkPos = chunk.getPos();
        BlockPos center = this.getNearestSphereCenter(noiseConfig, chunkPos.getCenterX(), chunkPos.getCenterZ());
        BlockState lakeState = this.getLakeBlock(noiseConfig, center);

        for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
            for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
                double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z, false));
                if (radialDistance > this.sphereRadius) {
                    continue;
                }

                double sphereHalfHeight = Math.sqrt((double) this.sphereRadius * this.sphereRadius
                    - (center.getX() - x) * (double) (center.getX() - x)
                    - (center.getZ() - z) * (double) (center.getZ() - z));
                double columnNoise = terrainNoise.sample(x / 8.0D, 0.0D, z / 8.0D) / 8.0D;

                for (int y = center.getY() - (int) sphereHalfHeight; y <= center.getY() + (int) sphereHalfHeight; y++) {
                    double shellDistance = Math.sqrt(center.getSquaredDistance(x, y, z, false));
                    double threshold = columnNoise + (double) y / (double) center.getY();
                    BlockState state = y * threshold < center.getY() ? this.defaultBlock : Blocks.AIR.getDefaultState();

                    if (state.isOf(this.defaultBlock.getBlock()) && shellDistance <= this.lakeRadius && !lakeState.isAir()) {
                        state = y * threshold >= center.getY() - 1 ? Blocks.AIR.getDefaultState() : lakeState;
                    }

                    chunk.setBlockState(new BlockPos(x, y, z), state, false);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
        super.generateFeatures(world, chunk, structureAccessor);
        this.finishBiospheres(world, chunk, structureAccessor);
    }

    @Override
    public int getWorldHeight() {
        return this.worldHeight;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinimumY() {
        return this.minimumY;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockPos center = this.getNearestSphereCenter(noiseConfig, x, z);
        double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z, false));
        if (radialDistance > this.sphereRadius) {
            return this.minimumY;
        }
        double sphereHalfHeight = Math.sqrt((double) this.sphereRadius * this.sphereRadius
            - (center.getX() - x) * (double) (center.getX() - x)
            - (center.getZ() - z) * (double) (center.getZ() - z));
        return center.getY() + (int) sphereHalfHeight;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int topY = this.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noiseConfig);
        BlockState[] states = new BlockState[world.getHeight()];
        for (int y = 0; y < states.length; y++) {
            states[y] = y + world.getBottomY() <= topY ? this.defaultBlock : Blocks.AIR.getDefaultState();
        }
        return new VerticalBlockSample(world.getBottomY(), states);
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        BlockPos center = this.getNearestSphereCenter(noiseConfig, pos.getX(), pos.getZ());
        text.add("Biospheres center: " + center.toShortString());
    }

    public int getSphereDistance() {
        return this.sphereDistance;
    }

    public int getSphereRadius() {
        return this.sphereRadius;
    }

    public int getLakeRadius() {
        return this.lakeRadius;
    }

    public int getShoreRadius() {
        return this.shoreRadius;
    }

    public Identifier getDefaultBlockId() {
        return this.defaultBlockId;
    }

    public Identifier getDefaultFluidId() {
        return this.defaultFluidId;
    }

    public Identifier getDefaultBridgeId() {
        return this.defaultBridgeId;
    }

    public Identifier getDefaultEdgeId() {
        return this.defaultEdgeId;
    }
}
```

Port the old `finishBiospheres`, `makeBridges`, and `fillBridgeSlice` loops into the modern class unchanged in shape, but update them to write through `StructureWorldAccess` instead of `ChunkRegion`.

- [ ] **Step 5: Remove the unused empty type and get the base port compiling**

Delete `src/main/java/xyz/coolsa/biosphere/BiospheresGeneratorType.java`.

Run: `./gradlew.bat clean test build`

Expected: PASS. The generator now compiles and packages on 1.21.11, even before the final surface pass and world preset resources are added.

- [ ] **Step 6: Commit the modernized runtime worldgen port**

```bash
git add src/main/java/xyz/coolsa/biosphere/Biospheres.java src/main/java/xyz/coolsa/biosphere/BiospheresBiomeSource.java src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java
git rm src/main/java/xyz/coolsa/biosphere/BiospheresGeneratorType.java
git commit -m "feat(worldgen): port biospheres generator to 1.21.11"
```

### Task 4: Restore the surface pass and expose a normal world preset

**Files:**
- Modify: `src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java`
- Create: `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`
- Create: `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json`
- Create: `src/main/resources/assets/biospheres/lang/en_us.json`
- Create: `src/main/resources/assets/biospheres/icon.png`
- Modify: `src/main/resources/fabric.mod.json`

- [ ] **Step 1: Restore a biome-aware surface pass so sphere tops are not bare stone**

Replace the empty `buildSurface` override in `BiospheresChunkGenerator.java` with a column pass that rewrites the exposed top of each sphere column based on biome tags. This is smaller than a full `NoiseChunkGenerator` transplant and is enough to restore the visible surface behavior that the old mod relied on.

Use this implementation shape:

```java
@Override
public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    ChunkPos chunkPos = chunk.getPos();
    BlockPos.Mutable mutable = new BlockPos.Mutable();

    for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
        for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
            int surfaceY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x & 15, z & 15);
            if (surfaceY <= this.minimumY) {
                continue;
            }

            RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(x >> 2, surfaceY >> 2, z >> 2);
            BlockState topState = this.pickTopMaterial(biome, mutable.set(x, surfaceY, z));
            BlockState underState = this.pickUnderMaterial(biome, mutable);

            if (chunk.getBlockState(mutable).isOf(this.defaultBlock.getBlock())) {
                chunk.setBlockState(mutable, topState, false);
            }

            for (int depth = 1; depth <= 3; depth++) {
                mutable.set(x, surfaceY - depth, z);
                if (!chunk.getBlockState(mutable).isOf(this.defaultBlock.getBlock())) {
                    break;
                }
                chunk.setBlockState(mutable, underState, false);
            }
        }
    }
}

private BlockState pickTopMaterial(RegistryEntry<Biome> biome, BlockPos pos) {
    if (biome.isIn(BiomeTags.IS_BADLANDS)) {
        return Blocks.RED_SAND.getDefaultState();
    }
    if (biome.isIn(BiomeTags.IS_DESERT) || biome.isIn(BiomeTags.IS_BEACH)) {
        return Blocks.SAND.getDefaultState();
    }
    if (biome.isIn(BiomeTags.IS_SNOWY)) {
        return Blocks.SNOW_BLOCK.getDefaultState();
    }
    if (biome.matchesKey(BiomeKeys.MUSHROOM_FIELDS)) {
        return Blocks.MYCELIUM.getDefaultState();
    }
    return Blocks.GRASS_BLOCK.getDefaultState();
}

private BlockState pickUnderMaterial(RegistryEntry<Biome> biome, BlockPos pos) {
    if (biome.isIn(BiomeTags.IS_BADLANDS)) {
        return Blocks.RED_SANDSTONE.getDefaultState();
    }
    if (biome.isIn(BiomeTags.IS_DESERT) || biome.isIn(BiomeTags.IS_BEACH)) {
        return Blocks.SANDSTONE.getDefaultState();
    }
    return Blocks.DIRT.getDefaultState();
}
```

The required success condition for this step is visible biome top material again: grass on plains spheres, sand on desert spheres, snow surfaces on cold spheres, and no regression in the glass shell or bridges.

- [ ] **Step 2: Add the data-driven world preset and visible world preset tag**

Create `src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json`.

```json
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "biospheres:biosphere",
        "sphere_distance": 128,
        "sphere_radius": 32,
        "lake_radius": 16,
        "shore_radius": 6,
        "minimum_y": -64,
        "world_height": 384,
        "default_block": "minecraft:stone",
        "default_fluid": "minecraft:water",
        "default_bridge": "minecraft:oak_planks",
        "default_edge": "minecraft:oak_fence",
        "biome_source": {
          "type": "biospheres:sphere_biomes",
          "sphere_distance": 128,
          "sphere_radius": 32,
          "void_biome": "minecraft:the_void",
          "biomes": [
            "minecraft:plains",
            "minecraft:ocean",
            "minecraft:desert",
            "minecraft:windswept_hills",
            "minecraft:forest",
            "minecraft:taiga",
            "minecraft:swamp",
            "minecraft:river",
            "minecraft:frozen_ocean",
            "minecraft:frozen_river",
            "minecraft:mushroom_fields",
            "minecraft:beach",
            "minecraft:jungle",
            "minecraft:savanna",
            "minecraft:badlands",
            "minecraft:warm_ocean",
            "minecraft:lukewarm_ocean",
            "minecraft:cold_ocean",
            "minecraft:deep_ocean",
            "minecraft:sunflower_plains",
            "minecraft:flower_forest",
            "minecraft:ice_spikes",
            "minecraft:dark_forest",
            "minecraft:birch_forest",
            "minecraft:old_growth_pine_taiga",
            "minecraft:windswept_forest",
            "minecraft:snowy_plains",
            "minecraft:stony_shore",
            "minecraft:eroded_badlands"
          ]
        }
      }
    },
    "minecraft:the_nether": {
      "type": "minecraft:the_nether",
      "generator": {
        "type": "minecraft:noise",
        "settings": "minecraft:nether",
        "biome_source": {
          "type": "minecraft:multi_noise",
          "preset": "minecraft:nether"
        }
      }
    },
    "minecraft:the_end": {
      "type": "minecraft:the_end",
      "generator": {
        "type": "minecraft:noise",
        "settings": "minecraft:end",
        "biome_source": {
          "type": "minecraft:the_end"
        }
      }
    }
  }
}
```

Create `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json`.

```json
{
  "replace": false,
  "values": [
    "biospheres:biospheres"
  ]
}
```

- [ ] **Step 3: Add the preset name and move the icon into the correct namespace**

Create `src/main/resources/assets/biospheres/lang/en_us.json`.

```json
{
  "generator.biospheres.biospheres": "Biospheres"
}
```

Move `src/main/resources/assets/modid/icon.png` to `src/main/resources/assets/biospheres/icon.png`.

- [ ] **Step 4: Run the full build and verify the preset files are packaged**

Run: `./gradlew.bat clean build`

Expected: PASS

Run: `jar tf build/libs/biospheres_generator-0.0.3.jar | findstr /C:"worldgen/world_preset/biospheres.json" /C:"tags/worldgen/world_preset/normal.json" /C:"assets/biospheres/lang/en_us.json"`

Expected: all three resource paths are present in the built jar.

- [ ] **Step 5: Commit the world preset and surface restoration work**

```bash
git add src/main/java/xyz/coolsa/biosphere/BiospheresChunkGenerator.java src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json src/main/resources/assets/biospheres/lang/en_us.json src/main/resources/assets/biospheres/icon.png src/main/resources/fabric.mod.json
git commit -m "feat(preset): add biospheres world preset"
```

### Task 5: Verify client and dedicated server behavior, then update the README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Start the client and create a fresh Biospheres world from the UI**

Run: `./gradlew.bat runClient`

Expected in-game verification:

- The Create World screen shows `Biospheres` in the normal world preset list.
- The new world has void gaps between spheres.
- Sphere interiors are populated with biome-appropriate surfaces and vegetation.
- Glass shells and bridges are present.
- Different spheres resolve to different biomes.

- [ ] **Step 2: Start a dedicated server using the Biospheres preset**

Run: `./gradlew.bat runServer`

After the first launch, set `level-type=biospheres:biospheres` in `run/server/server.properties`, delete `run/server/world`, and launch `./gradlew.bat runServer` again.

Expected: the server starts without registry or codec errors and generates a Biospheres overworld.

- [ ] **Step 3: Update the README with the new target version and server instructions**

Update `README.md` so the version and setup text matches the port.

```md
# Biospheres (Fabric)

This is a recreation of Risugami's original Biospheres mod for Fabric.

## Target version

This project targets Minecraft `1.21.11` on Fabric Loader `0.19.1` with Fabric API `0.141.3+1.21.11`.

## World creation

Create a new world and pick `Biospheres` from the world preset list.

## Dedicated server

Set `level-type=biospheres:biospheres` in `server.properties` before generating a new world.

## Development

Use `./gradlew.bat build` to build the mod and `./gradlew.bat runClient` or `./gradlew.bat runServer` to test it.
```

- [ ] **Step 4: Run the final verification pass**

Run: `./gradlew.bat test build`

Expected: PASS

Manual verification checklist:

- Client preset visible
- Dedicated server world generation works
- No client-only classes referenced from common code
- No registry or codec errors on startup
- Sphere spacing, shell, bridges, and lakes match the old mod's behavior

- [ ] **Step 5: Commit the documentation and verification cleanup**

```bash
git add README.md
git commit -m "docs(readme): document 1.21.11 preset usage"
```
