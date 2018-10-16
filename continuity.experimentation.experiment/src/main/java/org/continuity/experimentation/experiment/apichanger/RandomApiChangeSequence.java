package org.continuity.experimentation.experiment.apichanger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomApiChangeSequence {

	private static final int SEQUENCE_LENGTH = 60;

	private static final Random RAND = new Random('C' + 'o' + 'n' + 't' + 'i' + 'n' + 'u' + 'I' + 'T' + 'y');

	public static void main(String[] args) {
		List<ApiChangeType> sequence = new ArrayList<>(SEQUENCE_LENGTH);

		for (int i = 0; i < SEQUENCE_LENGTH; i++) {
			sequence.add(ApiChangeType.getRandom(RAND.nextDouble()));
		}

		System.out.println(sequence);
	}


}
