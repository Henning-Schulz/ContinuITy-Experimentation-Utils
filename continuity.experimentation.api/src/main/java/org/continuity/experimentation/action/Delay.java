package org.continuity.experimentation.action;

import org.continuity.experimentation.IExperimentAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple delay for waiting a specified time in milliseconds.
 *
 * @author Henning Schulz
 *
 */
public class Delay implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(Delay.class);

	private final long delayMillis;

	public Delay(long delayMillis) {
		this.delayMillis = delayMillis;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() {
		LOGGER.info("Going to sleep for {} ms...", delayMillis);
		long start = System.currentTimeMillis();

		try {
			Thread.sleep(delayMillis);
		} catch (InterruptedException e) {
			long sleepDuration = System.currentTimeMillis() - start;
			LOGGER.warn("Interrupted during sleep. Slept only {} ms instead of {} ms!", sleepDuration, delayMillis);
			e.printStackTrace();
		}

		LOGGER.info("Resuming.");
	}

}
