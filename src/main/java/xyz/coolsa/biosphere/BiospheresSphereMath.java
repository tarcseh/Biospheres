package xyz.coolsa.biosphere;

import net.minecraft.world.biome.source.util.MultiNoiseUtil;

public final class BiospheresSphereMath {
    private BiospheresSphereMath() {
    }

    public static int nearestCenter(int blockCoord, int sphereDistance) {
        return Math.round(blockCoord / (float) sphereDistance) * sphereDistance;
    }

    public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int sphereRadius, int extraRadius) {
        return isInsideSphereBand(biomeX, biomeZ, sphereDistance, sphereRadius, sphereRadius, extraRadius);
    }

    public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int minRadius, int maxRadius, int extraRadius) {
        int blockX = biomeX * 4;
        int blockZ = biomeZ * 4;
        int centerX = nearestCenter(blockX, sphereDistance);
        int centerZ = nearestCenter(blockZ, sphereDistance);
        int radius = pickRadiusForSphere(centerX, centerZ, minRadius, maxRadius);
        double dx = centerX - blockX;
        double dz = centerZ - blockZ;
        return Math.sqrt(dx * dx + dz * dz) < radius + extraRadius;
    }

    public static int pickIndex(MultiNoiseUtil.NoiseValuePoint point, int count) {
        long mixed = point.temperatureNoise()
            ^ Long.rotateLeft(point.humidityNoise(), 11)
            ^ Long.rotateLeft(point.continentalnessNoise(), 22)
            ^ Long.rotateLeft(point.weirdnessNoise(), 33);
        return Math.floorMod(mixed, count);
    }

    public static int pickIndexForSphere(int centerX, int centerZ, int count) {
        long mixed = mixSphereSeed(centerX, centerZ);
        return Math.floorMod(mixed, count);
    }

    public static int pickRadiusForSphere(int centerX, int centerZ, int minRadius, int maxRadius) {
        if (maxRadius < minRadius) {
            throw new IllegalArgumentException("maxRadius must be >= minRadius");
        }

        long mixed = mixSphereSeed(centerX, centerZ);
        int radiusSpan = maxRadius - minRadius + 1;
        return minRadius + Math.floorMod(mixed, radiusSpan);
    }

    public static int bridgeGap(int firstCenterCoord, int secondCenterCoord, int firstRadius, int secondRadius) {
        return Math.max(1, Math.abs(secondCenterCoord - firstCenterCoord) - firstRadius - secondRadius);
    }

    public static int bridgeProgress(int currentCoord, int centerCoord, int centerRadius, int neighborCoord) {
        int direction = Integer.compare(neighborCoord, centerCoord);
        int shellBoundary = centerCoord + direction * centerRadius;
        return Math.max(0, direction * (currentCoord - shellBoundary));
    }

    public static int bridgeStartCoord(int centerCoord, int centerRadius, int neighborCoord) {
        return centerCoord + Integer.compare(neighborCoord, centerCoord) * centerRadius;
    }

    public static int bridgeEndCoord(int neighborCoord, int neighborRadius, int centerCoord) {
        return neighborCoord + Integer.compare(centerCoord, neighborCoord) * neighborRadius;
    }

    public static int bridgeSupportDepth(int startY, int endY, int startCoord, int endCoord) {
        int span = Math.max(1, Math.abs(endCoord - startCoord));
        int heightDelta = Math.abs(endY - startY);
        if (heightDelta * 2 < span) {
            return 1;
        }

        return Math.max(2, (int) Math.ceil(heightDelta / (double) span));
    }

    public static int interpolateBridgeY(int startY, int endY, int startCoord, int endCoord, int currentCoord) {
        if (startCoord == endCoord) {
            return startY;
        }

        double progress = (currentCoord - startCoord) / (double) (endCoord - startCoord);
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return (int) Math.round(startY + (endY - startY) * progress);
    }

    public static int pickCenterY(MultiNoiseUtil.NoiseValuePoint point, int sphereRadius, int minimumY, int worldHeight) {
        double normalized = (Math.floorMod(point.depth(), 2_000_001L) / 1_000_000.0D) - 1.0D;
        double curved = Math.pow(normalized * 0.5D, 3.0D) + 0.5D;
        return pickCenterYFromCurve(curved, sphereRadius, minimumY, worldHeight);
    }

    public static int pickCenterYForSphere(int centerX, int centerZ, int sphereRadius, int minimumY, int worldHeight) {
        long mixed = mixSphereSeed(centerX, centerZ);
        double normalized = ((mixed >>> 11) * 0x1.0p-53) - 0.5D;
        double curved = Math.pow(normalized, 3.0D) + 0.5D;
        return pickCenterYFromCurve(curved, sphereRadius, minimumY, worldHeight);
    }

	private static int pickCenterYFromCurve(double curved, int sphereRadius, int minimumY, int worldHeight) {
		int worldBottom = minimumY;
		int worldTop = minimumY + worldHeight - 1;
		int minCenter = worldBottom + sphereRadius;
		int maxCenter = worldTop - sphereRadius;
		if (maxCenter < minCenter) {
			return (worldBottom + worldTop) / 2;
		}

		double clamped = Math.max(0.0D, Math.min(1.0D, curved));
		return minCenter + (int) Math.round(clamped * (maxCenter - minCenter));
	}

    private static long mixSphereSeed(int centerX, int centerZ) {
        long mixed = 0x9E3779B97F4A7C15L;
        mixed ^= (long) centerX * 341873128712L;
        mixed ^= (long) centerZ * 132897987541L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    public static boolean hasLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.continentalnessNoise(), 10L) >= 5L;
    }

    public static boolean isLavaLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.weirdnessNoise(), 10L) == 0L;
    }
}
