package xyz.coolsa.biosphere;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

final class BiospheresColumnStates {

	private BiospheresColumnStates() {
	}

	static BlockState[] airFilled(int height) {
		BlockState[] states = new BlockState[height];
		for (int i = 0; i < states.length; i++) {
			states[i] = Blocks.AIR.getDefaultState();
		}
		return states;
	}
}
