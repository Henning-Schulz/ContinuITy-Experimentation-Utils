package org.continuity.experimentation.experiment;

import java.util.Locale;

import org.apache.jmeter.util.JMeterUtils;
import org.continuity.experimentation.exception.AbortException;

import net.sf.markov4jmeter.testplangenerator.JMeterEngineGateway;

/**
 * @author Henning Schulz
 *
 */
public class Main {

	public static void main(String[] args) throws AbortException {
		// Do not remove unless you are not using JMeter!
		// Also, requires the jmeter folder in the continuity.experimentation.experiment project to
		// be placed in the folder where this is executed.
		initJMeter();

		// TODO: Implement experiment

	}

	private static void initJMeter() {
		JMeterEngineGateway.getInstance().initJMeter("jmeter", "bin/jmeter.properties", Locale.ENGLISH);
		JMeterUtils.initLogging();
	}

}
