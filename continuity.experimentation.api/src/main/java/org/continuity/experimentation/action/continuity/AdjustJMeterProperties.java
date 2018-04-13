package org.continuity.experimentation.action.continuity;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

public class AdjustJMeterProperties implements IExperimentAction {

	private static final String NUM_USERS_PATTERN = "(<stringProp name=\"ThreadGroup.num_threads\">)(\\d+)(</stringProp>\n)";

	private static final String DURATION_PATTERN = "(<stringProp name=\"ThreadGroup.duration\">)(\\d+)(</stringProp>\n)";

	private static final String RAMP_UP_PATTERN = "(<stringProp name=\"ThreadGroup.ramp_time\">)(\\d+)(</stringProp>\n)";

	private int numUsers = 1;

	private long duration = 120;

	private int rampUp = 1;

	private final IDataHolder<TestPlanBundle> testPlanBundle;

	private AdjustJMeterProperties(IDataHolder<TestPlanBundle> testPlanBundle) {
		this.testPlanBundle = testPlanBundle;
	}

	public static AdjustJMeterProperties of(IDataHolder<TestPlanBundle> testPlanBundle) {
		return new AdjustJMeterProperties(testPlanBundle);
	}

	public AdjustJMeterProperties with(int numUsers, long duration, int rampUp) {
		this.numUsers = numUsers;
		this.duration = duration;
		this.rampUp = rampUp;

		return this;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		TestPlanBundle bundle = testPlanBundle.get();
		String testplan = bundle.getTestPlan();

		testplan = replace(testplan, NUM_USERS_PATTERN, numUsers);
		testplan = replace(testplan, DURATION_PATTERN, duration);
		testplan = replace(testplan, RAMP_UP_PATTERN, rampUp);

		bundle.setTestPlan(testplan);
		testPlanBundle.set(bundle);
	}

	private String replace(String testplan, String pattern, Object value) {
		return testplan.replaceAll(pattern, "$1" + value + "$3");
	}

}
