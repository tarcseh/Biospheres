package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryFixedCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.List;
import java.util.stream.Stream;

public final class BiospheresBiomeSource extends BiomeSource {
	public static final MapCodec<BiospheresBiomeSource> CODEC = CodecData.CODEC.xmap(
		data -> new BiospheresBiomeSource(data.biomes(), data.voidBiome(), data.sphereDistance(), data.minSphereRadius(), data.maxSphereRadius()),
		BiospheresBiomeSource::toCodecData
	);

	public static final class CodecData {
		public static final MapCodec<CodecData> CODEC = RecordCodecBuilder.<CodecData>mapCodec(instance -> instance.group(
			RegistryFixedCodec.of(RegistryKeys.BIOME).listOf().fieldOf("biomes").forGetter(CodecData::biomes),
			RegistryFixedCodec.of(RegistryKeys.BIOME).fieldOf("void_biome").forGetter(CodecData::voidBiome),
			Codec.INT.optionalFieldOf("sphere_distance", 480).forGetter(CodecData::sphereDistance),
			Codec.INT.optionalFieldOf("min_sphere_radius", 160).forGetter(CodecData::minSphereRadius),
			Codec.INT.optionalFieldOf("max_sphere_radius", 224).forGetter(CodecData::maxSphereRadius)
		).apply(instance, CodecData::new)).validate(data -> {
			if (data.maxSphereRadius < data.minSphereRadius) {
				return DataResult.error(() -> "max_sphere_radius must be >= min_sphere_radius");
			}

			if (data.sphereDistance < data.maxSphereRadius * 2) {
				return DataResult.error(() -> "sphere_distance must be >= 2 * max_sphere_radius to keep adjacent spheres from overlapping");
			}

			return DataResult.success(data);
		});

		private final List<RegistryEntry<Biome>> biomes;
		private final RegistryEntry<Biome> voidBiome;
		private final int sphereDistance;
		private final int minSphereRadius;
		private final int maxSphereRadius;

		public CodecData(List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> voidBiome, int sphereDistance, int minSphereRadius, int maxSphereRadius) {
			this.biomes = List.copyOf(biomes);
			this.voidBiome = voidBiome;
			this.sphereDistance = sphereDistance;
			this.minSphereRadius = minSphereRadius;
			this.maxSphereRadius = maxSphereRadius;
		}

		public List<RegistryEntry<Biome>> biomes() {
			return this.biomes;
		}

		public RegistryEntry<Biome> voidBiome() {
			return this.voidBiome;
		}

		public int sphereDistance() {
			return this.sphereDistance;
		}

		public int minSphereRadius() {
			return this.minSphereRadius;
		}

		public int maxSphereRadius() {
			return this.maxSphereRadius;
		}
	}

	private final List<RegistryEntry<Biome>> biomes;
	private final RegistryEntry<Biome> voidBiome;
	private final int sphereDistance;
	private final int minSphereRadius;
	private final int maxSphereRadius;

	public BiospheresBiomeSource(List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> voidBiome, int sphereDistance, int minSphereRadius, int maxSphereRadius) {
		this.biomes = List.copyOf(biomes);
		this.voidBiome = voidBiome;
		this.sphereDistance = sphereDistance;
		this.minSphereRadius = minSphereRadius;
		this.maxSphereRadius = maxSphereRadius;
	}

	@Override
	protected MapCodec<? extends BiomeSource> getCodec() {
		return CODEC;
	}

	@Override
	protected Stream<RegistryEntry<Biome>> biomeStream() {
		return Stream.concat(this.biomes.stream(), Stream.of(this.voidBiome)).distinct();
	}

	@Override
	public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
		if (!BiospheresSphereMath.isInsideSphereBand(x, z, this.sphereDistance, this.minSphereRadius, this.maxSphereRadius, 6)) {
			return this.voidBiome;
		}

		int centerX = BiospheresSphereMath.nearestCenter(x * 4, this.sphereDistance);
		int centerZ = BiospheresSphereMath.nearestCenter(z * 4, this.sphereDistance);
		MultiNoiseUtil.NoiseValuePoint spherePoint = noise.sample(centerX >> 2, 0, centerZ >> 2);
		int sphereIndex = this.pickSphereIndex(spherePoint, centerX, centerZ, this.biomes.size());
		return this.biomes.get(sphereIndex);
	}

	private int pickSphereIndex(MultiNoiseUtil.NoiseValuePoint spherePoint, int centerX, int centerZ, int count) {
		if (count <= 0) {
			throw new IllegalArgumentException("count must be positive");
		}

		long mixed = spherePoint.temperatureNoise()
			^ Long.rotateLeft(spherePoint.humidityNoise(), 11)
			^ Long.rotateLeft(spherePoint.continentalnessNoise(), 22)
			^ Long.rotateLeft(spherePoint.weirdnessNoise(), 33)
			^ (long) centerX * 341873128712L
			^ Long.rotateLeft((long) centerZ * 132897987541L, 17);
		return Math.floorMod(mixed, count);
	}

	public List<RegistryEntry<Biome>> getSphereBiomes() {
		return this.biomes;
	}

	public RegistryEntry<Biome> getVoidBiome() {
		return this.voidBiome;
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

	public List<Identifier> getBiomeIds() {
		return this.biomes.stream().map(entry -> entry.getKeyOrValue().left().orElseThrow().getValue()).toList();
	}

	public Identifier getVoidBiomeId() {
		return this.voidBiome.getKeyOrValue().left().orElseThrow().getValue();
	}

	private static CodecData toCodecData(BiospheresBiomeSource source) {
		return new CodecData(source.biomes, source.voidBiome, source.sphereDistance, source.minSphereRadius, source.maxSphereRadius);
	}
}
