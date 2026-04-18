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
    void picksStableBiomeIndexFromSphereCoordinates() {
        int first = BiospheresSphereMath.pickIndexForSphere(128, 256, 29);
        int second = BiospheresSphereMath.pickIndexForSphere(128, 256, 29);

        assertEquals(first, second);
        assertTrue(first >= 0 && first < 29);
    }

    @Test
    void picksDifferentBiomeIndicesForDifferentSphereCenters() {
        int first = BiospheresSphereMath.pickIndexForSphere(0, 0, 29);
        int second = BiospheresSphereMath.pickIndexForSphere(128, 0, 29);
        int third = BiospheresSphereMath.pickIndexForSphere(0, 128, 29);

        assertTrue(first != second || second != third || first != third);
    }

    @Test
    void keepsSeededSphereCenterYInsideExpectedBounds() {
        int y = BiospheresSphereMath.pickCenterYForSphere(128, 256, 32, -64, 384);

        assertTrue(y >= 64);
        assertTrue(y <= 192);
    }

    @Test
    void picksStableRadiusForSphereCoordinates() {
        int first = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 160);
        int second = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 160);

        assertEquals(first, second);
        assertTrue(first >= 20);
        assertTrue(first <= 160);
    }

    @Test
    void supportsRaisedMaximumRadiusWhenPoliciesNeedIt() {
        int radius = BiospheresSphereMath.pickRadiusForSphere(128, 256, 20, 224);

        assertTrue(radius >= 20);
        assertTrue(radius <= 224);
    }

    @Test
    void preservesTheCurrentLakeThresholds() {
        assertTrue(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 5L, 0L, 0L, 0L)));
        assertFalse(BiospheresSphereMath.hasLake(new MultiNoiseUtil.NoiseValuePoint(0L, 0L, 4L, 0L, 0L, 0L)));
    }

    @Test
    void computesPositiveBridgeGapBetweenDifferentSphereSizes() {
        assertEquals(64, BiospheresSphereMath.bridgeGap(0, 384, 160, 160));
        assertEquals(166, BiospheresSphereMath.bridgeGap(0, 384, 80, 138));
    }

    @Test
    void computesBridgeProgressFromTheShellBoundary() {
        assertEquals(0, BiospheresSphereMath.bridgeProgress(320, 160, 160, 384));
        assertEquals(32, BiospheresSphereMath.bridgeProgress(352, 160, 160, 384));
        assertEquals(64, BiospheresSphereMath.bridgeProgress(384, 160, 160, 384));
    }

    @Test
    void computesBridgeShellAnchorsFromBothSpheres() {
        assertEquals(320, BiospheresSphereMath.bridgeStartCoord(160, 160, 384));
        assertEquals(224, BiospheresSphereMath.bridgeEndCoord(384, 160, 160));
        assertEquals(304, BiospheresSphereMath.bridgeEndCoord(384, 80, 160));
    }

    @Test
    void interpolatesBridgeHeightAcrossTheWholeSpan() {
        assertEquals(100, BiospheresSphereMath.interpolateBridgeY(100, 132, 320, 384, 320));
        assertEquals(116, BiospheresSphereMath.interpolateBridgeY(100, 132, 320, 384, 352));
        assertEquals(132, BiospheresSphereMath.interpolateBridgeY(100, 132, 320, 384, 384));
    }
}
