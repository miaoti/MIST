package io.mist.core.util;

/**
 *
 * @author Sergio Segura
 */
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class IDGenerator {


	static long seed = -1;
	static Random rand = new Random();

	/**
	 * @return a short ID (13 characters)
	 */
	public static String generateId() {
		byte[] bytes = new byte[16];
		rand.nextBytes(bytes);
		UUID uuid = UUID.nameUUIDFromBytes(bytes);
		long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
		return Long.toString(l, Character.MAX_RADIX);
	}

	public static String generateTimeId() {
		Long baseSeed = SeededRandom.getBaseSeed();
		if (baseSeed != null) {
			// Under -Drandom.seed, the time-of-day suffix would re-introduce
			// the very non-determinism the seed flag exists to eliminate
			// (the suffix flows into the generated test class name via
			// MistRunner). Return the configured seed so two consecutive
			// seeded runs produce identical Flow_Scenario_*.java sources
			// without needing post-hoc normalisation.
			return String.valueOf(baseSeed);
		}
		return String.valueOf(new Date().getTime());
	}

	public static void setSeed(long s) {
		seed=s;
		rand.setSeed(seed);
	}
}
