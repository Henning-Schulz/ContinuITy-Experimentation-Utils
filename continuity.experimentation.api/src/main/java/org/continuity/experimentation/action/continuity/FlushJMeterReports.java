package org.continuity.experimentation.action.continuity;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlushJMeterReports extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlushJMeterReports.class);

	public FlushJMeterReports(String host, String port) {
		super(host, port);
	}

	public FlushJMeterReports(String host) {
		super(host, "8080");
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		LOGGER.info("Flushing the JMeter reports...");

		String report = "";
		int flushCount = -1;

		while (report != null) {
			flushCount++;
			report = get("/loadtest/report?timeout=10000", String.class);
		}

		LOGGER.info("Flushed {} JMeter reports.", flushCount);
	}

}
