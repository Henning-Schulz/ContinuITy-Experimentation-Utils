package org.continuity.experimentation.experiment;

import java.io.File;
import java.util.Locale;

import org.apache.jmeter.util.JMeterUtils;
import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.continuity.JMeterTestplan;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.sf.markov4jmeter.testplangenerator.JMeterEngineGateway;

public class JMeterDecoding {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static void main(String[] args) throws AbortInnerException, AbortException, Exception {
		initJMeter();

		Context context = new Context();
		context.append("exp-modularization");
		traverseDirs(context);
	}

	private static void initJMeter() {
		JMeterEngineGateway.getInstance().initJMeter("jmeter", "bin/jmeter.properties", Locale.ENGLISH);
		JMeterUtils.initLogging();
	}

	private static void traverseDirs(Context context) throws AbortInnerException, AbortException, Exception {
		decodeTestPlan(context);

		for (File subdir : context.toPath().toFile().listFiles()) {
			if (subdir.isDirectory()) {
				context.append(subdir.getName());
				traverseDirs(context);
				context.remove(subdir.getName());
			}
		}
	}

	private static void decodeTestPlan(Context context) throws AbortInnerException, AbortException, Exception {
		File testplanFile = context.toPath().resolve("load-test.json").toFile();

		if (testplanFile.exists()) {
			JMeterTestPlanBundle bundle = MAPPER.readValue(testplanFile, JMeterTestPlanBundle.class);
			JMeterTestplan.write(StaticDataHolder.of(bundle)).execute(context);
		}
	}

}
