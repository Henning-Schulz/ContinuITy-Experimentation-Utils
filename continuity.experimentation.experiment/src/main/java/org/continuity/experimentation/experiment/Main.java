package org.continuity.experimentation.experiment;

import java.io.File;
import java.nio.file.Path;

import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.AdjustJMeterProperties;
import org.continuity.experimentation.action.continuity.FlushJMeterReports;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.action.continuity.MarkovChainIntoTestPlan;
import org.continuity.experimentation.action.continuity.RandomMarkovChain;
import org.continuity.experimentation.action.continuity.UploadAnnotation;
import org.continuity.experimentation.action.continuity.UploadSystemModel;
import org.continuity.experimentation.action.continuity.WaitForJmeterReport;
import org.continuity.experimentation.data.CountingDataHolder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.PathHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;

public class Main {

	private static final String SUT_HOST = "172.16.145.67";

	private static final String CONTINUITY_HOST = "172.16.145.68";

	private static final String CONTINUITY_PORT = "8080";

	private static final int NUM_USERS = 1;

	private static final long DURATION = 180;

	private static final long THINK_TIME = 100;

	private static final String TAG = "heat-clinic";

	public static void main(String[] args) throws AbortException {
		// Context switches
		ContextChange restartForReference = new ContextChange("1-restart");
		ContextChange reference = new ContextChange("2-test");

		IDataHolder<String[][]> markovChainHolder = new SimpleDataHolder<>("markov-chain", String[][].class);
		IDataHolder<TestPlanBundle> referenceTestplanHolder = new SimpleDataHolder<>("reference-testplan", TestPlanBundle.class);

		IDataHolder<String> versionStringHolder = CountingDataHolder.of("v").withStartValue(0);
		IDataHolder<Path> annotationPathHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("annotation-heat-clinic.yml");
		IDataHolder<Path> systemModelPathHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("system-model-heat-clinic.yml");
		IDataHolder<Path> testPlanPathHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("reference-testplan.json");
		IDataHolder<Path> allowedTransitionsHolder = PathHolder.newPath().resolveStatic("heat-clinic-versions").resolveDynamic(versionStringHolder).resolveStatic("allowed-transitions.csv");

		IDataHolder<String> systemReportHolder = new SimpleDataHolder<>("system-report", String.class);
		IDataHolder<String> annotationReportHolder = new SimpleDataHolder<>("annotation-report", String.class);

		Experiment.newExperiment("ASE-18-api-test").loop(1) //
				.append(new DataInvalidation(versionStringHolder, markovChainHolder, referenceTestplanHolder))

				// Flush JMeter reports
				.append(new FlushJMeterReports(CONTINUITY_HOST))

				// Change version
				.append(TargetSystem.checkoutGitVersion(Application.HEAT_CLINIC, versionStringHolder, SUT_HOST))
				.append(UploadSystemModel.from(systemModelPathHolder, StaticDataHolder.of(TAG)).to(CONTINUITY_HOST, systemReportHolder))
				.append(new Delay(5000))
				.append(UploadAnnotation.from(annotationPathHolder, StaticDataHolder.of(TAG)).to(CONTINUITY_HOST, annotationReportHolder))

				// Restart the heat clinic and create users
				.append(restartForReference.append()) //
				.append(TargetSystem.restart(Application.HEAT_CLINIC, SUT_HOST)).append(TargetSystem.waitFor(Application.HEAT_CLINIC, SUT_HOST)) //
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, StaticDataHolder.of(new TestPlanBundle(new File("heat-clinic-register-users.json")))))
				.append(new WaitForJmeterReport(CONTINUITY_HOST, 60)) //
				.append(restartForReference.remove()) //

				// Execute the reference test
				.append(reference.append()) //
				.append(RandomMarkovChain.create(allowedTransitionsHolder, THINK_TIME, markovChainHolder))
				.append(MarkovChainIntoTestPlan.merge(testPlanPathHolder, markovChainHolder, "behavior_model", referenceTestplanHolder))
				.append(AdjustJMeterProperties.of(referenceTestplanHolder).with(NUM_USERS, DURATION, NUM_USERS))
				.append(new JMeterTestPlanExecution(CONTINUITY_HOST, referenceTestplanHolder)).append(new WaitForJmeterReport(CONTINUITY_HOST, DURATION)) //
				.append(reference.remove()) //

				.endLoop().build().execute();
	}

}
