package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresTerrainShaperTest {
	private static BiospheresTerrainProfile shape(
		BiospheresTerrainShaper shaper,
		BiospheresSphereDescriptor sphere,
		double relief,
		double valley,
		boolean lakeCapable,
		BiospheresTerrainShaper.TerrainFamily family
	) {
		int bottom = sphere.centerY() - sphere.radius();
		int ceiling = sphere.centerY() + sphere.radius();
		return shaper.shapeColumn(sphere, relief, valley, lakeCapable, family, bottom, ceiling);
	}

	@Test
	void keepsFlatNonLakeColumnsFromOpeningLakeCavities() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 16, 20, 24, 12);
		BiospheresTerrainProfile profile = shape(
			new BiospheresTerrainShaper(),
			sphere,
			0.1D,
			0.0D,
			false,
			BiospheresTerrainShaper.TerrainFamily.DEFAULT
		);

		assertFalse(profile.hasLake());
	}

	@Test
	void keepsTerrainInsideTheSphereVerticalSlice() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 16, 20, 24, 12);
		BiospheresTerrainProfile profile = shape(
			new BiospheresTerrainShaper(),
			sphere,
			0.85D,
			0.15D,
			false,
			BiospheresTerrainShaper.TerrainFamily.DEFAULT
		);

		assertTrue(profile.bottomY() >= sphere.centerY() - sphere.radius());
		assertTrue(profile.ceilingY() <= sphere.centerY() + sphere.radius());
		assertTrue(profile.surfaceY() <= profile.ceilingY());
		assertTrue(profile.containsSolidAt(profile.surfaceY()));
		assertFalse(profile.containsLakeAt(profile.surfaceY()));
		assertTrue(profile.containsSolidAt(profile.lakeFloorY()));
	}

	@Test
	void givesMountainInputsMoreReliefThanFlatInputs() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 16, 20, 24, 12);
		BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

		BiospheresTerrainProfile mountain = shape(
			shaper,
			sphere,
			1.0D,
			0.8D,
			false,
			BiospheresTerrainShaper.TerrainFamily.MOUNTAIN
		);
		BiospheresTerrainProfile flat = shape(
			shaper,
			sphere,
			0.2D,
			-0.2D,
			false,
			BiospheresTerrainShaper.TerrainFamily.DEFAULT
		);

		assertTrue(mountain.surfaceY() > flat.surfaceY());
		assertEquals(sphere.centerY() - sphere.radius(), mountain.bottomY());
	}

	@Test
	void allowsLakeCapableColumnsToReserveInteriorLakeSpace() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 20, 24, 28, 18);
		BiospheresTerrainProfile profile = shape(
			new BiospheresTerrainShaper(),
			sphere,
			0.35D,
			-0.65D,
			true,
			BiospheresTerrainShaper.TerrainFamily.DEFAULT
		);

		assertTrue(profile.hasLake());
		assertTrue(profile.lakeFloorY() < profile.lakeTopY());
		assertTrue(profile.lakeTopY() < profile.surfaceY());
		assertTrue(profile.containsLakeAt(profile.lakeFloorY()));
	}

	@Test
	void clampsSmallRadiusColumnsToTheSphereBand() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 32, 0, 2, 3, 4, 2);
		BiospheresTerrainProfile profile = shape(
			new BiospheresTerrainShaper(),
			sphere,
			1.0D,
			-0.9D,
			true,
			BiospheresTerrainShaper.TerrainFamily.MOUNTAIN
		);

		assertEquals(sphere.centerY() - sphere.radius(), profile.bottomY());
		assertEquals(sphere.centerY() + sphere.radius(), profile.ceilingY());
		assertTrue(profile.surfaceY() >= profile.bottomY());
		assertTrue(profile.surfaceY() <= profile.ceilingY());
		assertTrue(profile.lakeTopY() >= profile.bottomY());
		assertTrue(profile.lakeTopY() <= profile.surfaceY());
	}

	@Test
	void oceanFamilyPrefersWaterAndLowerSurface() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 20, 24, 28, 18);
		BiospheresTerrainShaper shaper = new BiospheresTerrainShaper();

		BiospheresTerrainProfile ocean = shape(
			shaper,
			sphere,
			0.0D,
			-0.2D,
			true,
			BiospheresTerrainShaper.TerrainFamily.OCEAN
		);
		BiospheresTerrainProfile land = shape(
			shaper,
			sphere,
			0.0D,
			-0.2D,
			true,
			BiospheresTerrainShaper.TerrainFamily.DEFAULT
		);

		assertTrue(ocean.hasLake());
		assertTrue(ocean.surfaceY() < land.surfaceY());
	}
}
