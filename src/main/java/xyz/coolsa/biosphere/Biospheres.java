package xyz.coolsa.biosphere;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Biospheres implements ModInitializer {

	public static final String MOD_ID = "biospheres";
	public static final Identifier CHUNK_GENERATOR_ID = Identifier.of(MOD_ID, "biosphere");
	public static final Identifier BIOME_SOURCE_ID = Identifier.of(MOD_ID, "sphere_biomes");
	public static final Identifier LEGACY_CHUNK_GENERATOR_ID = Identifier.of(MOD_ID, "biosphere_legacy");
	public static final Identifier LEGACY_BIOME_SOURCE_ID = Identifier.of(MOD_ID, "sphere_biomes_legacy");

	@Override
	public void onInitialize() {
		Registry.register(Registries.CHUNK_GENERATOR, CHUNK_GENERATOR_ID, BiospheresChunkGenerator.CODEC);
		Registry.register(Registries.BIOME_SOURCE, BIOME_SOURCE_ID, BiospheresBiomeSource.CODEC);
		Registry.register(Registries.CHUNK_GENERATOR, LEGACY_CHUNK_GENERATOR_ID, BiospheresLegacyChunkGenerator.CODEC);
		Registry.register(Registries.BIOME_SOURCE, LEGACY_BIOME_SOURCE_ID, BiospheresLegacyBiomeSource.CODEC);
	}
}
