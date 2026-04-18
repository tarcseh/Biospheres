package xyz.coolsa.biosphere;

import java.util.Map;

public final class BiospheresStructurePolicy {
	private final Map<BiospheresStructureFamily, BiospheresStructureFit> fits;

	private BiospheresStructurePolicy(Map<BiospheresStructureFamily, BiospheresStructureFit> fits) {
		this.fits = Map.copyOf(fits);
	}

	public static BiospheresStructurePolicy defaultPolicy() {
		return new BiospheresStructurePolicy(Map.ofEntries(
			Map.entry(BiospheresStructureFamily.VILLAGE, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.VILLAGE),
				48,
				12,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.PILLAGER_OUTPOST, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.PILLAGER_OUTPOST),
				40,
				12,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.WOODLAND_MANSION, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.WOODLAND_MANSION),
				48,
				16,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.ANCIENT_CITY, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.ANCIENT_CITY),
				80,
				16,
				false,
				true
			)),
			Map.entry(BiospheresStructureFamily.STRONGHOLD, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.STRONGHOLD),
				64,
				16,
				false,
				true
			)),
			Map.entry(BiospheresStructureFamily.TRIAL_CHAMBERS, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.TRIAL_CHAMBERS),
				72,
				16,
				false,
				true
			)),
			Map.entry(BiospheresStructureFamily.OCEAN_MONUMENT, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.OCEAN_MONUMENT),
				56,
				14,
				false,
				false
			)),
			Map.entry(BiospheresStructureFamily.OCEAN_RUIN, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.OCEAN_RUIN),
				36,
				10,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.MINESHAFT, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.MINESHAFT),
				32,
				10,
				false,
				true
			)),
			Map.entry(BiospheresStructureFamily.RUINED_PORTAL, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.RUINED_PORTAL),
				32,
				10,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.TRAIL_RUINS, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.TRAIL_RUINS),
				48,
				12,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.DESERT_PYRAMID, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.DESERT_PYRAMID),
				40,
				12,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.JUNGLE_TEMPLE, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.JUNGLE_TEMPLE),
				40,
				12,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.IGLOO, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.IGLOO),
				28,
				8,
				true,
				false
			)),
			Map.entry(BiospheresStructureFamily.SWAMP_HUT, new BiospheresStructureFit(
				BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.SWAMP_HUT),
				28,
				8,
				true,
				false
			))
		));
	}

	public BiospheresStructureFit fitFor(BiospheresStructureFamily family) {
		BiospheresStructureFit fit = this.fits.get(family);
		if (fit == null) {
			throw new IllegalArgumentException("Unsupported structure family: " + family);
		}
		return fit;
	}

	public boolean isEligible(BiospheresStructureFamily family, BiospheresSphereDescriptor sphere, boolean surfaceSuitable, boolean undergroundSuitable) {
		BiospheresStructureFit fit = this.fitFor(family);
		if (sphere.radius() < fit.minHorizontalRadius() + fit.shellMargin()) {
			return false;
		}
		if (fit.requiresSurfaceSuitability() && !surfaceSuitable) {
			return false;
		}
		if (fit.requiresUndergroundSuitability() && !undergroundSuitable) {
			return false;
		}
		return true;
	}
}
