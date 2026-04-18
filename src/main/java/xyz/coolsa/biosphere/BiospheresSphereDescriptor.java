package xyz.coolsa.biosphere;

import net.minecraft.util.math.BlockPos;

public record BiospheresSphereDescriptor(
	int centerX,
	int centerY,
	int centerZ,
	int radius,
	int shellRadius,
	int bridgeRadius,
	int lakeRadius
) {
	public BlockPos centerPos() {
		return new BlockPos(this.centerX, this.centerY, this.centerZ);
	}
}
