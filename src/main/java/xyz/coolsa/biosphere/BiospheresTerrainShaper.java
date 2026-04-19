package xyz.coolsa.biosphere;

public final class BiospheresTerrainShaper {
	public enum TerrainFamily {
		DEFAULT,
		OCEAN,
		RIVER,
		FROZEN_RIVER,
		MOUNTAIN
	}

	public BiospheresTerrainProfile shapeColumn(
		BiospheresSphereDescriptor sphere,
		double reliefSignal,
		double valleySignal,
		double riverSignal,
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
				reliefScale = 1.40D;
				valleyScale = 0.20D;
				baseShift = -(int) Math.round(sphere.radius() * 0.10D);
			}
			case RIVER, FROZEN_RIVER -> {
				reliefScale = 0.95D;
				valleyScale = 0.22D;
				baseShift = 0;
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
				sphere.centerY() - (int) Math.round(sphere.radius() * 0.02D),
				bottomY,
				ceilingY
			);
			double islandSignal = Math.abs(riverSignal);
			if (islandSignal > 0.18D) {
				int islandBoost = 2 + (int) Math.round((islandSignal - 0.18D) * sphere.radius() * 0.95D);
				surfaceY = clamp(surfaceY + islandBoost, bottomY, ceilingY);
			}
			hasLake = surfaceY <= waterlineY;
			if (hasLake) {
				lakeTopY = clamp(waterlineY, bottomY, ceilingY);
				lakeFloorY = clamp(surfaceY + 1, bottomY, lakeTopY);
				surfaceY = Math.max(surfaceY, lakeFloorY);
				if (lakeTopY < lakeFloorY) {
					hasLake = false;
					lakeTopY = bottomY;
					lakeFloorY = bottomY;
				}
			} else {
				lakeTopY = bottomY;
				lakeFloorY = bottomY;
			}
		} else if (family == TerrainFamily.RIVER || family == TerrainFamily.FROZEN_RIVER) {
			int waterlineY = clamp(
				sphere.centerY() - (int) Math.round(sphere.radius() * 0.02D),
				bottomY,
				ceilingY
			);
			double channelDistance = Math.abs(riverSignal);
			double channelCore = family == TerrainFamily.FROZEN_RIVER ? 0.09D : 0.10D;
			double channelBank = family == TerrainFamily.FROZEN_RIVER ? 0.34D : 0.38D;
			boolean hasChannel = channelDistance < channelCore;
			if (hasChannel) {
				surfaceY = clamp(Math.min(surfaceY, waterlineY - 1), bottomY, ceilingY);
			} else if (channelDistance < channelBank) {
				double bankProgress = (channelDistance - channelCore) / Math.max(0.0001D, channelBank - channelCore);
				int bankY = waterlineY + (bankProgress < 0.55D ? 1 : 2);
				surfaceY = clamp(bankY, bottomY, ceilingY);
			} else {
				int hillBoost = (int) Math.round((channelDistance - channelBank) * sphere.radius() * 0.16D);
				surfaceY = clamp(surfaceY + Math.max(0, hillBoost), bottomY, ceilingY);
			}

			hasLake = hasChannel;
			if (hasLake) {
				lakeTopY = clamp(waterlineY, bottomY, ceilingY);
				lakeFloorY = clamp(surfaceY + 1, bottomY, lakeTopY);
				surfaceY = Math.max(surfaceY, lakeFloorY);
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
			if (hasLake) {
				surfaceY = Math.max(surfaceY, lakeFloorY);
			}
		}

		return new BiospheresTerrainProfile(bottomY, surfaceY, ceilingY, lakeFloorY, lakeTopY, hasLake);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
