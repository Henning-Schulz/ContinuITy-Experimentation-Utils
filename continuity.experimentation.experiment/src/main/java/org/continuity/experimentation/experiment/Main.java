package org.continuity.experimentation.experiment;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;

import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.EmailReport;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.action.continuity.MarkovChainIntoTestPlan;
import org.continuity.experimentation.action.continuity.RandomMarkovChain;
import org.continuity.experimentation.action.continuity.WaitForJmeterReport;
import org.continuity.experimentation.action.inspectit.GetInfluxResults;
import org.continuity.experimentation.action.inspectit.StartNewRecording;
import org.continuity.experimentation.action.inspectit.StopRecording;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;

/**
 * @author Henning Schulz
 *
 */
public class Main {

	private static final String SUT_HOST = "172.16.145.67";

	private static final String CONTINUITY_HOST = "172.16.145.68";

	public static void main(String[] args) throws AbortException {
		// Context switches
		ContextChange restartForReference = new ContextChange("restart-for-reference");
		ContextChange reference = new ContextChange("reference");
		ContextChange restartForGenerated = new ContextChange("restart-for-generated");
		ContextChange generated = new ContextChange("generated");

		// Data holders
		IDataHolder<String[][]> markovChainHolder = new SimpleDataHolder<>("markov-chain", String[][].class);
		IDataHolder<TestPlanBundle> referenceTestplanHolder = new SimpleDataHolder<>("reference-testplan", TestPlanBundle.class);

		IDataHolder<Date> referenceRecordingStartTimeHolder = new SimpleDataHolder<>("reference-recording-start", Date.class);
		IDataHolder<Date> referenceRecordingStopTimeHolder = new SimpleDataHolder<>("reference-recording-stop", Date.class);

		Experiment.newExperiment("ASE-18-workload").loop(25).append(EmailReport.send()) //

				// Restart the heat clinic and create users
				.append(restartForReference.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, "8080")) //
				.append(restartForReference.remove()) //
				.append(new Delay(20000)) //

				// Execute the reference test
				.append(reference.append()) //
				.append(RandomMarkovChain.create(Paths.get("heat-clinic-allowed-transitions.csv"), 1000, markovChainHolder))
				.append(MarkovChainIntoTestPlan.merge(StaticDataHolder.of(Paths.get("heat-clinic-reference-testplan.json")), markovChainHolder, "behavior_model", referenceTestplanHolder))
				.append(new StartNewRecording(referenceRecordingStartTimeHolder, CONTINUITY_HOST))
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, referenceTestplanHolder)).append(new WaitForJmeterReport(CONTINUITY_HOST, "8080")) //
				.append(new StopRecording(referenceRecordingStopTimeHolder, CONTINUITY_HOST)) //
				.append(new GetInfluxResults(CONTINUITY_HOST, "8086", referenceRecordingStartTimeHolder, referenceRecordingStopTimeHolder)) //
				.append(reference.remove()) //

				// Restart the heat clinic and create users
				.append(restartForGenerated.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, "8080")) //
				.append(restartForGenerated.remove()) //
				.append(new Delay(20000)) //

				// TODO: Generate a workload model

				// TODO: Execute the generated test without annotation

				// TODO: Execute the generated test with annotation

				.endLoop().build().execute();

	}

}
