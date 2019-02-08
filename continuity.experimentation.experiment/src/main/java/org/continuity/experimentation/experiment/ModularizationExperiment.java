package org.continuity.experimentation.experiment;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.api.entities.config.LoadTestType;
import org.continuity.api.entities.config.ModularizationApproach;
import org.continuity.api.entities.config.ModularizationOptions;
import org.continuity.api.entities.config.Order;
import org.continuity.api.entities.config.OrderGoal;
import org.continuity.api.entities.config.OrderMode;
import org.continuity.api.entities.config.OrderOptions;
import org.continuity.api.entities.config.WorkloadModelType;
import org.continuity.api.entities.links.ExternalDataLinkType;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.report.OrderReport;
import org.continuity.api.entities.report.OrderResponse;
import org.continuity.experimentation.Experiment;
import org.continuity.experimentation.action.Clock;
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataBuffer;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.EmailReport;
import org.continuity.experimentation.action.LocalFile;
import org.continuity.experimentation.action.OpenXtrace;
import org.continuity.experimentation.action.PrometheusDataExporter;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.GetJmeterReport;
import org.continuity.experimentation.action.continuity.JMeterTestplan;
import org.continuity.experimentation.action.continuity.OrderSubmission;
import org.continuity.experimentation.action.continuity.UploadAnnotation;
import org.continuity.experimentation.action.continuity.UploadApplicationModel;
import org.continuity.experimentation.action.continuity.WaitForOrderReport;
import org.continuity.experimentation.builder.ConcurrentBuilder;
import org.continuity.experimentation.builder.ExperimentBuilder;
import org.continuity.experimentation.builder.LoopBuilder;
import org.continuity.experimentation.builder.StableExperimentBuilder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.NoopDataHolder;
import org.continuity.experimentation.data.ProcessingDataHolder;
import org.continuity.experimentation.data.SequentialListDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;

public class ModularizationExperiment {

	private static final String CONTEXT_INITIAL_UPLOADS = "1-initial-uploads";
	private static final String CONTEXT_REFERENCE_LOADTEST = "2-reference-load-test";
	private static final String CONTEXT_SESSION_LOGS_CREATION = "3-session-logs-creation";
	private static final String CONTEXT_MODULARIZED_LOADTESTS = "4-modularized-load-tests";

	private final ExperimentProperties properties;
	private final List<TestExecution> testExecutions;
	private final Experiment experiment;

	private IDataHolder<LinkExchangeModel> referenceTestLinks = new SimpleDataHolder<>("reference-test-links", LinkExchangeModel.class);
	private IDataHolder<Date> testStartDate = new SimpleDataHolder<>("test-start-date", Date.class);
	private IDataHolder<Date> testEndDate = new SimpleDataHolder<>("test-end-date", Date.class);
	private IDataHolder<OrderReport> orderReport = new SimpleDataHolder<>("order-report", OrderReport.class);
	private IDataHolder<OrderReport> referenceExecutionReport = new SimpleDataHolder<>("reference-execution-report", OrderReport.class);
	private IDataHolder<String> referenceTracesLink = new SimpleDataHolder<>("reference-open-xtraces-link", String.class);

	public ModularizationExperiment(ExperimentProperties properties, List<TestExecution> testExecutions) throws MalformedURLException {
		this.properties = properties;
		this.testExecutions = testExecutions;
		this.experiment = createExperiment();
	}

	public Experiment getExperiment() {
		return experiment;
	}

	private Experiment createExperiment() throws MalformedURLException {
		StableExperimentBuilder builder = Experiment.newExperiment("exp-modularization");

		builder = appendInitialUploads(builder);
		builder = appendReferenceLoadTest(builder);
		builder = appendModularizedTests(builder);

		return builder.build();
	}

	/**
	 * Uploads all used artifacts to ContinuITy
	 *
	 * @param builder
	 */
	private StableExperimentBuilder appendInitialUploads(StableExperimentBuilder builder) {
		ContextChange context = new ContextChange(CONTEXT_INITIAL_UPLOADS);

		builder = builder.append(context.append());

		IDataHolder<JMeterTestPlanBundle> testPlanBundle = new SimpleDataHolder<>("JmeterTestplanBundle", JMeterTestPlanBundle.class);

		builder = builder.append(JMeterTestplan.read(StaticDataHolder.of(Paths.get(properties.getReferenceLoadTestFilePath())), testPlanBundle))
				.append(JMeterTestplan.upload(properties.getOrchestratorHost(), properties.getOrchestratorPort(), testPlanBundle, StaticDataHolder.of(properties.getTag()), referenceTestLinks));

		String[] idpaElements = Paths.get(properties.getIdpaFilePath()).toFile().list();
		List<Path> appPaths = Arrays.stream(idpaElements).filter(s -> s.startsWith("application-") && s.endsWith(".yml")).map(file -> Paths.get(properties.getIdpaFilePath(), file))
				.collect(Collectors.toList());
		List<Path> annPaths = Arrays.stream(idpaElements).filter(s -> s.startsWith("annotation-") && s.endsWith(".yml")).map(file -> Paths.get(properties.getIdpaFilePath(), file))
				.collect(Collectors.toList());
		SequentialListDataHolder<Path> appHolder = new SequentialListDataHolder<>("idpa-application", appPaths);
		SequentialListDataHolder<Path> annHolder = new SequentialListDataHolder<>("idpa-annotation", annPaths);

		IDataHolder<String> appTag = new ProcessingDataHolder<>("idpa-app-tag", appHolder, this::extractTagFromIdpaPath);
		ContextChange appContext = new ContextChange(appTag);
		IDataHolder<String> annTag = new ProcessingDataHolder<>("idpa-ann-tag", annHolder, this::extractTagFromIdpaPath);
		ContextChange annContext = new ContextChange(annTag);

		builder = builder.loop(appPaths.size()) //
				.append(appContext.rename()) //
				.append(UploadApplicationModel.from(appHolder, appTag).to(properties.getOrchestratorHost(), properties.getOrchestratorPort(), NoopDataHolder.instance())) //
				.append(appHolder::next) //
				.append(appContext.renameBack()) //
				.endLoop() //
				.loop(annPaths.size()) //
				.append(annContext.rename()) //
				.append(UploadAnnotation.from(annHolder, annTag).to(properties.getOrchestratorHost(), properties.getOrchestratorPort(), NoopDataHolder.instance())) //
				.append(annHolder::next) //
				.append(annContext.renameBack()) //
				.endLoop();

		builder.append(context.remove());

		return builder;
	}

	private String extractTagFromIdpaPath(Path path) {
		String file = path.getFileName().toString();

		return file.substring(file.indexOf("-") + 1, file.lastIndexOf("."));
	}

	/**
	 * Executes the reference load test and collects the traces
	 *
	 * @param builder
	 * @throws MalformedURLException
	 * @throws AbortInnerException
	 */
	private StableExperimentBuilder appendReferenceLoadTest(StableExperimentBuilder builder) throws MalformedURLException {
		ContextChange context = new ContextChange(CONTEXT_REFERENCE_LOADTEST);

		builder = builder.append(context.append()).append(EmailReport.send());

		IDataHolder<String> traceHolder = new SimpleDataHolder<>("open-xtraces", String.class);

		Order order;

		if (!properties.omitReferenceTest()) {
			order = createOrder(OrderGoal.EXECUTE_LOAD_TEST);
			order.getOptions().setNumUsers(properties.getLoadTestNumUsers());

			builder = appendSystemRestart(builder);
			builder = appendTestExecution(builder, order, referenceTestLinks) //
					.append(OpenXtrace.download(properties.getExternalTraceSourceLink(), testStartDate, testEndDate, properties.getDateFormat(), traceHolder));
		} else {
			builder = builder.append(LocalFile.read(StaticDataHolder.of(Paths.get("reference-traces.json")), traceHolder));
			testEndDate.set(new Date()); // Will be used as data timestamp
		}

		builder = builder.append(context.remove());
		context = new ContextChange(CONTEXT_SESSION_LOGS_CREATION);
		builder = builder.append(context.append());

		IDataHolder<LinkExchangeModel> sessionLogsSource = new ProcessingDataHolder<>("session-logs-source", referenceTracesLink, link -> {
			LinkExchangeModel source = new LinkExchangeModel();
			source.getMeasurementDataLinks().setLink(link);
			source.getMeasurementDataLinks().setLinkType(ExternalDataLinkType.OPEN_XTRACE);
			try {
				source.getMeasurementDataLinks().setTimestamp(testEndDate.get());
			} catch (AbortInnerException e) {
				e.printStackTrace();
				source.getMeasurementDataLinks().setTimestamp(new Date());
			}
			return source;
		});

		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("session-logs-creation-order-response", OrderResponse.class);
		order = createOrder(OrderGoal.CREATE_SESSION_LOGS);

		builder = builder.append(DataBuffer.upload(properties.getOrchestratorSatelliteHost(), traceHolder, referenceTracesLink)) //
				.append(new DataInvalidation(traceHolder)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), order, orderResponse, sessionLogsSource)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), StaticDataHolder.of(order), orderResponse, referenceExecutionReport,
						properties.getOrderReportTimeout()));

		builder = builder.append(context.remove());

		return builder;
	}

	/**
	 * Executes all modularized load tests.
	 *
	 * @param builder
	 */
	private StableExperimentBuilder appendModularizedTests(StableExperimentBuilder builder) {
		ContextChange context = new ContextChange(CONTEXT_MODULARIZED_LOADTESTS);
		builder = builder.append(context.append());

		final SequentialListDataHolder<TestExecution> testExecutionsHolder = new SequentialListDataHolder<>("test-execution", testExecutions);
		IDataHolder<Order> orderHolder = new SimpleDataHolder<>("load-test-creation-order", Order.class);
		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("test-creation-order-response", OrderResponse.class);
		IDataHolder<String> innerContextHolder = new ProcessingDataHolder<>("modularized-test-execution-context", testExecutionsHolder, TestExecution::toContext);
		IDataHolder<LinkExchangeModel> createdTestLinks = new ProcessingDataHolder<>("created-test-links", orderReport, OrderReport::getInternalArtifacts);
		ContextChange innerContext = new ContextChange(innerContextHolder);
		ContextChange creationContext = new ContextChange("load-test-creation");
		ContextChange executionContext = new ContextChange("load-test-creation");

		ConcurrentBuilder<LoopBuilder<StableExperimentBuilder>> threadBuilder = builder.loop(testExecutions.size()) //
				.append(innerContext.rename()).append(EmailReport.send()) //
				.newThread().append(ctxt -> initLoadTestCreationOrder(testExecutionsHolder, orderHolder)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse, orderReport, properties.getOrderReportTimeout())) //
				.newThread(); //

		LoopBuilder<StableExperimentBuilder> loopBuilder = appendSystemRestart(threadBuilder) //
				.join() //
				.append(new DataInvalidation(testStartDate, testEndDate));

		builder = appendTestExecution(loopBuilder, createOrder(OrderGoal.EXECUTE_LOAD_TEST), createdTestLinks) //
				.append(new DataInvalidation(orderHolder, orderResponse, orderReport)) //
				.append(innerContext.renameBack()) //
				.append(testExecutionsHolder::next) //
				.endLoop();

		builder.append(context.remove());

		return builder;
	}

	/**
	 * Creates the order for the load test creation and stores it in the data holder.
	 *
	 * @param testExecutionsHolder
	 * @param orderHolder
	 * @throws AbortInnerException
	 */
	private void initLoadTestCreationOrder(IDataHolder<TestExecution> testExecutionsHolder, IDataHolder<Order> orderHolder) throws AbortInnerException {
		Order order = createOrder(OrderGoal.CREATE_LOAD_TEST);

		TestExecution exec = testExecutionsHolder.get();

		if (exec.isModularized()) {
			ModularizationOptions modularizationOptions = new ModularizationOptions();
			modularizationOptions.setModularizationApproach(exec.getModularizationApproach());
			modularizationOptions.setServices(createServicesUnderTest(exec.getServicesUnderTest()));
			order.setModularizationOptions(modularizationOptions);
		}

		LinkExchangeModel linkExchangeModel = new LinkExchangeModel();

		linkExchangeModel.getMeasurementDataLinks().setLink(referenceTracesLink.get());
		linkExchangeModel.getMeasurementDataLinks().setLinkType(ExternalDataLinkType.OPEN_XTRACE);
		linkExchangeModel.getMeasurementDataLinks().setTimestamp(testEndDate.get());

		if (exec.getModularizationApproach() != ModularizationApproach.SESSION_LOGS) {
			// Already created session logs can be reused
			linkExchangeModel.getSessionLogsLinks().setLink(referenceExecutionReport.get().getInternalArtifacts().getSessionLogsLinks().getLink());
		}

		order.setSource(linkExchangeModel);

		orderHolder.set(order);
	}

	/**
	 * Rebuilds a map of service tags and the corresponding service hostname Assumes, that the
	 * models of the certain services are named as {APPLICATION_TAG}-{HOSTNAME}
	 *
	 * @param testCombination
	 *            a list of the services under test
	 * @return a map of service tags and the corresponding service hostname
	 */
	private HashMap<String, String> createServicesUnderTest(List<String> testCombination) {
		HashMap<String, String> servicesUnderTest = new HashMap<String, String>();
		for (String service : testCombination) {
			servicesUnderTest.put(properties.getTag() + "-" + service, service);
		}
		return servicesUnderTest;

	}

	/**
	 * Restarts the Sock Shop and waits for the defined delay.
	 *
	 * @param builder
	 * @return
	 */
	private <B extends ExperimentBuilder<B, C>, C> B appendSystemRestart(B builder) {
		if (!properties.omitSutRestart()) {
			return builder.append(TargetSystem.restart(Application.SOCK_SHOP, properties.getSutSatelliteHost())) //
					.append(new Delay(300000)).append(TargetSystem.waitFor(Application.SOCK_SHOP, properties.getTargetServerHost(), properties.getTargetServerPort(), 1800000))
					.append(new Delay(properties.getDelayBetweenExecutions()));
		} else {
			return builder.append(new Delay(1));
		}
	}

	/**
	 * Executes the load test defined by the order
	 *
	 * @param builder
	 * @param order
	 * @return
	 */
	private <B extends ExperimentBuilder<B, C>, C> B appendTestExecution(B builder, Order order, IDataHolder<LinkExchangeModel> source) {
		List<String> metrics = Arrays.asList("request_duration_seconds_count", "process_resident_memory_bytes", "process_cpu_seconds_total", "process_cpu_usage", "jvm_memory_used_bytes");
		List<String> allServicesToMonitor = Arrays.asList("shipping", "payment", "user", "cart", "orders", "catalogue", "frontend", "jmeter");

		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("test-execution-order-response", OrderResponse.class);

		ContextChange prometheusContext = new ContextChange("prometheus");

		// TODO: Do we need to subtract an hour from the start and end time?
		return builder.append(Clock.takeTime(testStartDate)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), order, orderResponse, source)) //
				.append(new Delay(properties.getLoadTestDuration() * 1000)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), StaticDataHolder.of(order), orderResponse, orderReport,
						properties.getOrderReportTimeout())) //
				.append(Clock.takeTime(testEndDate)) //
				.append(prometheusContext.append()) //
				.append(new PrometheusDataExporter(metrics, properties.getPrometheusHost(), properties.getPrometheusPort(), properties.getOrchestratorHost(), properties.getOrchestratorPort(),
						allServicesToMonitor, properties.getLoadTestDuration())) //
				.append(prometheusContext.remove()) //
				.append(new GetJmeterReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderReport));
	}

	private Order createOrder(OrderGoal goal) {
		Order order = new Order();

		order.setTag(properties.getTag());
		order.setGoal(goal);
		order.setMode(OrderMode.PAST_SESSIONS);

		OrderOptions orderOptions = new OrderOptions();
		orderOptions.setDuration(properties.getLoadTestDuration());
		orderOptions.setLoadTestType(LoadTestType.JMETER);
		orderOptions.setWorkloadModelType(WorkloadModelType.WESSBAS);
		orderOptions.setRampup(properties.getLoadTestRampup());
		order.setOptions(orderOptions);

		return order;
	}

}
