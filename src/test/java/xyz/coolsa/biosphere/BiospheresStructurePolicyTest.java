package xyz.coolsa.biosphere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresStructurePolicyTest {
	@Test
	void reportsMeasuredHorizontalBoundsForKnownFamilies() {
		assertEquals(144, BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.VILLAGE));
		assertEquals(120, BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.ANCIENT_CITY));
		assertEquals(56, BiospheresStructureBoundsCatalog.requiredHorizontalRadius(BiospheresStructureFamily.OCEAN_RUIN));
	}

	@Test
	void reportsLargestRequiredHorizontalRadiusAcrossAllFamilies() {
		assertEquals(144, BiospheresStructureBoundsCatalog.largestRequiredHorizontalRadius());
	}

	@Test
	void villageRequiresSurfaceSuitabilityAndLargeRadius() {
		BiospheresStructureFit fit = BiospheresStructurePolicy.defaultPolicy().fitFor(BiospheresStructureFamily.VILLAGE);

		assertTrue(fit.requiresSurfaceSuitability());
		assertFalse(fit.requiresUndergroundSuitability());
		assertTrue(fit.minHorizontalRadius() > 100);
	}

	@Test
	void ancientCityRequiresUndergroundSuitability() {
		BiospheresStructureFit fit = BiospheresStructurePolicy.defaultPolicy().fitFor(BiospheresStructureFamily.ANCIENT_CITY);

		assertTrue(fit.requiresUndergroundSuitability());
		assertFalse(fit.requiresSurfaceSuitability());
		assertTrue(fit.minVerticalClearance() > 40);
	}

	@Test
	void eligibilityRequiresSurfaceSuitabilityWhenTheFamilyNeedsIt() {
		BiospheresStructurePolicy policy = BiospheresStructurePolicy.defaultPolicy();
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 160, 161, 158, 16);

		assertFalse(policy.isEligible(BiospheresStructureFamily.VILLAGE, sphere, false, true));
		assertTrue(policy.isEligible(BiospheresStructureFamily.VILLAGE, sphere, true, true));
	}

	@Test
	void eligibilityRequiresUndergroundSuitabilityWhenTheFamilyNeedsIt() {
		BiospheresStructurePolicy policy = BiospheresStructurePolicy.defaultPolicy();
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 160, 161, 158, 16);

		assertFalse(policy.isEligible(BiospheresStructureFamily.ANCIENT_CITY, sphere, true, false));
		assertTrue(policy.isEligible(BiospheresStructureFamily.ANCIENT_CITY, sphere, true, true));
	}

	@Test
	void eligibilityRejectsSpheresThatAreTooSmall() {
		BiospheresStructurePolicy policy = BiospheresStructurePolicy.defaultPolicy();
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 150, 151, 148, 16);

		assertFalse(policy.isEligible(BiospheresStructureFamily.VILLAGE, sphere, true, true));
	}

	@Test
	void defaultPolicyDefinesEveryApprovedFamily() {
		BiospheresStructurePolicy policy = BiospheresStructurePolicy.defaultPolicy();

		for (BiospheresStructureFamily family : BiospheresStructureFamily.values()) {
			assertTrue(policy.fitFor(family).minHorizontalRadius() > 0);
		}
	}
}
