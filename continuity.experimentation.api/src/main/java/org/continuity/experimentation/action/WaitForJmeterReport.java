package org.continuity.experimentation.action;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.data.IDataHolder;

public class WaitForJmeterReport extends AbstractRestAction {

	/**
	 * reference loadtest report path
	 */
	private static final String REFERENCE_LOADTEST_REPORT_PATH = "/referenceLoadtest/";

	/**
	 * generated loadtest report path
	 */
	private static final String GENERATED_LOADTEST_REPORT_PATH = "/generatedLoadtest/";

	/**
	 * The maximum number of attempts to retrieve the report.
	 */
	private static final int MAX_ATTEMPTS = 200;

	/**
	 * run count
	 */
	// Will be increased before the first iteration
	private static int runCount = -1;

	/**
	 * The desired report destination.
	 */
	private String reportDestination;

	/**
	 * Is running loadtest automatically generated.
	 */
	private boolean generatedLoadtest;

	/**
	 * Chosen loadTest
	 */
	private IDataHolder<TestPlanBundle> chosenLoadTest;

	/**
	 * Constructor.
	 *
	 * @param reportDestination
	 *            the destination, where the report should be stored (including the filename).
	 * @param host
	 *            The host of the continuITy frontend.
	 * @param port
	 *            The port of the continuITy frontend.
	 * @param reportDestination
	 *            the report destination.
	 * @param chosenLoadTest
	 *            the chosen load test
	 */
	public WaitForJmeterReport(String host, String port, String reportDestination, boolean generatedLoadtest, IDataHolder<TestPlanBundle> chosenLoadTest) {
		super(host, port);
		this.reportDestination = reportDestination;
		this.generatedLoadtest = generatedLoadtest;
		this.chosenLoadTest = chosenLoadTest;
	}

	@Override
	public void execute() {
		String report = null;
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			report = get("/loadtest/report?timeout=20000", String.class);
			if (report != null) {
				break;
			}
		}
		if (report != null) {
			try {
				File file = new File(reportDestination);
				File manipulatedFile = null;
				if(generatedLoadtest) {
					manipulatedFile = new File("run#" + runCount + GENERATED_LOADTEST_REPORT_PATH + file.getName());
				} else {
					// Increase count before each reference load test.
					// (Workload model generation can fail and then, no generated load test will be
					// executed)
					runCount++;
					manipulatedFile = new File("run#" + runCount + REFERENCE_LOADTEST_REPORT_PATH + file.getName());
				}
				FileUtils.writeStringToFile(manipulatedFile, report, Charset.defaultCharset());

				// Copy loadTest config to the corresponding run.
				FileUtils.copyFile(chosenLoadTest.get().getFile(), new File(manipulatedFile.getParent() + "/" + chosenLoadTest.get().getFile().getName()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
