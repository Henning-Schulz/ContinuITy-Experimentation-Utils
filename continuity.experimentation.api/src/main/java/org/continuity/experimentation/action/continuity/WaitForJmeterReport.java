package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for a JMeter report to be ready and stores it in the current context as
 * {@code jmeter-report.csv}.
 *
 * @author Henning Schulz
 *
 */
public class WaitForJmeterReport extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(WaitForJmeterReport.class);

	private final long timeToStopWaiting;

	/**
	 * Constructor.
	 *
	 * @param reportDestination
	 *            the destination, where the report should be stored (including the filename).
	 * @param host
	 *            The host of the continuITy frontend.
	 * @param port
	 *            The port of the continuITy frontend.
	 * @param expectedTestDuration
	 *            The expected duration of the test.
	 */
	public WaitForJmeterReport(String host, String port, long expectedTestDuration) {
		super(host, port);
		this.timeToStopWaiting = System.currentTimeMillis() + Math.min(1800000, Math.max(600000, 2 * expectedTestDuration));
	}

	/**
	 * Constructor using the default port 8080.
	 *
	 * @param reportDestination
	 *            the destination, where the report should be stored (including the filename).
	 * @param host
	 *            The host of the continuITy frontend.
	 * @param expectedTestDuration
	 *            The expected duration of the test.
	 */
	public WaitForJmeterReport(String host, long expectedTestDuration) {
		this(host, "8080", expectedTestDuration);
	}

	/**
	 * Constructor using the default port 8080.
	 *
	 * @param reportDestination
	 *            the destination, where the report should be stored (including the filename).
	 * @param host
	 *            The host of the continuITy frontend.
	 */
	public WaitForJmeterReport(String host) {
		this(host, 1800000);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(Context context) throws IOException {
		LOGGER.info("Waiting for jmeter report of test...");

		long startTime = System.currentTimeMillis();
		long currentTime;

		String report = null;
		while ((currentTime = System.currentTimeMillis()) < timeToStopWaiting) {
			report = get("/loadtest/report?timeout=20000", String.class);
			if (report != null) {
				break;
			}
		}

		if (report != null) {
			Path basePath = context.toPath();
			FileUtils.writeStringToFile(basePath.resolve("jmeter-report.csv").toFile(), report, Charset.defaultCharset());
			LOGGER.info("Wrote JMeter report to {}.", basePath);
		} else {
			LOGGER.warn("Could not get a JMeter report within {} seconds!", (currentTime - startTime) / 1000);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Wait for the report at " + super.toString();
	}

}
