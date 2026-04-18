package xyz.coolsa.biosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryOps.RegistryInfo;
import net.minecraft.registry.RegistryOps.RegistryInfoGetter;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryOwner;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresWorldPresetCodecTest {

	@Test
	void decodesBiomeSourceWithRadiusRangeFields() {
		String json = """
			{
			  "biomes": [],
			  "void_biome": "minecraft:the_void",
			  "sphere_distance": 128,
			  "min_sphere_radius": 20,
			  "max_sphere_radius": 160
			}
			""";

		assertDoesNotThrow(() -> decodeCodecData(json));
	}

	@Test
	void defaultsBiomeSourceRadiusRangeWhenFieldsAreMissing() {
		String json = """
			{
			  "biomes": [],
			  "void_biome": "minecraft:the_void"
			}
			""";

		BiospheresBiomeSource.CodecData data = decodeCodecData(json);

		assertEquals(20, data.minSphereRadius());
		assertEquals(160, data.maxSphereRadius());
	}

	@Test
	void rejectsBiomeSourceWhenMinimumRadiusExceedsMaximumRadius() {
		String json = """
			{
			  "biomes": [],
			  "void_biome": "minecraft:the_void",
			  "sphere_distance": 128,
			  "min_sphere_radius": 160,
			  "max_sphere_radius": 20
			}
			""";

		assertThrows(IllegalStateException.class, () -> decodeCodecData(json));
	}

	@Test
	void worldPresetUsesGeneratorRadiusRangeFields() throws Exception {
		JsonElement preset = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json")));
		JsonElement generator = preset.getAsJsonObject()
			.getAsJsonObject("dimensions")
			.getAsJsonObject("minecraft:overworld")
			.getAsJsonObject("generator");

		assertTrue(generator.getAsJsonObject().get("max_sphere_radius").getAsInt() >= BiospheresStructureBoundsCatalog.largestRequiredHorizontalRadius());
		assertEquals(20, generator.getAsJsonObject().get("min_sphere_radius").getAsInt());
		assertEquals(160, generator.getAsJsonObject().get("max_sphere_radius").getAsInt());
	}

	@Test
	void worldPresetKeepsSphereSpacingBeyondMaximumDiameter() throws Exception {
		JsonElement preset = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/biospheres/worldgen/world_preset/biospheres.json")));
		JsonElement generator = preset.getAsJsonObject()
			.getAsJsonObject("dimensions")
			.getAsJsonObject("minecraft:overworld")
			.getAsJsonObject("generator");

		int sphereDistance = generator.getAsJsonObject().get("sphere_distance").getAsInt();
		int maxSphereRadius = generator.getAsJsonObject().get("max_sphere_radius").getAsInt();

		assertTrue(sphereDistance > maxSphereRadius * 2,
			() -> "sphere_distance must leave space between maximum-size spheres");
	}

	private static BiospheresBiomeSource.CodecData decodeCodecData(String json) {
		RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, new RegistryInfoGetter() {
			@Override
			public <T> Optional<RegistryInfo<T>> getRegistryInfo(RegistryKey<? extends net.minecraft.registry.Registry<? extends T>> key) {
				if (!RegistryKeys.BIOME.equals(key)) {
					return Optional.empty();
				}

				@SuppressWarnings("unchecked")
				RegistryInfo<T> info = (RegistryInfo<T>) createBiomeRegistryInfo();
				return Optional.of(info);
			}
		});
		return BiospheresBiomeSource.CodecData.CODEC.codec().parse(ops, JsonParser.parseString(json))
			.getOrThrow(message -> new IllegalStateException("Failed to decode biome source: " + message));
	}

	private static RegistryInfo<Biome> createBiomeRegistryInfo() {
		RegistryEntryOwner<Biome> owner = new RegistryEntryOwner<>() { };
		RegistryEntryLookup<Biome> lookup = new RegistryEntryLookup<>() {
			@Override
			public Optional<RegistryEntry.Reference<Biome>> getOptional(RegistryKey<Biome> key) {
				if (BiomeKeys.PLAINS.equals(key) || BiomeKeys.THE_VOID.equals(key)) {
					return Optional.of(RegistryEntry.Reference.standAlone(owner, key));
				}

				return Optional.empty();
			}

			@Override
			public Optional<net.minecraft.registry.entry.RegistryEntryList.Named<Biome>> getOptional(TagKey<Biome> tagKey) {
				return Optional.empty();
			}
		};

		return new RegistryInfo<>(owner, lookup, Lifecycle.stable());
	}
}
