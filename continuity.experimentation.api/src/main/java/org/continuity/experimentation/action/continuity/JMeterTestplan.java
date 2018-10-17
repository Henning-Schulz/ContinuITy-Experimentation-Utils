package org.continuity.experimentation.action.continuity;

import java.nio.file.Path;

import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.experimentation.data.IDataHolder;

/**
 * Subsumes JMeter test plan actions.
 *
 * @see JMeterTestplanReader
 * @see JMeterTestplanWriter
 * @see JMeterTestPlanUploader
 *
 * @author Henning Schulz
 *
 */
public final class JMeterTestplan {

	private JMeterTestplan() {
		// should not be instantiated
	}

	/**
	 * Reads a JMeter test plan.
	 *
	 * @param dirPath
	 *            [in] Path to the directory holding the test plan. The directory must hold exactly
	 *            one file ending with .jmx and potentially multiple files ending with .csv as
	 *            behavior models.
	 * @param testplanBundle
	 *            [out] The read test plan.
	 */
	public static JMeterTestplanReader read(IDataHolder<Path> dirPath, IDataHolder<JMeterTestPlanBundle> testplanBundle) {
		return new JMeterTestplanReader(dirPath, testplanBundle);
	}

	/**
	 * Writes a JMeter test plan.
	 *
	 * @param testplanBundle
	 *            [in] The test plan to be written.
	 */
	public static JMeterTestplanWriter write(IDataHolder<JMeterTestPlanBundle> testplanBundle) {
		return new JMeterTestplanWriter(testplanBundle);
	}

	/**
	 * Uploads a JMeter test plan to ContinuITy.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param port
	 *            The port of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public static JMeterTestPlanUploader upload(String host, String port, IDataHolder<JMeterTestPlanBundle> testplanBundle, IDataHolder<String> tag) {
		return new JMeterTestPlanUploader(host, port, testplanBundle, tag);
	}

	/**
	 * Uploads a JMeter test plan to ContinuITy.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public static JMeterTestPlanUploader upload(String host, IDataHolder<JMeterTestPlanBundle> testplanBundle, IDataHolder<String> tag) {
		return new JMeterTestPlanUploader(host, testplanBundle, tag);
	}

}
