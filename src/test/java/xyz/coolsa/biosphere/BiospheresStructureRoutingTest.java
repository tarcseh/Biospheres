package xyz.coolsa.biosphere;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiospheresStructureRoutingTest {

	@Test
	void rejectsVillageWhenSphereIsBelowThePolicyThreshold() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 120, 0, 72, 73, 70, 16);

		assertFalse(BiospheresStructureRouting.defaultRouting().canStart(BiospheresStructureFamily.VILLAGE, sphere));
	}

	@Test
	void rejectsProjectedBoundsThatLeakPastTheShellMargin() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 112, 113, 110, 16);
		BlockBox projectedBox = new BlockBox(-120, 70, -20, 20, 130, 20);

		assertFalse(BiospheresStructureRouting.defaultRouting().canPlaceProjectedBounds(BiospheresStructureFamily.STRONGHOLD, sphere, projectedBox));
	}

	@Test
	void rejectsKnownStructureIdsWhoseProjectedBoundsLeakPastTheShellMargin() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 112, 113, 110, 16);
		BlockBox projectedBox = new BlockBox(-120, 70, -20, 20, 130, 20);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "trial_chambers"), projectedBox, sphere));
	}

	@Test
	void allowsUnknownStructureIdsToPassThroughProjectedBoundsChecks() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 72, 73, 70, 16);
		BlockBox projectedBox = new BlockBox(-200, -64, -200, 200, 256, 200);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("biospheres", "unknown_structure"), projectedBox, sphere));
	}

	@Test
	void rejectsUnknownStructureClassifications() {
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("biospheres", "unknown_structure")).isEmpty());
	}

	@Test
	void classifiesTrailRuinsAsKnownStructureFamily() {
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("minecraft", "trail_ruins")).isPresent());
	}

	@Test
	void classifiesOceanRuinsAsKnownStructureFamily() {
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("minecraft", "ocean_ruin_cold")).isPresent());
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("minecraft", "ocean_ruin_warm")).isPresent());
	}

	@Test
	void classifiesRuinedPortalVariantsAsKnownStructureFamily() {
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("minecraft", "ruined_portal_mountain")).isPresent());
		assertTrue(BiospheresStructureRouting.defaultRouting().familyFor(Identifier.of("minecraft", "ruined_portal_ocean")).isPresent());
	}

	@Test
	void allowsUnknownStructureClassificationsToFallBackWithoutBlockingShellPasses() {
		assertTrue(BiospheresStructureRouting.defaultRouting().canSkipUnknownStructure(Identifier.of("biospheres", "unknown_structure")));
	}

	@Test
	void allowsStrongholdsToPreserveVanillaLocateBehavior() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 128, 129, 126, 16);
		BlockBox projectedBox = new BlockBox(-40, 16, -40, 40, 184, 40);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "stronghold"), projectedBox, sphere));
	}

	@Test
	void rejectsStrongholdsWhoseProjectedBoundsDropFarBelowTheSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 128, 129, 126, 16);
		BlockBox projectedBox = new BlockBox(-40, -96, -40, 40, 40, 40);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "stronghold"), projectedBox, sphere));
	}

	@Test
	void allowsMineshaftsToPreserveVanillaLocateBehavior() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
		BlockBox projectedBox = new BlockBox(-40, 16, -40, 40, 184, 40);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "mineshaft"), projectedBox, sphere));
	}

	@Test
	void rejectsMineshaftsThatExtendBelowTheSphereFloor() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
		BlockBox projectedBox = new BlockBox(-40, -80, -40, 40, 40, 40);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "mineshaft"), projectedBox, sphere));
	}

	@Test
	void rejectsLargeSurfaceStructuresWhenTheyWouldClipTheShell() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 140, 0, 112, 113, 110, 16);
		BlockBox projectedBox = new BlockBox(-120, 120, -120, 120, 220, 120);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "woodland_mansion"), projectedBox, sphere));
	}

	@Test
	void allowsMansionsWithTallTerrainPaddingWhenFootprintFitsInsideSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 128, 0, 208, 209, 206, 16);
		BlockBox projectedBox = new BlockBox(-80, 8, -80, 80, 260, 80);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "woodland_mansion"), projectedBox, sphere));
	}

	@Test
	void allowsAncientCitiesWhenFootprintFitsEvenWithLargeVerticalBounds() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 96, 0, 208, 209, 206, 16);
		BlockBox projectedBox = new BlockBox(-92, -96, -92, 92, 176, 92);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "ancient_city"), projectedBox, sphere));
	}

	@Test
	void allowsTrailChambersWithDeepVerticalBoundsWhenFootprintFits() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 96, 0, 208, 209, 206, 16);
		BlockBox projectedBox = new BlockBox(-72, -72, -72, 72, 160, 72);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "trial_chambers"), projectedBox, sphere));
	}

	@Test
	void rejectsTrailChambersThatLeakOutsideSphereFootprint() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 96, 0, 128, 129, 126, 16);
		BlockBox projectedBox = new BlockBox(-196, -72, -196, -120, 160, -120);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "trial_chambers"), projectedBox, sphere));
	}

	@Test
	void rejectsStrongholdsThatExtendHorizontallyOutsideTheSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 128, 129, 126, 16);
		BlockBox projectedBox = new BlockBox(-200, 16, -200, -120, 184, -120);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "stronghold"), projectedBox, sphere));
	}

	@Test
	void rejectsMineshaftsThatExtendHorizontallyOutsideTheSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
		BlockBox projectedBox = new BlockBox(-200, 16, -200, -120, 184, -120);

		assertFalse(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "mineshaft"), projectedBox, sphere));
	}

}
