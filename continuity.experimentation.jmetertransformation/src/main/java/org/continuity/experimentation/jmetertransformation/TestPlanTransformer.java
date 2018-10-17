package org.continuity.experimentation.jmetertransformation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.ListedHashTree;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.sf.markov4jmeter.testplangenerator.JMeterEngineGateway;

/**
 * Utility class for transforming JMeter test plans (.jmx) into the ContinuITy-readable json format.
 *
 * @author Henning Schulz
 *
 */
public class TestPlanTransformer {

	private static final String DELIM_ARG = "=";

	private static final String ARG_JMETER_HOME = "-jmeter.home";

	private static final String ARG_FOLDER = "-root.dir";

	public static void main(String[] args) throws IOException {
		Properties properties = parseArgs(args);

		JMeterEngineGateway.getInstance().initJMeter(properties.getProperty(ARG_JMETER_HOME, "NOT-SET"), "bin/jmeter.properties", Locale.ENGLISH);
		JMeterUtils.initLogging();

		for (File file : new File(properties.getProperty(ARG_FOLDER, "./")).listFiles()) {
			if (file.getName().endsWith(".jmx")) {
				createBundle(file);
			} else if (file.isDirectory()) {
				for (File subfile : file.listFiles()) {
					if (subfile.getName().endsWith(".jmx")) {
						createBundle(subfile);
					}
				}
			}
		}
	}

	private static void createBundle(File file) throws IOException {
		ListedHashTree testPlan = (ListedHashTree) SaveService.loadTree(file);

		JMeterTestPlanBundle bundle = new JMeterTestPlanBundle(testPlan, new HashMap<>());
		ObjectMapper mapper = new ObjectMapper();
		String filename = file.getAbsolutePath();
		String jsonFilename = filename.substring(0, filename.length() - 4) + ".json";
		mapper.writeValue(new File(jsonFilename), bundle);
	}

	private static Properties parseArgs(String[] args) {
		Properties properties = new Properties();

		for (String arg : args) {
			String[] tokens = arg.split(DELIM_ARG);

			if (tokens.length != 2) {
				throw new IllegalArgumentException("Arguments need to consist of KEY=VALUE! Passed one was " + arg);
			}

			properties.setProperty(tokens[0], tokens[1]);
		}

		return properties;
	}

}
