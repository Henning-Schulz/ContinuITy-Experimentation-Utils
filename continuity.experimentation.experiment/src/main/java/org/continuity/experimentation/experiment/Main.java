package org.continuity.experimentation.experiment;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;

import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.Abort;
import org.continuity.experimentation.action.Clock;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.EmailReport;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.AppendTimeRangeRequestParameter;
import org.continuity.experimentation.action.continuity.FlushJMeterReports;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.action.continuity.MarkovChainIntoTestPlan;
import org.continuity.experimentation.action.continuity.RandomMarkovChain;
import org.continuity.experimentation.action.continuity.WaitForJmeterReport;
import org.continuity.experimentation.action.continuity.WorkloadModelGeneration;
import org.continuity.experimentation.action.continuity.WorkloadTransformationAndExecution;
import org.continuity.experimentation.action.inspectit.GetInfluxResults;
import org.continuity.experimentation.data.CountingDataHolder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;

/**
 * ContinuITy version: 0.3.52
 *
 * @author Henning Schulz
 *
 */
public class Main {

	private static final String SUT_HOST = "172.16.145.67";

	private static final String CONTINUITY_HOST = "172.16.145.68";

	private static final String CONTINUITY_PORT = "8080";

	private static final int NUM_USERS = 75;

	private static final long DURATION = 900;

	private static final long THINK_TIME = 5000;

	private static final String TAG = "heat-clinic";

	private static final String TAG_NO_ANN = "heat-clinic-no-ann";

	public static void main(String[] args) throws AbortException {
		// Context switches
		ContextChange restartForReference = new ContextChange("1-restart-for-reference");
		ContextChange reference = new ContextChange("2-reference");
		ContextChange restartForGeneratedNoAnn = new ContextChange("3-restart-for-generated-no-ann");
		ContextChange generatedNoAnn = new ContextChange("4-generated-no-ann");
		ContextChange restartForGeneratedWithAnn = new ContextChange("5-restart-for-generated-with-ann");
		ContextChange generatedWithAnn = new ContextChange("6-generated-with-ann");

		// Data holders
		IDataHolder<String[][]> markovChainHolder = new SimpleDataHolder<>("markov-chain", String[][].class);
		IDataHolder<TestPlanBundle> referenceTestplanHolder = new SimpleDataHolder<>("reference-testplan", TestPlanBundle.class);

		IDataHolder<Date> recordingStartTimeHolder = new SimpleDataHolder<>("recording-start", Date.class);
		IDataHolder<Date> recordingStopTimeHolder = new SimpleDataHolder<>("recording-stop", Date.class);

		IDataHolder<String> measurementDataLink = new SimpleDataHolder<>("measurement-data-link", "http://" + CONTINUITY_HOST + ":8182/", true);
		IDataHolder<String> workloadLinkNoAnn = new SimpleDataHolder<>("workload-link-no-ann", String.class);
		IDataHolder<String> workloadLinkWithAnn = new SimpleDataHolder<>("workload-link-with-ann", String.class);

		IDataHolder<String> tagForNoAnnGeneration = CountingDataHolder.of(TAG_NO_ANN);

		Experiment.newExperiment("ASE-18-workload").loop(20) //

				// Invalidate all data holders
				.append(new DataInvalidation(markovChainHolder, referenceTestplanHolder, recordingStartTimeHolder, recordingStopTimeHolder, measurementDataLink, workloadLinkNoAnn, workloadLinkWithAnn,
						tagForNoAnnGeneration))

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
				.append(RandomMarkovChain.create(Paths.get("heat-clinic-allowed-transitions.csv"), THINK_TIME, markovChainHolder))
				.append(MarkovChainIntoTestPlan.merge(StaticDataHolder.of(Paths.get("heat-clinic-reference-testplan.json")), markovChainHolder, "behavior_model", referenceTestplanHolder))
				.append(Clock.takeTime(recordingStartTimeHolder)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, referenceTestplanHolder)).append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(Clock.takeTime(recordingStopTimeHolder)) //
				.append(new GetInfluxResults(CONTINUITY_HOST, "8086", recordingStartTimeHolder, recordingStopTimeHolder)) //
				.append(reference.remove()) //

				// Restart the heat clinic
				.append(restartForGeneratedNoAnn.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST))

				// Generate the workload models
				.append(new AppendTimeRangeRequestParameter(recordingStartTimeHolder, recordingStopTimeHolder, measurementDataLink))
				.append(new WorkloadModelGeneration(CONTINUITY_HOST, CONTINUITY_PORT, "wessbas", tagForNoAnnGeneration, measurementDataLink, workloadLinkNoAnn))
				.append(Abort.innerIf(workloadLinkNoAnn::isNotSet, "The WESSBAS model without annotation was not correctly generated!"))
				.append(new WorkloadModelGeneration(CONTINUITY_HOST, CONTINUITY_PORT, "wessbas", StaticDataHolder.of(TAG), measurementDataLink, workloadLinkWithAnn))
				.append(Abort.innerIf(workloadLinkWithAnn::isNotSet, "The WESSBAS model with annotation was not correctly generated!"))

				//
				.append(new DataInvalidation(recordingStartTimeHolder, recordingStopTimeHolder))

				// Wait for the heat clinic and create users
				.append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, 60)) //
				.append(restartForGeneratedNoAnn.remove()) //
				.append(new Delay(20000)) //

				// Execute the generated test without annotation
				.append(generatedNoAnn.append()) //
				.append(Clock.takeTime(recordingStartTimeHolder))
				.append(new WorkloadTransformationAndExecution(CONTINUITY_HOST, "jmeter", StaticDataHolder.of(TAG_NO_ANN), workloadLinkNoAnn, NUM_USERS, DURATION, NUM_USERS))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(Clock.takeTime(recordingStopTimeHolder)) //
				.append(new GetInfluxResults(CONTINUITY_HOST, "8086", recordingStartTimeHolder, recordingStopTimeHolder)) //
				.append(generatedNoAnn.remove())

				// Restart the heat clinic and create users
				.append(restartForGeneratedWithAnn.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, 60)) //
				.append(restartForGeneratedWithAnn.remove()) //
				.append(new Delay(20000)) //

				// Execute the generated test with annotation
				.append(generatedWithAnn.append()) //
				.append(Clock.takeTime(recordingStartTimeHolder))
				.append(new WorkloadTransformationAndExecution(CONTINUITY_HOST, "jmeter", StaticDataHolder.of(TAG), workloadLinkWithAnn, NUM_USERS, DURATION, NUM_USERS))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(Clock.takeTime(recordingStopTimeHolder)) //
				.append(new GetInfluxResults(CONTINUITY_HOST, "8086", recordingStartTimeHolder, recordingStopTimeHolder)) //
				.append(generatedWithAnn.remove())

				//
				.endLoop()

				//
				.build().execute();

	}

}
