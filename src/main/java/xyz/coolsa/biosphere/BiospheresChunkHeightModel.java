package xyz.coolsa.biosphere;

final class BiospheresChunkHeightModel {
	private BiospheresChunkHeightModel() {
	}

	static int computeMaskedSurfaceY(BiospheresSphereDescriptor sphere, int x, int z, int biomeSurfaceY, int minimumY) {
		double dx = sphere.centerX() - x;
		double dz = sphere.centerZ() - z;
		double horizontalDistanceSquared = dx * dx + dz * dz;
		double radiusSquared = (double) sphere.radius() * sphere.radius();
		if (horizontalDistanceSquared > radiusSquared) {
			return minimumY;
		}

		double sphereHalfHeight = Math.sqrt(radiusSquared - horizontalDistanceSquared);
		int sphereTop = sphere.centerY() + (int) sphereHalfHeight;
		int sphereBottom = sphere.centerY() - (int) sphereHalfHeight;
		if (biomeSurfaceY < sphereBottom) {
			return minimumY;
		}

		return Math.min(sphereTop, biomeSurfaceY);
	}
}
