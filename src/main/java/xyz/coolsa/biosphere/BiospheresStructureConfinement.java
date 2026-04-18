package xyz.coolsa.biosphere;

import net.minecraft.util.math.BlockBox;

public final class BiospheresStructureConfinement {
	private BiospheresStructureConfinement() {
	}

	public static boolean isWithinSphere(BlockBox box, BiospheresSphereDescriptor sphere, int shellMargin) {
		if (box == null || sphere == null || shellMargin < 0) {
			return false;
		}

		int allowedRadius = sphere.radius() - shellMargin;
		if (allowedRadius < 0) {
			return false;
		}

		return containsCorner(box.getMinX(), box.getMinY(), box.getMinZ(), sphere, allowedRadius)
			&& containsCorner(box.getMinX(), box.getMinY(), box.getMaxZ(), sphere, allowedRadius)
			&& containsCorner(box.getMinX(), box.getMaxY(), box.getMinZ(), sphere, allowedRadius)
			&& containsCorner(box.getMinX(), box.getMaxY(), box.getMaxZ(), sphere, allowedRadius)
			&& containsCorner(box.getMaxX(), box.getMinY(), box.getMinZ(), sphere, allowedRadius)
			&& containsCorner(box.getMaxX(), box.getMinY(), box.getMaxZ(), sphere, allowedRadius)
			&& containsCorner(box.getMaxX(), box.getMaxY(), box.getMinZ(), sphere, allowedRadius)
			&& containsCorner(box.getMaxX(), box.getMaxY(), box.getMaxZ(), sphere, allowedRadius);
	}

	public static boolean isHorizontallyWithinSphere(BlockBox box, BiospheresSphereDescriptor sphere, int shellMargin, int verticalTolerance) {
		if (box == null || sphere == null || shellMargin < 0 || verticalTolerance < 0) {
			return false;
		}

		int allowedRadius = sphere.radius() - shellMargin;
		if (allowedRadius < 0) {
			return false;
		}

		long radiusSquared = (long) allowedRadius * allowedRadius;
		if (!containsCornerXZ(box.getMinX(), box.getMinZ(), sphere, radiusSquared)
			|| !containsCornerXZ(box.getMinX(), box.getMaxZ(), sphere, radiusSquared)
			|| !containsCornerXZ(box.getMaxX(), box.getMinZ(), sphere, radiusSquared)
			|| !containsCornerXZ(box.getMaxX(), box.getMaxZ(), sphere, radiusSquared)) {
			return false;
		}

		int sphereFloor = sphere.centerY() - sphere.radius();
		int sphereCeiling = sphere.centerY() + sphere.radius();
		return box.getMinY() >= sphereFloor - verticalTolerance
			&& box.getMaxY() <= sphereCeiling + verticalTolerance;
	}

	private static boolean containsCorner(int x, int y, int z, BiospheresSphereDescriptor sphere, int allowedRadius) {
		long dx = (long) x - sphere.centerX();
		long dy = (long) y - sphere.centerY();
		long dz = (long) z - sphere.centerZ();
		long radiusSquared = (long) allowedRadius * allowedRadius;
		long distanceSquared = dx * dx + dy * dy + dz * dz;
		return distanceSquared <= radiusSquared;
	}

	private static boolean containsCornerXZ(int x, int z, BiospheresSphereDescriptor sphere, long radiusSquared) {
		long dx = (long) x - sphere.centerX();
		long dz = (long) z - sphere.centerZ();
		long distanceSquared = dx * dx + dz * dz;
		return distanceSquared <= radiusSquared;
	}
}
