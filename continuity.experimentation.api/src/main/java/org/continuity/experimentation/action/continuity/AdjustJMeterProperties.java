package org.continuity.experimentation.action.continuity;

import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.commons.jmeter.JMeterPropertiesCorrector;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdjustJMeterProperties implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdjustJMeterProperties.class);

	private final JMeterPropertiesCorrector propertiesCorrector = new JMeterPropertiesCorrector();

	private int numUsers = 1;

	private long duration = 120;

	private int rampUp = 1;

	private final IDataHolder<JMeterTestPlanBundle> testPlanBundle;

	private AdjustJMeterProperties(IDataHolder<JMeterTestPlanBundle> testPlanBundle) {
		this.testPlanBundle = testPlanBundle;
	}

	public static AdjustJMeterProperties of(IDataHolder<JMeterTestPlanBundle> testPlanBundle) {
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
		LOGGER.info("Setting properties numUsers = {}, duration = {}, rampUp = {} in test plan {}.", numUsers, duration, rampUp, testPlanBundle);

		JMeterTestPlanBundle bundle = testPlanBundle.get();
		propertiesCorrector.setRuntimeProperties(bundle.getTestPlan(), numUsers, duration, rampUp);
		testPlanBundle.set(bundle);
	}

}
