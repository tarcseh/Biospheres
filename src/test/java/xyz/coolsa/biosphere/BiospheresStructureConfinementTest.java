package xyz.coolsa.biosphere;

import net.minecraft.util.math.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresStructureConfinementTest {

	@Test
	void acceptsBoundingBoxFullyInsideSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 160, 161, 158, 16);
		BlockBox box = new BlockBox(-40, 80, -40, 40, 120, 40);

		assertTrue(BiospheresStructureConfinement.isWithinSphere(box, sphere, 8));
	}

	@Test
	void rejectsBoundingBoxThatLeaksPastSphereBoundary() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 80, 81, 78, 16);
		BlockBox box = new BlockBox(-90, 60, -20, -10, 120, 20);

		assertFalse(BiospheresStructureConfinement.isWithinSphere(box, sphere, 8));
	}
}
