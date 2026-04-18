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

	private static boolean containsCorner(int x, int y, int z, BiospheresSphereDescriptor sphere, int allowedRadius) {
		long dx = (long) x - sphere.centerX();
		long dy = (long) y - sphere.centerY();
		long dz = (long) z - sphere.centerZ();
		long radiusSquared = (long) allowedRadius * allowedRadius;
		long distanceSquared = dx * dx + dy * dy + dz * dz;
		return distanceSquared <= radiusSquared;
	}
}
