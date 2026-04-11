package xyz.coolsa.biosphere;

import net.minecraft.world.biome.source.util.MultiNoiseUtil;

public final class BiospheresSphereMath {
    private BiospheresSphereMath() {
    }

    public static int nearestCenter(int blockCoord, int sphereDistance) {
        return Math.round(blockCoord / (float) sphereDistance) * sphereDistance;
    }

    public static boolean isInsideSphereBand(int biomeX, int biomeZ, int sphereDistance, int sphereRadius, int extraRadius) {
        int blockX = biomeX * 4;
        int blockZ = biomeZ * 4;
        int centerX = nearestCenter(blockX, sphereDistance);
        int centerZ = nearestCenter(blockZ, sphereDistance);
        double dx = centerX - blockX;
        double dz = centerZ - blockZ;
        return Math.sqrt(dx * dx + dz * dz) < sphereRadius + extraRadius;
    }

    public static int pickIndex(MultiNoiseUtil.NoiseValuePoint point, int count) {
        long mixed = point.temperatureNoise()
            ^ Long.rotateLeft(point.humidityNoise(), 11)
            ^ Long.rotateLeft(point.continentalnessNoise(), 22)
            ^ Long.rotateLeft(point.weirdnessNoise(), 33);
        return Math.floorMod(mixed, count);
    }

    public static int pickCenterY(MultiNoiseUtil.NoiseValuePoint point, int sphereRadius, int minimumY, int worldHeight) {
        double normalized = (Math.floorMod(point.depth(), 2_000_001L) / 1_000_000.0D) - 1.0D;
        double curved = Math.pow(normalized * 0.5D, 3.0D) + 0.5D;
        int minCenter = minimumY + sphereRadius * 2;
        int maxCenter = minimumY + worldHeight - sphereRadius * 2;
        return minCenter + (int) Math.round(curved * (maxCenter - minCenter));
    }

    public static boolean hasLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.continentalnessNoise(), 10L) >= 5L;
    }

    public static boolean isLavaLake(MultiNoiseUtil.NoiseValuePoint point) {
        return Math.floorMod(point.weirdnessNoise(), 10L) == 0L;
    }
}
