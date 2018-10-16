package org.continuity.experimentation.experiment;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;

import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.Abort;
import org.continuity.experimentation.action.Clock;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.AdjustJMeterProperties;
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
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.PathHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;

/**
 * ContinuITy version: 0.3.56
 *
 * @author Henning Schulz
 *
 */
public class FooMain {

	private static final String SUT_HOST = "172.16.145.67";

	private static final String CONTINUITY_HOST = "localhost";

	private static final String INSPECTIT_HOST = "172.16.145.68";

	private static final String CONTINUITY_PORT = "8080";

	private static final int NUM_USERS = 10;

	private static final long DURATION = 120;

	private static final long THINK_TIME = 1000;

	private static final String TAG = "heat-clinic-v2";

	public static void main(String[] args) throws AbortException {
		// Context switches
		ContextChange restartForReference = new ContextChange("1-restart-for-reference");
		ContextChange reference = new ContextChange("2-reference");
		ContextChange restartForGeneratedWithAnn = new ContextChange("3-restart-for-generated");
		ContextChange generatedWithAnn = new ContextChange("4-generated");

		// Data holders
		IDataHolder<String[][]> markovChainHolder = new SimpleDataHolder<>("markov-chain", String[][].class);
		IDataHolder<TestPlanBundle> referenceTestplanHolder = new SimpleDataHolder<>("reference-testplan", TestPlanBundle.class);

		IDataHolder<Date> recordingStartTimeHolder = new SimpleDataHolder<>("recording-start", Date.class);
		IDataHolder<Date> recordingStopTimeHolder = new SimpleDataHolder<>("recording-stop", Date.class);

		IDataHolder<String> measurementDataLink = new SimpleDataHolder<>("measurement-data-link", "http://" + INSPECTIT_HOST + ":8182/", true);
		IDataHolder<String> workloadLinkNoAnn = new SimpleDataHolder<>("workload-link-no-ann", String.class);
		IDataHolder<String> workloadLinkWithAnn = new SimpleDataHolder<>("workload-link-with-ann", String.class);

		IDataHolder<String> versionStringHolder = StaticDataHolder.of("v2");
		IDataHolder<Path> testPlanPathHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("reference-testplan.json");
		IDataHolder<Path> allowedTransitionsHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("allowed-transitions.csv");

		IDataHolder<String> systemReportHolder = new SimpleDataHolder<>("system-report", String.class);
		IDataHolder<String> annotationReportHolder = new SimpleDataHolder<>("annotation-report", String.class);

		Experiment.newExperiment("Example-Models").loop(1) //

				// Invalidate all data holders
				.append(new DataInvalidation(versionStringHolder, markovChainHolder, referenceTestplanHolder, recordingStartTimeHolder, recordingStopTimeHolder, measurementDataLink,
						workloadLinkNoAnn, workloadLinkWithAnn, systemReportHolder, annotationReportHolder))

				// Flush JMeter reports
				.append(new FlushJMeterReports(CONTINUITY_HOST))

				// Change heat clinic version
				.append(TargetSystem.checkoutGitVersion(Application.HEAT_CLINIC, versionStringHolder, SUT_HOST))

				// Restart the heat clinic and create users
				.append(restartForReference.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, 60)) //
				.append(restartForReference.remove()) //
				.append(new Delay(20000)) //

				// Execute the reference test
				.append(reference.append()) //
				.append(RandomMarkovChain.create(allowedTransitionsHolder, THINK_TIME, markovChainHolder))
				.append(MarkovChainIntoTestPlan.merge(testPlanPathHolder, markovChainHolder, "behavior_model", referenceTestplanHolder))
				.append(AdjustJMeterProperties.of(referenceTestplanHolder).with(NUM_USERS, DURATION, NUM_USERS))
				.append(Clock.takeTime(recordingStartTimeHolder)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, referenceTestplanHolder)).append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(Clock.takeTime(recordingStopTimeHolder)) //
				.append(new GetInfluxResults(INSPECTIT_HOST, "8086", recordingStartTimeHolder, recordingStopTimeHolder)) //
				.append(reference.remove()) //

				// Restart the heat clinic
				.append(restartForGeneratedWithAnn.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST))

				// Generate the workload model
				.append(new AppendTimeRangeRequestParameter(recordingStartTimeHolder, recordingStopTimeHolder, measurementDataLink))
				.append(new WorkloadModelGeneration(CONTINUITY_HOST, CONTINUITY_PORT, "wessbas", StaticDataHolder.of(TAG), measurementDataLink, workloadLinkWithAnn))
				.append(Abort.innerIf(workloadLinkWithAnn::isNotSet, "The WESSBAS model with annotation was not correctly generated!"))

				//
				.append(new DataInvalidation(recordingStartTimeHolder, recordingStopTimeHolder))

				// Wait for the heat clinic and create users
				.append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
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
