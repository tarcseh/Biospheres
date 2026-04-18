package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public final class BiospheresChunkGenerator extends ChunkGenerator {
	public static final MapCodec<BiospheresChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		BiomeSource.CODEC.fieldOf("biome_source").forGetter(BiospheresChunkGenerator::getBiomeSource),
		Codec.INT.optionalFieldOf("sphere_distance", 384).forGetter(BiospheresChunkGenerator::getSphereDistance),
		Codec.INT.optionalFieldOf("min_sphere_radius", 20).forGetter(BiospheresChunkGenerator::getMinSphereRadius),
		Codec.INT.optionalFieldOf("max_sphere_radius", 160).forGetter(BiospheresChunkGenerator::getMaxSphereRadius),
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
	private final int minSphereRadius;
	private final int maxSphereRadius;
	private final int lakeRadius;
	private final int shoreRadius;
	private final int minimumY;
	private final int worldHeight;
	private final BiospheresSphereLayout layout;
	private final BiospheresStructureRouting structureRouting;
	private final Identifier defaultBlockId;
	private final Identifier defaultFluidId;
	private final Identifier defaultBridgeId;
	private final Identifier defaultEdgeId;
	private final BlockState defaultBlock;
	private final BlockState defaultFluid;
	private final BlockState defaultBridge;
	private final BlockState defaultEdge;
	private volatile NoiseChunkGenerator carverDelegate;

	public BiospheresChunkGenerator(
		BiomeSource biomeSource,
		int sphereDistance,
		int minSphereRadius,
		int maxSphereRadius,
		int lakeRadius,
		int shoreRadius,
		int minimumY,
		int worldHeight,
		Identifier defaultBlockId,
		Identifier defaultFluidId,
		Identifier defaultBridgeId,
		Identifier defaultEdgeId
	) {
		super(biomeSource);
		this.sphereDistance = sphereDistance;
		this.minSphereRadius = minSphereRadius;
		this.maxSphereRadius = maxSphereRadius;
		this.lakeRadius = lakeRadius;
		this.shoreRadius = shoreRadius;
		this.minimumY = minimumY;
		this.worldHeight = worldHeight;
		this.layout = new BiospheresSphereLayout(sphereDistance, minSphereRadius, maxSphereRadius, lakeRadius, shoreRadius, minimumY, worldHeight);
		this.structureRouting = BiospheresStructureRouting.defaultRouting();
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
		return this.layout.resolve(x, z).centerPos();
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
				BiospheresSphereDescriptor descriptor = this.layout.resolve(x, z);
				double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z));
				if (radialDistance > descriptor.radius()) {
					continue;
				}

				double sphereHalfHeight = Math.sqrt((double) descriptor.radius() * descriptor.radius()
					- (center.getX() - x) * (double) (center.getX() - x)
					- (center.getZ() - z) * (double) (center.getZ() - z));
				double columnNoise = terrainNoise.sample(x / 8.0D, 0.0D, z / 8.0D) / 8.0D;

				for (int y = center.getY() - (int) sphereHalfHeight; y <= center.getY() + (int) sphereHalfHeight; y++) {
					double shellDistance = Math.sqrt(center.getSquaredDistance(x, y, z));
					double threshold = columnNoise + (double) y / (double) center.getY();
					BlockState state = y * threshold < center.getY() ? this.defaultBlock : Blocks.AIR.getDefaultState();

					if (state.isOf(this.defaultBlock.getBlock()) && shellDistance <= descriptor.lakeRadius() && !lakeState.isAir()) {
						state = y * threshold >= center.getY() - 1 ? Blocks.AIR.getDefaultState() : lakeState;
					}

					chunk.setBlockState(new BlockPos(x, y, z), state, 0);
				}
			}
		}

		return CompletableFuture.completedFuture(chunk);
	}

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
					chunk.setBlockState(mutable, topState, 0);
				}

				for (int depth = 1; depth <= 3; depth++) {
					mutable.set(x, surfaceY - depth, z);
					if (!chunk.getBlockState(mutable).isOf(this.defaultBlock.getBlock())) {
						break;
					}
					chunk.setBlockState(mutable, underState, 0);
				}
			}
		}
	}

	private BlockState pickTopMaterial(RegistryEntry<Biome> biome, BlockPos pos) {
		if (biome.isIn(BiomeTags.IS_BADLANDS)) {
			return Blocks.RED_SAND.getDefaultState();
		}
		if (biome.matchesKey(BiomeKeys.DESERT) || biome.isIn(BiomeTags.IS_BEACH)) {
			return Blocks.SAND.getDefaultState();
		}
		if (biome.matchesKey(BiomeKeys.SNOWY_PLAINS) || biome.matchesKey(BiomeKeys.ICE_SPIKES)) {
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
		if (biome.matchesKey(BiomeKeys.DESERT) || biome.isIn(BiomeTags.IS_BEACH)) {
			return Blocks.SANDSTONE.getDefaultState();
		}
		return Blocks.DIRT.getDefaultState();
	}

	@Override
	public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk) {
		this.getOrCreateCarverDelegate(chunkRegion).carve(chunkRegion, seed, noiseConfig, biomeAccess, structureAccessor, chunk);
	}

	private NoiseChunkGenerator getOrCreateCarverDelegate(ChunkRegion chunkRegion) {
		NoiseChunkGenerator delegate = this.carverDelegate;
		if (delegate != null) {
			return delegate;
		}

			synchronized (this) {
				delegate = this.carverDelegate;
				if (delegate == null) {
					RegistryEntry<ChunkGeneratorSettings> overworldSettings = chunkRegion.getRegistryManager()
						.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
						.getOrThrow(ChunkGeneratorSettings.OVERWORLD);

					delegate = new NoiseChunkGenerator(this.getBiomeSource(), overworldSettings);
					this.carverDelegate = delegate;
				}
		}

		return delegate;
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
	public void setStructureStarts(
		DynamicRegistryManager registryManager,
		StructurePlacementCalculator placementCalculator,
		StructureAccessor structureAccessor,
		Chunk chunk,
		StructureTemplateManager structureTemplateManager,
		RegistryKey<World> dimension
	) {
		if (SharedConstants.DISABLE_STRUCTURES) {
			return;
		}

		ChunkPos chunkPos = chunk.getPos();
		ChunkSectionPos sectionPos = ChunkSectionPos.from(chunk);
		NoiseConfig noiseConfig = placementCalculator.getNoiseConfig();

		for (RegistryEntry<StructureSet> structureSetEntry : placementCalculator.getStructureSets()) {
			this.trySetStructureStarts(
				structureSetEntry,
				registryManager,
				placementCalculator,
				structureAccessor,
				chunk,
				chunkPos,
				sectionPos,
				noiseConfig,
				structureTemplateManager,
				dimension
			);
		}
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
		BiospheresSphereDescriptor descriptor = this.layout.resolve(x, z);
		BlockPos center = descriptor.centerPos();
		double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z));
		if (radialDistance > descriptor.radius()) {
			return this.minimumY;
		}
		double sphereHalfHeight = Math.sqrt((double) descriptor.radius() * descriptor.radius()
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

	private void finishBiospheres(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
		ChunkPos chunkPos = chunk.getPos();
		BlockPos centerPos = this.getNearestSphereCenter(world.getSeed(), chunkPos.getCenterX(), chunkPos.getCenterZ());
		BlockPos.Mutable current = new BlockPos.Mutable();
		BlockPos[] closestSpheres = this.getClosestSpheres(world.getSeed(), centerPos);
		BiospheresSphereDescriptor centerDescriptor = this.layout.resolve(centerPos.getX(), centerPos.getZ());

		for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
			for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
				double radialDistance = Math.sqrt(centerPos.getSquaredDistance(x, centerPos.getY(), z));
				if (radialDistance <= centerDescriptor.radius() + 16) {
					double sphereHalfHeight = Math.sqrt((double) centerDescriptor.radius() * centerDescriptor.radius()
						- (centerPos.getX() - x) * (double) (centerPos.getX() - x)
						- (centerPos.getZ() - z) * (double) (centerPos.getZ() - z));
					double largerSphereHalfHeight = Math.sqrt((double) (centerDescriptor.radius() + 16) * (centerDescriptor.radius() + 16)
						- (centerPos.getX() - x) * (double) (centerPos.getX() - x)
						- (centerPos.getZ() - z) * (double) (centerPos.getZ() - z));

					for (int y = centerPos.getY() - (int) sphereHalfHeight; y <= centerPos.getY() + (int) sphereHalfHeight; y++) {
						double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
						if (newRadialDistance <= centerDescriptor.radius() - 1) {
							continue;
						}

					world.setBlockState(current.set(x, y, z), Blocks.GLASS.getDefaultState(), 0);
				}

				for (int y = this.minimumY; y <= centerPos.getY() + (int) largerSphereHalfHeight; y++) {
					double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
					if (newRadialDistance >= centerDescriptor.radius()) {
						world.setBlockState(current.set(x, y, z), Blocks.AIR.getDefaultState(), 0);
						}
					}
				}

				this.makeBridges(new BlockPos(x, 0, z), centerPos, closestSpheres, world, current);
			}
		}
	}

	private BlockPos getNearestSphereCenter(long seed, int x, int z) {
		return this.layout.resolve(x, z).centerPos();
	}

	private void trySetStructureStarts(
		RegistryEntry<StructureSet> structureSetEntry,
		DynamicRegistryManager registryManager,
		StructurePlacementCalculator placementCalculator,
		StructureAccessor structureAccessor,
		Chunk chunk,
		ChunkPos chunkPos,
		ChunkSectionPos sectionPos,
		NoiseConfig noiseConfig,
		StructureTemplateManager structureTemplateManager,
		RegistryKey<World> dimension
	) {
		StructureSet structureSet = structureSetEntry.value();
		StructurePlacement placement = structureSet.placement();
		List<StructureSet.WeightedEntry> weightedEntries = structureSet.structures();

		for (StructureSet.WeightedEntry weightedEntry : weightedEntries) {
			Structure structure = weightedEntry.structure().value();
			StructureStart existingStart = structureAccessor.getStructureStart(sectionPos, structure, chunk);
			if (existingStart != null && existingStart.hasChildren()) {
				return;
			}
		}

		if (!placement.shouldGenerate(placementCalculator, chunkPos.x, chunkPos.z)) {
			return;
		}

		long structureSeed = placementCalculator.getStructureSeed();
		if (weightedEntries.size() == 1) {
			this.trySetStructureStart(
				weightedEntries.getFirst(),
				registryManager,
				noiseConfig,
				structureTemplateManager,
				structureSeed,
				structureAccessor,
				chunk,
				chunkPos,
				sectionPos,
				dimension
			);
			return;
		}

		ArrayList<StructureSet.WeightedEntry> candidates = new ArrayList<>(weightedEntries);
		ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
		random.setCarverSeed(structureSeed, chunkPos.x, chunkPos.z);
		int totalWeight = 0;
		for (StructureSet.WeightedEntry weightedEntry : candidates) {
			totalWeight += weightedEntry.weight();
		}

		while (!candidates.isEmpty()) {
			int target = random.nextInt(totalWeight);
			int index = 0;
			for (; index < candidates.size(); index++) {
				target -= candidates.get(index).weight();
				if (target < 0) {
					break;
				}
			}

			StructureSet.WeightedEntry candidate = candidates.get(index);
			if (this.trySetStructureStart(
				candidate,
				registryManager,
				noiseConfig,
				structureTemplateManager,
				structureSeed,
				structureAccessor,
				chunk,
				chunkPos,
				sectionPos,
				dimension
			)) {
				return;
			}

			candidates.remove(index);
			totalWeight -= candidate.weight();
		}
	}

	private boolean trySetStructureStart(
		StructureSet.WeightedEntry weightedEntry,
		DynamicRegistryManager registryManager,
		NoiseConfig noiseConfig,
		StructureTemplateManager structureTemplateManager,
		long structureSeed,
		StructureAccessor structureAccessor,
		Chunk chunk,
		ChunkPos chunkPos,
		ChunkSectionPos sectionPos,
		RegistryKey<World> dimension
	) {
		RegistryEntry<Structure> structureEntry = weightedEntry.structure();
		Structure structure = structureEntry.value();
		int references = this.getStructureReferences(structureAccessor, chunk, sectionPos, structure);
		Predicate<RegistryEntry<Biome>> validBiomePredicate = structure.getValidBiomes()::contains;
		StructureStart start = structure.createStructureStart(
			structureEntry,
			dimension,
			registryManager,
			this,
			this.biomeSource,
			noiseConfig,
			structureTemplateManager,
			structureSeed,
			chunkPos,
			references,
			chunk,
			validBiomePredicate
		);
		if (!start.hasChildren()) {
			return false;
		}

		Optional<Identifier> structureId = structureEntry.getKey().map(RegistryKey::getValue);
		BlockPos structureCenter = start.getBoundingBox().getCenter();
		BiospheresSphereDescriptor sphere = this.layout.resolve(structureCenter.getX(), structureCenter.getZ());
		if (structureId.isPresent() && !this.structureRouting.canAccept(structureId.get(), start.getBoundingBox(), sphere)) {
			return false;
		}

		structureAccessor.setStructureStart(sectionPos, structure, start, chunk);
		return true;
	}

	private int getStructureReferences(StructureAccessor structureAccessor, Chunk chunk, ChunkSectionPos sectionPos, Structure structure) {
		StructureStart existingStart = structureAccessor.getStructureStart(sectionPos, structure, chunk);
		return existingStart != null ? existingStart.getReferences() : 0;
	}

	private BlockPos[] getClosestSpheres(long seed, BlockPos centerPos) {
		BlockPos[] nesw = new BlockPos[4];
		for (int i = 0; i < 4; i++) {
			int xMod = centerPos.getX();
			int zMod = centerPos.getZ();
			if (i / 2 < 1) {
				xMod += (int) Math.round(Math.pow(-1, i) * this.sphereDistance);
			} else {
				zMod += (int) Math.round(Math.pow(-1, i) * this.sphereDistance);
			}
			nesw[i] = this.getNearestSphereCenter(seed, xMod, zMod);
		}
		return nesw;
	}

	private void makeBridges(BlockPos pos, BlockPos centerPos, BlockPos[] nesw, StructureWorldAccess world, BlockPos.Mutable current) {
		double radialDistance = Math.sqrt(centerPos.getSquaredDistance(pos.getX(), centerPos.getY(), pos.getZ()));
		BiospheresSphereDescriptor centerDescriptor = this.layout.resolve(centerPos.getX(), centerPos.getZ());
		for (int i = 0; i < 4; i++) {
			if (i == 1 || i == 3) {
				continue;
			}

			BiospheresSphereDescriptor neighborDescriptor = this.layout.resolve(nesw[i].getX(), nesw[i].getZ());
			if (radialDistance > centerDescriptor.radius() - 2) {
				double slope = nesw[i].getY() - centerPos.getY();
				double currentPos = 0;
				switch (i) {
					case 0 -> {
						slope /= BiospheresSphereMath.bridgeGap(centerPos.getX(), nesw[i].getX(), centerDescriptor.radius(), neighborDescriptor.radius());
						currentPos = centerPos.getX() - pos.getX() + centerDescriptor.radius();
						if (pos.getZ() <= centerPos.getZ() + 2 && pos.getZ() >= centerPos.getZ() - 2 && pos.getX() > centerPos.getX()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 1 -> {
						slope /= BiospheresSphereMath.bridgeGap(centerPos.getX(), nesw[i].getX(), centerDescriptor.radius(), neighborDescriptor.radius());
						currentPos = centerPos.getX() - pos.getX() - centerDescriptor.radius();
						if (pos.getZ() <= centerPos.getZ() + 2 && pos.getZ() >= centerPos.getZ() - 2 && pos.getX() < centerPos.getX()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 2 -> {
						slope /= BiospheresSphereMath.bridgeGap(centerPos.getZ(), nesw[i].getZ(), centerDescriptor.radius(), neighborDescriptor.radius());
						currentPos = centerPos.getZ() - pos.getZ() + centerDescriptor.radius();
						if (pos.getX() <= centerPos.getX() + 2 && pos.getX() >= centerPos.getX() - 2 && pos.getZ() > centerPos.getZ()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 3 -> {
						slope /= BiospheresSphereMath.bridgeGap(centerPos.getZ(), nesw[i].getZ(), centerDescriptor.radius(), neighborDescriptor.radius());
						currentPos = centerPos.getZ() - pos.getZ() - centerDescriptor.radius();
						if (pos.getX() <= centerPos.getX() + 2 && pos.getX() >= centerPos.getX() - 2 && pos.getZ() < centerPos.getZ()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
				}
			}
		}
	}

	private void fillBridgeSlice(BlockPos pos, StructureWorldAccess world, BlockPos.Mutable current) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		world.setBlockState(current.set(x, y - 1, z), this.defaultBridge, 0);
		world.setBlockState(current.set(x, y, z), Blocks.AIR.getDefaultState(), 0);
		world.setBlockState(current.set(x, y + 1, z), Blocks.AIR.getDefaultState(), 0);
		world.setBlockState(current.set(x, y + 2, z), Blocks.AIR.getDefaultState(), 0);
		world.setBlockState(current.set(x, y + 3, z), Blocks.AIR.getDefaultState(), 0);
	}

	public int getSphereDistance() {
		return this.sphereDistance;
	}

	public int getMinSphereRadius() {
		return this.minSphereRadius;
	}

	public int getMaxSphereRadius() {
		return this.maxSphereRadius;
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
