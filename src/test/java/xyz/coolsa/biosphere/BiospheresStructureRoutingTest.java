package xyz.coolsa.biosphere;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresStructureRoutingTest {

	@Test
	void rejectsVillageWhenSphereIsBelowThePolicyThreshold() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 120, 0, 72, 73, 70, 16);

		assertFalse(BiospheresStructureRouting.defaultRouting().canStart(BiospheresStructureFamily.VILLAGE, sphere));
	}

	@Test
	void rejectsProjectedBoundsThatLeakPastTheShellMargin() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 112, 113, 110, 16);
		BlockBox projectedBox = new BlockBox(-120, 70, -20, 20, 130, 20);

		assertFalse(BiospheresStructureRouting.defaultRouting().canPlaceProjectedBounds(BiospheresStructureFamily.STRONGHOLD, sphere, projectedBox));
	}

	@Test
	void rejectsUnknownStructureClassifications() {
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("biospheres", "unknown_structure")).isEmpty());
	}
}
