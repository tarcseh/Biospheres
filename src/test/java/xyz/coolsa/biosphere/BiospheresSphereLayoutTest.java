package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresSphereLayoutTest {
    @Test
    void resolvesStableDescriptorForGridCell() {
        BiospheresSphereLayout layout = new BiospheresSphereLayout(128, 20, 160, 16, 6, -64, 384);

        BiospheresSphereDescriptor first = layout.resolve(128, 256);
        BiospheresSphereDescriptor second = layout.resolve(128, 256);

        assertEquals(first, second);
        assertTrue(first.radius() >= 20);
        assertTrue(first.radius() <= 160);
    }

    @Test
    void resolvesCardinalNeighborsOnTheFixedGrid() {
        BiospheresSphereLayout layout = new BiospheresSphereLayout(128, 20, 160, 16, 6, -64, 384);

        BiospheresSphereDescriptor center = layout.resolve(0, 0);
        BiospheresSphereDescriptor east = layout.resolveNeighbor(center, 1, 0);

        assertEquals(center.centerX() + 128, east.centerX());
        assertEquals(center.centerZ(), east.centerZ());
    }
}
