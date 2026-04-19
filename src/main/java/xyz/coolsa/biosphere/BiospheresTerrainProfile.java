package xyz.coolsa.biosphere;

public record BiospheresTerrainProfile(int centerX, int centerY, int centerZ, int radius) {
	public boolean containsSolidAt(int x, int y, int z) {
		return this.containsAt(x, y, z, this.radius);
	}

	public boolean containsLakeAt(int x, int y, int z) {
		return this.containsAt(x, y, z, this.radius - 1);
	}

	private boolean containsAt(int x, int y, int z, int effectiveRadius) {
		if (effectiveRadius < 0) {
			return false;
		}

		long dx = (long) x - this.centerX;
		long dy = (long) y - this.centerY;
		long dz = (long) z - this.centerZ;
		long distanceSquared = dx * dx + dy * dy + dz * dz;
		long radiusSquared = (long) effectiveRadius * effectiveRadius;
		return distanceSquared <= radiusSquared;
	}
}
