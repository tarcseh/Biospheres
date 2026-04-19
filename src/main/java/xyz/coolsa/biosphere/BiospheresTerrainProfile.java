package xyz.coolsa.biosphere;

public record BiospheresTerrainProfile(int bottomY, int surfaceY, int ceilingY, int lakeFloorY, int lakeTopY, boolean hasLake) {
	public BiospheresTerrainProfile {
		if (bottomY > surfaceY) {
			throw new IllegalArgumentException("bottomY must be <= surfaceY");
		}
		if (surfaceY > ceilingY) {
			throw new IllegalArgumentException("surfaceY must be <= ceilingY");
		}
		if (hasLake && lakeFloorY > lakeTopY) {
			throw new IllegalArgumentException("lakeFloorY must be <= lakeTopY when hasLake is true");
		}
	}

	public boolean containsSolidAt(int y) {
		return y >= this.bottomY && y <= this.surfaceY;
	}

	public boolean containsLakeAt(int y) {
		return this.hasLake && y >= this.lakeFloorY && y <= this.lakeTopY;
	}
}
