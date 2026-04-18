package xyz.coolsa.biosphere;

public record BiospheresStructureFit(
	int minHorizontalRadius,
	int minVerticalClearance,
	int shellMargin,
	boolean requiresSurfaceSuitability,
	boolean requiresUndergroundSuitability
) {
	public BiospheresStructureFit {
		if (minHorizontalRadius < 0) {
			throw new IllegalArgumentException("minHorizontalRadius must be non-negative");
		}
		if (minVerticalClearance < 0) {
			throw new IllegalArgumentException("minVerticalClearance must be non-negative");
		}
		if (shellMargin < 0) {
			throw new IllegalArgumentException("shellMargin must be non-negative");
		}
	}
}
