package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
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
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.Chunk;
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
		super(biomeSource);
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
				double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z));
				if (radialDistance > this.sphereRadius) {
					continue;
				}

				double sphereHalfHeight = Math.sqrt((double) this.sphereRadius * this.sphereRadius
					- (center.getX() - x) * (double) (center.getX() - x)
					- (center.getZ() - z) * (double) (center.getZ() - z));
				double columnNoise = terrainNoise.sample(x / 8.0D, 0.0D, z / 8.0D) / 8.0D;

				for (int y = center.getY() - (int) sphereHalfHeight; y <= center.getY() + (int) sphereHalfHeight; y++) {
					double shellDistance = Math.sqrt(center.getSquaredDistance(x, y, z));
					double threshold = columnNoise + (double) y / (double) center.getY();
					BlockState state = y * threshold < center.getY() ? this.defaultBlock : Blocks.AIR.getDefaultState();

					if (state.isOf(this.defaultBlock.getBlock()) && shellDistance <= this.lakeRadius && !lakeState.isAir()) {
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
			double radialDistance = Math.sqrt(center.getSquaredDistance(x, center.getY(), z));
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

	private void finishBiospheres(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
		ChunkPos chunkPos = chunk.getPos();
		BlockPos centerPos = this.getNearestSphereCenter(world.getSeed(), chunkPos.getCenterX(), chunkPos.getCenterZ());
		BlockPos.Mutable current = new BlockPos.Mutable();

		for (int x = chunkPos.getStartX() - 7; x <= chunkPos.getEndX() + 7; x++) {
			for (int z = chunkPos.getStartZ() - 7; z <= chunkPos.getEndZ() + 7; z++) {
				double radialDistance = Math.sqrt(centerPos.getSquaredDistance(x, centerPos.getY(), z));
				if (radialDistance > this.sphereRadius + 16) {
					continue;
				}

				double sphereHalfHeight = Math.sqrt((double) this.sphereRadius * this.sphereRadius
					- (centerPos.getX() - x) * (double) (centerPos.getX() - x)
					- (centerPos.getZ() - z) * (double) (centerPos.getZ() - z));
				double largerSphereHalfHeight = Math.sqrt((double) (this.sphereRadius + 16) * (this.sphereRadius + 16)
					- (centerPos.getX() - x) * (double) (centerPos.getX() - x)
					- (centerPos.getZ() - z) * (double) (centerPos.getZ() - z));

				for (int y = centerPos.getY() - (int) sphereHalfHeight; y <= centerPos.getY() + (int) sphereHalfHeight; y++) {
					double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
					if (newRadialDistance <= this.sphereRadius - 1) {
						continue;
					}

					BlockState blockState = y * (1.0D + (double) y / (double) centerPos.getY()) >= centerPos.getY()
						? Blocks.GLASS.getDefaultState()
						: this.defaultBlock;
					world.setBlockState(current.set(x, y, z), blockState, 0);
				}

				for (int y = 0; y <= centerPos.getY() + (int) largerSphereHalfHeight; y++) {
					double newRadialDistance = Math.sqrt(centerPos.getSquaredDistance(x, y, z));
					if (newRadialDistance >= this.sphereRadius) {
						world.setBlockState(current.set(x, y, z), Blocks.AIR.getDefaultState(), 0);
					}
				}

				this.makeBridges(new BlockPos(x, 0, z), centerPos, this.getClosestSpheres(world.getSeed(), centerPos), world, current);
			}
		}
	}

	private BlockPos getNearestSphereCenter(long seed, int x, int z) {
		int centerX = BiospheresSphereMath.nearestCenter(x, this.sphereDistance);
		int centerZ = BiospheresSphereMath.nearestCenter(z, this.sphereDistance);
		long mixed = seed ^ (centerX * 341873128712L) ^ (centerZ * 132897987541L);
		MultiNoiseUtil.NoiseValuePoint point = new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 0L, 0L, mixed, 0L);
		int centerY = BiospheresSphereMath.pickCenterY(point, this.sphereRadius, this.minimumY, this.worldHeight);
		return new BlockPos(centerX, centerY, centerZ);
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
		for (int i = 0; i < 4; i++) {
			if (radialDistance > this.sphereRadius - 2) {
				double slope = nesw[i].getY() - centerPos.getY();
				double currentPos = 0;
				switch (i) {
					case 0 -> {
						slope /= Math.abs((double) (centerPos.getZ() - nesw[i].getZ())) - 2 * this.sphereRadius;
						currentPos = centerPos.getX() - pos.getX() + this.sphereRadius;
						if (pos.getZ() <= centerPos.getZ() + 2 && pos.getZ() >= centerPos.getZ() - 2 && pos.getX() > centerPos.getX()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 1 -> {
						slope /= -Math.abs((double) (centerPos.getZ() - nesw[i].getZ())) + 2 * this.sphereRadius;
						currentPos = centerPos.getX() - pos.getX() - this.sphereRadius;
						if (pos.getZ() <= centerPos.getZ() + 2 && pos.getZ() >= centerPos.getZ() - 2 && pos.getX() < centerPos.getX()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 2 -> {
						slope /= -Math.abs((double) (centerPos.getZ() - nesw[i].getZ())) + 2 * this.sphereRadius;
						currentPos = centerPos.getZ() - pos.getZ() + this.sphereRadius;
						if (pos.getX() <= centerPos.getX() + 2 && pos.getX() >= centerPos.getX() - 2 && pos.getZ() > centerPos.getZ()) {
							this.fillBridgeSlice(new BlockPos(pos.getX(), (int) (slope * currentPos + centerPos.getY()), pos.getZ()), world, current);
						}
					}
					case 3 -> {
						slope /= Math.abs((double) (centerPos.getZ() - nesw[i].getZ())) - 2 * this.sphereRadius;
						currentPos = centerPos.getZ() - pos.getZ() - this.sphereRadius;
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
