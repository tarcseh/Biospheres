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

    public static int pickCenterY(MultiNoiseUtil.NoiseValuePoint point, int sphereRadius, int minimumY, int worldHeight) {
        double normalized = (Math.floorMod(point.depth(), 2_000_001L) / 1_000_000.0D) - 1.0D;
        double curved = Math.pow(normalized * 0.5D, 3.0D) + 0.5D;
        int minCenter = minimumY + sphereRadius * 4;
        int maxCenter = minimumY + worldHeight - sphereRadius * 4;
        return minCenter + (int) Math.round(curved * (maxCenter - minCenter));
    }

    public static int pickCenterYForSphere(int centerX, int centerZ, int sphereRadius, int minimumY, int worldHeight) {
        long mixed = mixSphereSeed(centerX, centerZ);
        double normalized = ((mixed >>> 11) * 0x1.0p-53) - 0.5D;
        double curved = Math.pow(normalized, 3.0D) + 0.5D;
        int minCenter = minimumY + sphereRadius * 4;
        int maxCenter = minimumY + worldHeight - sphereRadius * 4;
        return minCenter + (int) Math.round(curved * (maxCenter - minCenter));
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
