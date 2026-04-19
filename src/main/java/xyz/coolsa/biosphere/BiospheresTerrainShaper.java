package xyz.coolsa.biosphere;

public final class BiospheresTerrainShaper {
	public enum TerrainFamily {
		DEFAULT,
		OCEAN,
		MOUNTAIN
	}

	public BiospheresTerrainProfile shapeColumn(
		BiospheresSphereDescriptor sphere,
		double reliefSignal,
		double valleySignal,
		boolean lakeCapable,
		TerrainFamily family,
		int columnBottomY,
		int columnCeilingY
	) {
		int sphereBottom = sphere.centerY() - sphere.radius();
		int sphereCeiling = sphere.centerY() + sphere.radius();
		int bottomY = clamp(columnBottomY, sphereBottom, sphereCeiling);
		int ceilingY = clamp(columnCeilingY, bottomY, sphereCeiling);

		double reliefScale;
		double valleyScale;
		int baseShift;
			switch (family) {
			case MOUNTAIN -> {
				reliefScale = 1.60D;
				valleyScale = 0.20D;
				baseShift = (int) Math.round(sphere.radius() * 0.08D);
			}
			case OCEAN -> {
				reliefScale = 2.00D;
				valleyScale = 0.25D;
				baseShift = -(int) Math.round(sphere.radius() * 0.04D);
			}
			default -> {
				reliefScale = 0.90D;
				valleyScale = 0.35D;
				baseShift = 0;
			}
		}

		int surfaceOffset = (int) Math.round(reliefSignal * (sphere.radius() * 0.45D) * reliefScale);
		int valleyOffset = (int) Math.round(Math.max(0.0D, -valleySignal) * (sphere.radius() * 0.2D) * valleyScale);
		int surfaceY = clamp(sphere.centerY() + surfaceOffset - valleyOffset + baseShift, bottomY, ceilingY);

		boolean hasLake;
		int lakeTopY;
		int lakeFloorY;
		if (family == TerrainFamily.OCEAN) {
			int waterlineY = clamp(
				sphere.centerY() - (int) Math.round(sphere.radius() * 0.03D),
				bottomY,
				ceilingY
			);
			hasLake = surfaceY <= waterlineY;
			if (hasLake) {
				lakeTopY = clamp(waterlineY, bottomY, ceilingY);
				lakeFloorY = clamp(surfaceY + 1, bottomY, lakeTopY);
				if (lakeTopY < lakeFloorY) {
					hasLake = false;
					lakeTopY = bottomY;
					lakeFloorY = bottomY;
				}
			} else {
				lakeTopY = bottomY;
				lakeFloorY = bottomY;
			}
		} else {
			double lakeThreshold = family == TerrainFamily.MOUNTAIN ? -0.10D : -0.06D;
			hasLake = lakeCapable && valleySignal < lakeThreshold;
			lakeTopY = hasLake ? clamp(surfaceY - (family == TerrainFamily.MOUNTAIN ? 4 : 3), bottomY, surfaceY) : bottomY;
			lakeFloorY = hasLake ? clamp(lakeTopY - Math.max(2, sphere.lakeRadius() / 3), bottomY, lakeTopY) : bottomY;
		}

		return new BiospheresTerrainProfile(bottomY, surfaceY, ceilingY, lakeFloorY, lakeTopY, hasLake);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
