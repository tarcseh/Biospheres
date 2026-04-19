package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresTerrainProfileTest {

	@Test
	void detectsSolidAndLakeColumnsAroundTheProfileCenter() {
		BiospheresTerrainProfile profile = new BiospheresTerrainProfile(10, 64, 20, 6);

		assertTrue(profile.containsSolidAt(10, 64, 20));
		assertTrue(profile.containsLakeAt(10, 64, 20));
		assertFalse(profile.containsSolidAt(30, 64, 20));
		assertFalse(profile.containsLakeAt(30, 64, 20));
	}
}
