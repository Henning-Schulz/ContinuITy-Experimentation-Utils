package org.continuity.experimentation.experiment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.apache.jmeter.util.JMeterUtils;
import org.continuity.api.entities.config.ModularizationApproach;
import org.continuity.experimentation.exception.AbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.markov4jmeter.testplangenerator.JMeterEngineGateway;

/**
 * Experiment with the Sock Shop for the ContinuITy microservice modularization.
 *
 * @author Tobias Angerstein, Henning Schulz
 *
 */
public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	private static final String TAG = "sock-shop";
	private static final String DATE_FORMAT = "yyyy/MM/dd/HH:mm:ss";

	private static void initJMeter() {
		JMeterEngineGateway.getInstance().initJMeter("jmeter", "bin/jmeter.properties", Locale.ENGLISH);
		JMeterUtils.initLogging();
	}

	public static void main(String[] args) throws AbortException, IOException {
		initJMeter();

		ExperimentProperties props = new ExperimentProperties("config.properties", TAG, DATE_FORMAT);
		List<TestExecution> testExecutions = readTestExecutions();

		ModularizationExperiment experiment = new ModularizationExperiment(props, testExecutions);

		System.out.println(experiment.getExperiment());
	}

	private static List<TestExecution> readTestExecutions() {
		String testExecutionsCSV = "";
		try {
			testExecutionsCSV = FileUtils.readFileToString(new File("test-executions.csv"), "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<TestExecution> testExecutions = new ArrayList<TestExecution>();

		for(String row : testExecutionsCSV.split("\n")) {
			String modularizationApproach = row.split(",")[0];

			if ("non-modularized".equals(modularizationApproach)) {
				testExecutions.add(new TestExecution(false));
				LOGGER.info("Added non-modularized test case.");
			} else {
				String[] servicesUnderTest = Arrays.copyOfRange(row.split(","), 1, row.split(",").length);
				testExecutions.add(new TestExecution(ModularizationApproach.fromPrettyString(modularizationApproach), servicesUnderTest));
				LOGGER.info("Added test case {}: {}.", modularizationApproach, servicesUnderTest);
			}
		}

		return testExecutions;
	}

}
