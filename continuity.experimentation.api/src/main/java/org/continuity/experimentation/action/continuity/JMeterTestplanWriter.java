package org.continuity.experimentation.action.continuity;

import java.nio.file.Path;

import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.commons.jmeter.TestPlanWriter;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMeterTestplanWriter implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterTestplanWriter.class);

	private static final String PREFIX = "wessbas-";

	private final TestPlanWriter writer = new TestPlanWriter();

	private final IDataHolder<JMeterTestPlanBundle> testplanBundle;

	/**
	 * Use {@link JMeterTestplan#write(IDataHolder)} instead of this constructor.
	 *
	 * @param testplanBundle
	 *            [in] The test plan to be written.
	 */
	protected JMeterTestplanWriter(IDataHolder<JMeterTestPlanBundle> testplanBundle) {
		this.testplanBundle = testplanBundle;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		Path outputDir = getNewDir(context.toPath());

		LOGGER.info("Writing test plan bundle {} to {}.", testplanBundle, outputDir);
		writer.write(testplanBundle.get().getTestPlan(), testplanBundle.get().getBehaviors(), outputDir);
		LOGGER.info("Successfully wrote test plan to {}.", outputDir);
	}

	private Path getNewDir(Path root) {
		int number = 1;

		while (root.resolve(PREFIX + number).toFile().exists()) {
			number++;
		}

		Path path = root.resolve(PREFIX + number);
		path.toFile().mkdirs();

		return path;
	}

	@Override
	public String toString() {
		return "Write \"" + testplanBundle + "\" to the current context dir.";
	}

}
