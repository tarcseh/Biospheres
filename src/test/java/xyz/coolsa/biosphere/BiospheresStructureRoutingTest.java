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
	void allowsUnknownStructureClassificationsToFallBackWithoutBlockingShellPasses() {
		assertTrue(BiospheresStructureRouting.defaultRouting().canSkipUnknownStructure(Identifier.of("biospheres", "unknown_structure")));
	}

	@Test
	void allowsStrongholdsToPreserveVanillaLocateBehavior() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
		BlockBox projectedBox = new BlockBox(-40, 16, -40, 40, 184, 40);

		assertTrue(BiospheresStructureRouting.defaultRouting().canAccept(Identifier.of("minecraft", "stronghold"), projectedBox, sphere));
	}

	@Test
	void rejectsStrongholdsWhoseProjectedBoundsDropFarBelowTheSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
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
	void rejectsStrongholdsThatExtendHorizontallyOutsideTheSphere() {
		BiospheresSphereDescriptor sphere = new BiospheresSphereDescriptor(0, 100, 0, 96, 97, 94, 16);
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
