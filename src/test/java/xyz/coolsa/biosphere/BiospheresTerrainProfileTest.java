package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresTerrainProfileTest {

	@Test
	void detectsSolidAndLakeBandsWithinTheProfile() {
		BiospheresTerrainProfile profile = new BiospheresTerrainProfile(10, 18, 22, 12, 16, true);

		assertEquals(10, profile.bottomY());
		assertEquals(18, profile.surfaceY());
		assertEquals(22, profile.ceilingY());
		assertTrue(profile.containsSolidAt(10));
		assertTrue(profile.containsLakeAt(14));
		assertFalse(profile.containsSolidAt(9));
		assertFalse(profile.containsLakeAt(17));
	}

	@Test
	void rejectsImpossibleTerrainProfiles() {
		assertThrows(IllegalArgumentException.class, () -> new BiospheresTerrainProfile(10, 9, 22, 12, 16, true));
	}
}
