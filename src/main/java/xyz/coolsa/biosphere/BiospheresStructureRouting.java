package xyz.coolsa.biosphere;

import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.Optional;

public final class BiospheresStructureRouting {
	private static final Map<Identifier, BiospheresStructureFamily> STRUCTURE_FAMILIES = Map.ofEntries(
		Map.entry(Identifier.of("minecraft", "village_plains"), BiospheresStructureFamily.VILLAGE),
		Map.entry(Identifier.of("minecraft", "village_desert"), BiospheresStructureFamily.VILLAGE),
		Map.entry(Identifier.of("minecraft", "village_savanna"), BiospheresStructureFamily.VILLAGE),
		Map.entry(Identifier.of("minecraft", "village_snowy"), BiospheresStructureFamily.VILLAGE),
		Map.entry(Identifier.of("minecraft", "village_taiga"), BiospheresStructureFamily.VILLAGE),
		Map.entry(Identifier.of("minecraft", "pillager_outpost"), BiospheresStructureFamily.PILLAGER_OUTPOST),
		Map.entry(Identifier.of("minecraft", "woodland_mansion"), BiospheresStructureFamily.WOODLAND_MANSION),
		Map.entry(Identifier.of("minecraft", "ancient_city"), BiospheresStructureFamily.ANCIENT_CITY),
		Map.entry(Identifier.of("minecraft", "stronghold"), BiospheresStructureFamily.STRONGHOLD),
		Map.entry(Identifier.of("minecraft", "trial_chambers"), BiospheresStructureFamily.TRIAL_CHAMBERS),
		Map.entry(Identifier.of("minecraft", "ocean_monument"), BiospheresStructureFamily.OCEAN_MONUMENT),
		Map.entry(Identifier.of("minecraft", "mineshaft"), BiospheresStructureFamily.MINESHAFT),
		Map.entry(Identifier.of("minecraft", "ruined_portal"), BiospheresStructureFamily.RUINED_PORTAL),
		Map.entry(Identifier.of("minecraft", "desert_pyramid"), BiospheresStructureFamily.DESERT_PYRAMID),
		Map.entry(Identifier.of("minecraft", "jungle_pyramid"), BiospheresStructureFamily.JUNGLE_TEMPLE),
		Map.entry(Identifier.of("minecraft", "igloo"), BiospheresStructureFamily.IGLOO),
		Map.entry(Identifier.of("minecraft", "swamp_hut"), BiospheresStructureFamily.SWAMP_HUT)
	);

	private final BiospheresStructurePolicy policy;

	private BiospheresStructureRouting(BiospheresStructurePolicy policy) {
		this.policy = policy;
	}

	public static BiospheresStructureRouting defaultRouting() {
		return new BiospheresStructureRouting(BiospheresStructurePolicy.defaultPolicy());
	}

	public Optional<BiospheresStructureFamily> familyFor(Identifier structureId) {
		if (structureId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(STRUCTURE_FAMILIES.get(structureId));
	}

	public boolean canSkipUnknownStructure(Identifier structureId) {
		return this.familyFor(structureId).isEmpty();
	}

	public Optional<BiospheresStructureFamily> familyFor(StructureStart start, DynamicRegistryManager registryManager) {
		if (start == null) {
			return Optional.empty();
		}
		if (registryManager == null) {
			return Optional.empty();
		}

		return registryManager.getOptional(RegistryKeys.STRUCTURE)
			.flatMap(registry -> this.familyFor(registry.getId(start.getStructure())))
			;
	}

	public boolean canStart(BiospheresStructureFamily family, BiospheresSphereDescriptor sphere) {
		if (family == null || sphere == null) {
			return false;
		}

		BiospheresStructureFit fit = this.policy.fitFor(family);
		boolean surfaceSuitable = isSurfaceSuitable(sphere);
		boolean undergroundSuitable = isUndergroundSuitable(sphere);
		return this.policy.isEligible(family, sphere, surfaceSuitable, undergroundSuitable)
			&& sphere.radius() >= fit.minHorizontalRadius() + fit.shellMargin();
	}

	public boolean canPlaceProjectedBounds(BiospheresStructureFamily family, BiospheresSphereDescriptor sphere, BlockBox projectedBox) {
		if (family == null || sphere == null || projectedBox == null) {
			return false;
		}

		BiospheresStructureFit fit = this.policy.fitFor(family);
		if (!this.canStart(family, sphere)) {
			return false;
		}

		return BiospheresStructureConfinement.isWithinSphere(projectedBox, sphere, fit.shellMargin());
	}

	public boolean canAccept(Identifier structureId, BlockBox projectedBox, BiospheresSphereDescriptor sphere) {
		Optional<BiospheresStructureFamily> family = this.familyFor(structureId);
		if (family.isEmpty()) {
			return true;
		}
		if (family.get() == BiospheresStructureFamily.VILLAGE) {
			return sphere.radius() >= 144 && this.canPlaceProjectedBounds(family.get(), sphere, projectedBox);
		}
		if (family.get() == BiospheresStructureFamily.WOODLAND_MANSION) {
			return sphere.radius() >= 128 && this.canPlaceProjectedBounds(family.get(), sphere, projectedBox);
		}
		if (family.get() == BiospheresStructureFamily.MINESHAFT) {
			BiospheresStructureFit fit = this.policy.fitFor(family.get());
			return BiospheresStructureConfinement.isHorizontallyWithinSphere(projectedBox, sphere, fit.shellMargin(), 32);
		}
		if (family.get() == BiospheresStructureFamily.STRONGHOLD) {
			BiospheresStructureFit fit = this.policy.fitFor(family.get());
			return BiospheresStructureConfinement.isHorizontallyWithinSphere(projectedBox, sphere, fit.shellMargin(), 32);
		}
		return this.canPlaceProjectedBounds(family.get(), sphere, projectedBox);
	}

	public boolean canAccept(StructureStart start, BiospheresSphereDescriptor sphere, DynamicRegistryManager registryManager) {
		Optional<BiospheresStructureFamily> family = this.familyFor(start, registryManager);
		if (family.isEmpty()) {
			return true;
		}
		return this.canPlaceProjectedBounds(family.get(), sphere, start.getBoundingBox());
	}

	private static boolean isSurfaceSuitable(BiospheresSphereDescriptor sphere) {
		return sphere.radius() >= 96;
	}

	private static boolean isUndergroundSuitable(BiospheresSphereDescriptor sphere) {
		return (sphere.centerY() + sphere.radius()) - (sphere.centerY() - sphere.radius()) >= 96;
	}
}
