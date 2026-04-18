package xyz.coolsa.biosphere;

import java.util.Map;

public final class BiospheresStructureBoundsCatalog {
	private static final Map<BiospheresStructureFamily, Integer> HORIZONTAL_RADII = Map.ofEntries(
		Map.entry(BiospheresStructureFamily.VILLAGE, 144),
		Map.entry(BiospheresStructureFamily.PILLAGER_OUTPOST, 96),
		Map.entry(BiospheresStructureFamily.WOODLAND_MANSION, 96),
		Map.entry(BiospheresStructureFamily.ANCIENT_CITY, 120),
		Map.entry(BiospheresStructureFamily.STRONGHOLD, 96),
		Map.entry(BiospheresStructureFamily.TRIAL_CHAMBERS, 112),
		Map.entry(BiospheresStructureFamily.OCEAN_MONUMENT, 96),
		Map.entry(BiospheresStructureFamily.MINESHAFT, 84),
		Map.entry(BiospheresStructureFamily.RUINED_PORTAL, 64),
		Map.entry(BiospheresStructureFamily.DESERT_PYRAMID, 72),
		Map.entry(BiospheresStructureFamily.JUNGLE_TEMPLE, 72),
		Map.entry(BiospheresStructureFamily.IGLOO, 64),
		Map.entry(BiospheresStructureFamily.SWAMP_HUT, 64)
	);

	private BiospheresStructureBoundsCatalog() {
	}

	public static int requiredHorizontalRadius(BiospheresStructureFamily family) {
		Integer radius = HORIZONTAL_RADII.get(family);
		if (radius == null) {
			throw new IllegalArgumentException("Unsupported structure family: " + family);
		}
		return radius;
	}

	public static int largestRequiredHorizontalRadius() {
		return HORIZONTAL_RADII.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
	}
}
