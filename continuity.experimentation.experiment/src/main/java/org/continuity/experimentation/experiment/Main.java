package org.continuity.experimentation.experiment;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;

import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.Clock;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.EmailReport;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.FlushJMeterReports;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.action.continuity.MarkovChainIntoTestPlan;
import org.continuity.experimentation.action.continuity.RandomMarkovChain;
import org.continuity.experimentation.action.continuity.WaitForJmeterReport;
import org.continuity.experimentation.action.inspectit.GetInfluxResults;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;

/**
 * ContinuITy version: 0.3.56
 *
 * @author Henning Schulz
 *
 */
public class Main {

	private static final String SUT_HOST = "172.16.145.67";

	private static final String CONTINUITY_HOST = "172.16.145.68";

	private static final long DURATION = 900;

	private static final long THINK_TIME = 5000;

	public static void main2(String[] args) throws AbortException {
		// Context switches
		ContextChange restartForReference = new ContextChange("1-restart-for-reference");
		ContextChange reference = new ContextChange("2-reference");

		// Data holders
		IDataHolder<String[][]> markovChainHolder = new SimpleDataHolder<>("markov-chain", String[][].class);
		IDataHolder<TestPlanBundle> referenceTestplanHolder = new SimpleDataHolder<>("reference-testplan", TestPlanBundle.class);

		IDataHolder<Date> recordingStartTimeHolder = new SimpleDataHolder<>("recording-start", Date.class);
		IDataHolder<Date> recordingStopTimeHolder = new SimpleDataHolder<>("recording-stop", Date.class);

		Experiment.newExperiment("ASE-18-rho-baseline") //

				.append(RandomMarkovChain.create(StaticDataHolder.of(Paths.get("heat-clinic-allowed-transitions.csv")), THINK_TIME, markovChainHolder))
				.append(MarkovChainIntoTestPlan.merge(StaticDataHolder.of(Paths.get("heat-clinic-reference-testplan.json")), markovChainHolder, "behavior_model", referenceTestplanHolder))

				.loop(20)

				.append(new DataInvalidation(recordingStartTimeHolder, recordingStopTimeHolder))

				.append(EmailReport.send()) //

				// Restart CMR
				.append(TargetSystem.restart(Application.CMR, CONTINUITY_HOST)).append(TargetSystem.waitFor(Application.CMR, CONTINUITY_HOST, "8182"))
				.append(new Delay(60000))

				// Flush JMeter reports
				.append(new FlushJMeterReports(CONTINUITY_HOST))

				// Restart the heat clinic and create users
				.append(restartForReference.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, 60)) //
				.append(restartForReference.remove()) //
				.append(new Delay(20000)) //

				// Execute the reference test
				.append(reference.append()) //
				.append(Clock.takeTime(recordingStartTimeHolder)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, referenceTestplanHolder)).append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(Clock.takeTime(recordingStopTimeHolder)) //
				.append(new GetInfluxResults(CONTINUITY_HOST, "8086", recordingStartTimeHolder, recordingStopTimeHolder)) //
				.append(reference.remove()) //

				//
				.endLoop()

				//
				.build().execute();

	}

}
