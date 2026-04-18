package xyz.coolsa.biosphere;

import com.mojang.serialization.Codec;
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

/**
 * Legacy biome source from Biospheres v1 (master branch).
 * Uses fixed sphere radius instead of variable min/max radius.
 */
public final class BiospheresLegacyBiomeSource extends BiomeSource {
	public static final MapCodec<BiospheresLegacyBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		RegistryFixedCodec.of(RegistryKeys.BIOME).listOf().fieldOf("biomes").forGetter(BiospheresLegacyBiomeSource::getSphereBiomes),
		RegistryFixedCodec.of(RegistryKeys.BIOME).fieldOf("void_biome").forGetter(BiospheresLegacyBiomeSource::getVoidBiome),
		Codec.INT.optionalFieldOf("sphere_distance", 128).forGetter(BiospheresLegacyBiomeSource::getSphereDistance),
		Codec.INT.optionalFieldOf("sphere_radius", 32).forGetter(BiospheresLegacyBiomeSource::getSphereRadius)
	).apply(instance, BiospheresLegacyBiomeSource::new));

	private final List<RegistryEntry<Biome>> biomes;
	private final RegistryEntry<Biome> voidBiome;
	private final int sphereDistance;
	private final int sphereRadius;

	public BiospheresLegacyBiomeSource(List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> voidBiome, int sphereDistance, int sphereRadius) {
		this.biomes = List.copyOf(biomes);
		this.voidBiome = voidBiome;
		this.sphereDistance = sphereDistance;
		this.sphereRadius = sphereRadius;
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
		if (!BiospheresSphereMath.isInsideSphereBand(x, z, this.sphereDistance, this.sphereRadius, this.sphereRadius, 6)) {
			return this.voidBiome;
		}

		int centerX = BiospheresSphereMath.nearestCenter(x * 4, this.sphereDistance);
		int centerZ = BiospheresSphereMath.nearestCenter(z * 4, this.sphereDistance);
		int index = BiospheresSphereMath.pickIndexForSphere(centerX, centerZ, this.biomes.size());
		return this.biomes.get(index);
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

	public int getSphereRadius() {
		return this.sphereRadius;
	}

	public List<Identifier> getBiomeIds() {
		return this.biomes.stream().map(entry -> entry.getKeyOrValue().left().orElseThrow().getValue()).toList();
	}

	public Identifier getVoidBiomeId() {
		return this.voidBiome.getKeyOrValue().left().orElseThrow().getValue();
	}
}
