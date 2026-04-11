package xyz.coolsa.biosphere;

import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresSphereMathTest {
    @Test
    void snapsBlockCoordinatesToNearestSphereCenter() {
        assertEquals(128, BiospheresSphereMath.nearestCenter(120, 128));
        assertEquals(-128, BiospheresSphereMath.nearestCenter(-120, 128));
        assertEquals(0, BiospheresSphereMath.nearestCenter(12, 128));
    }

    @Test
    void detectsWhetherBiomeCoordinatesAreInsideTheSphereBand() {
        assertTrue(BiospheresSphereMath.isInsideSphereBand(0, 0, 128, 32, 6));
        assertFalse(BiospheresSphereMath.isInsideSphereBand(20, 20, 128, 32, 6));
    }

    @Test
    void picksAStableBiomeIndexFromASeededNoisePoint() {
        MultiNoiseUtil.NoiseValuePoint point = new MultiNoiseUtil.NoiseValuePoint(11L, 22L, 33L, 44L, 55L, 66L);

        int first = BiospheresSphereMath.pickIndex(point, 7);
        int second = BiospheresSphereMath.pickIndex(point, 7);

        assertEquals(first, second);
        assertTrue(first >= 0 && first < 7);
    }

    @Test
    void keepsSphereCentersInsideTheModernWorldHeight() {
        MultiNoiseUtil.NoiseValuePoint point = new MultiNoiseUtil.NoiseValuePoint(11L, 22L, 33L, 44L, 500_000L, 66L);

        int y = BiospheresSphereMath.pickCenterY(point, 32, -64, 384);

        assertTrue(y >= 0);
        assertTrue(y <= 256);
    }

    @Test
    void preservesTheCurrentLakeThresholds() {
        assertTrue(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 5L, 0L, 0L, 0L)));
        assertFalse(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 4L, 0L, 0L, 0L)));
    }
}
