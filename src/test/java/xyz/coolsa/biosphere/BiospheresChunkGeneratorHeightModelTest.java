package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresChunkGeneratorHeightModelTest {

	@Test
	void mountainLikeAndFlatInputsProduceDifferentMaskedSurfaceHeights() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);

		int mountainMasked = BiospheresChunkHeightModel.computeMaskedSurfaceY(
			sphere,
			sphere.centerX(),
			sphere.centerZ(),
			sphere.centerY() + sphere.radius() + 20,
			-64
		);
		int flatMasked = BiospheresChunkHeightModel.computeMaskedSurfaceY(
			sphere,
			sphere.centerX(),
			sphere.centerZ(),
			sphere.centerY() - 8,
			-64
		);

		assertTrue(mountainMasked > flatMasked);
		assertEquals(sphere.centerY() + sphere.radius(), mountainMasked);
		assertEquals(sphere.centerY() - 8, flatMasked);
	}

	@Test
	void outsideSphereColumnsAlwaysReturnMinimumY() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);

		int masked = BiospheresChunkHeightModel.computeMaskedSurfaceY(
			sphere,
			sphere.centerX() + sphere.radius() + 12,
			sphere.centerZ(),
			sphere.centerY() + 18,
			-64
		);

		assertEquals(-64, masked);
	}

	@Test
	void maskedHeightUsesColumnSliceNotFullSphereTop() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 96, 97, 90, 16);
		int x = sphere.centerX() + sphere.radius() - 1;

		int masked = BiospheresChunkHeightModel.computeMaskedSurfaceY(sphere, x, sphere.centerZ(), 220, -64);
		assertTrue(masked < sphere.centerY() + sphere.radius());
	}

}
