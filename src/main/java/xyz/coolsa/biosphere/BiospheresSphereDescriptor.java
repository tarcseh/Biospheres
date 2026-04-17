package xyz.coolsa.biosphere;

public record BiospheresSphereDescriptor(
	int centerX,
	int centerY,
	int centerZ,
	int radius,
	int shellRadius,
	int bridgeRadius,
	int lakeRadius
) {
}
