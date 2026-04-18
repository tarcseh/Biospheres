package xyz.coolsa.biosphere;

public final class BiospheresSphereLayout {
    private final int sphereDistance;
    private final int minRadius;
    private final int maxRadius;
    private final int lakeRadius;
    private final int shoreRadius;
    private final int minimumY;
    private final int worldHeight;

    public BiospheresSphereLayout(int sphereDistance, int minRadius, int maxRadius, int lakeRadius, int shoreRadius, int minimumY, int worldHeight) {
        this.sphereDistance = sphereDistance;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.lakeRadius = lakeRadius;
        this.shoreRadius = shoreRadius;
        this.minimumY = minimumY;
        this.worldHeight = worldHeight;
    }

    public BiospheresSphereDescriptor resolve(int blockX, int blockZ) {
        int centerX = BiospheresSphereMath.nearestCenter(blockX, this.sphereDistance);
        int centerZ = BiospheresSphereMath.nearestCenter(blockZ, this.sphereDistance);
        int sampledRadius = BiospheresSphereMath.pickRadiusForSphere(centerX, centerZ, this.minRadius, this.maxRadius);
        int maxRenderableRadius = Math.max(1, (this.worldHeight - 1) / 2);
        int radius = Math.min(sampledRadius, maxRenderableRadius);
        int centerY = BiospheresSphereMath.pickCenterYForSphere(centerX, centerZ, radius, this.minimumY, this.worldHeight);
        int shellRadius = radius + 1;
        int bridgeRadius = Math.max(1, radius - this.shoreRadius);
        int lakeRadius = Math.min(this.lakeRadius, Math.max(4, radius / 2));
        return new BiospheresSphereDescriptor(centerX, centerY, centerZ, radius, shellRadius, bridgeRadius, lakeRadius);
    }

    public BiospheresSphereDescriptor resolveNeighbor(BiospheresSphereDescriptor sphere, int xOffset, int zOffset) {
        return this.resolve(
            sphere.centerX() + xOffset * this.sphereDistance,
            sphere.centerZ() + zOffset * this.sphereDistance
        );
    }
}
