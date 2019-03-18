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
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.links.MeasurementDataLinkType;
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
import org.continuity.experimentation.action.NoopAction;
import org.continuity.experimentation.action.OpenXtrace;
import org.continuity.experimentation.action.PrometheusDataExporter;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
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
import org.continuity.experimentation.data.SequentialListDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;

public class ModularizationExperiment {

	private static final String CONTEXT_INITIAL_UPLOADS = "1-initial-uploads";
	private static final String CONTEXT_REFERENCE_LOADTEST = "2-reference-load-test";
	private static final String CONTEXT_SESSION_LOGS_CREATION = "3-session-logs-creation";
	private static final String CONTEXT_MODULARIZED_LOADTESTS = "4-modularized-load-tests";
	private static final String CONTEXT_NON_MODULARIZED_SESSION_LOGS = "5-non-modularized-session-logs";

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
		builder = appendNonModularizedSessionLogsCreation(builder);
		builder = builder.append(EmailReport.send());

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

		IDataHolder<String> appTag = appHolder.processing("idpa-app-tag", this::extractTagFromIdpaPath);
		ContextChange appContext = new ContextChange(appTag);
		IDataHolder<String> annTag = annHolder.processing("idpa-ann-tag", this::extractTagFromIdpaPath);
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

			ConcurrentBuilder<StableExperimentBuilder> threadBuilder = builder.newThread();

			threadBuilder = appendMonitoringRestart(threadBuilder);
			threadBuilder = appendSystemRestart(threadBuilder);

			threadBuilder = threadBuilder.newThread();

			builder = appendJMeterRestart(threadBuilder).join();

			builder = appendTestExecution(builder, order, referenceTestLinks, traceHolder);
		} else {
			builder = builder.append(LocalFile.read(StaticDataHolder.of(Paths.get("reference-traces.json")), traceHolder));
			testEndDate.set(new Date()); // Will be used as data timestamp
		}

		builder = builder.append(context.remove());
		context = new ContextChange(CONTEXT_SESSION_LOGS_CREATION);
		builder = builder.append(context.append());

		IDataHolder<LinkExchangeModel> sessionLogsSource = referenceTracesLink.processing("session-logs-source", link -> {
			LinkExchangeModel source = new LinkExchangeModel();
			source.getMeasurementDataLinks().setLink(link);
			source.getMeasurementDataLinks().setLinkType(MeasurementDataLinkType.OPEN_XTRACE);
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
	 * @throws MalformedURLException
	 */
	private StableExperimentBuilder appendModularizedTests(StableExperimentBuilder builder) throws MalformedURLException {
		ContextChange context = new ContextChange(CONTEXT_MODULARIZED_LOADTESTS);
		builder = builder.append(context.append());

		final SequentialListDataHolder<TestExecution> testExecutionsHolder = new SequentialListDataHolder<>("test-execution", testExecutions);
		IDataHolder<Order> orderHolder = new SimpleDataHolder<>("load-test-creation-order", Order.class);
		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("test-creation-order-response", OrderResponse.class);
		ContextChange innerContext = new ContextChange(testExecutionsHolder.processing("modularized-test-execution-context", TestExecution::toContext));
		ContextChange creationContext = new ContextChange("test-creation");
		ContextChange executionContext = new ContextChange("test-execution");

		ConcurrentBuilder<LoopBuilder<StableExperimentBuilder>> threadBuilder = builder.loop(testExecutions.size()) //
				.append(innerContext.rename()).append(EmailReport.send()) //
				.append(creationContext.append()) //
				.newThread().append(ctxt -> initLoadTestCreationOrder(testExecutionsHolder, orderHolder)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse, orderReport, properties.getOrderReportTimeout())) //
				.newThread(); //

		threadBuilder = appendMonitoringRestart(threadBuilder);
		threadBuilder = appendSystemRestart(threadBuilder);

		threadBuilder = threadBuilder.newThread();

		LoopBuilder<StableExperimentBuilder> loopBuilder = appendJMeterRestart(threadBuilder) //
				.join() //
				.append(new DataInvalidation(testStartDate, testEndDate)) //
				.append(creationContext.remove()) //
				.append(executionContext.append());

		builder = appendTestExecution(loopBuilder, createOrder(OrderGoal.EXECUTE_LOAD_TEST), orderReport.processing("created-test-links", OrderReport::getInternalArtifacts), NoopDataHolder.instance()) //
				.append(executionContext.remove()) //
				.append(new DataInvalidation(orderHolder, orderResponse, orderReport)) //
				.append(innerContext.renameBack()) //
				.append(testExecutionsHolder::next) //
				.endLoop();

		builder = builder.append(context.remove());

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

			if (exec.getModularizationApproach() == ModularizationApproach.REQUESTS) {
				order.setMode(OrderMode.PAST_REQUESTS);
				order.getOptions().setWorkloadModelType(WorkloadModelType.REQUEST_RATES);
			}
		}

		LinkExchangeModel linkExchangeModel = new LinkExchangeModel();

		linkExchangeModel.getMeasurementDataLinks().setLink(referenceTracesLink.get());
		linkExchangeModel.getMeasurementDataLinks().setLinkType(MeasurementDataLinkType.OPEN_XTRACE);
		linkExchangeModel.getMeasurementDataLinks().setTimestamp(testEndDate.get());

		if (exec.getModularizationApproach() == ModularizationApproach.WORKLOAD_MODEL) {
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
			return builder.append(TargetSystem.restart(Application.SOCK_SHOP_PINNED, properties.getSutSatelliteHost())) //
					.append(new Delay(300000)).append(TargetSystem.waitFor(Application.SOCK_SHOP_PINNED, properties.getTargetServerHost(), properties.getTargetServerPort(), 1800000))
					.append(new Delay(properties.getDelayBetweenExecutions()));
		} else {
			return builder.append(NoopAction.INSTANCE);
		}
	}

	/**
	 * Restarts the CMR and waits for the defined delay.
	 *
	 * @param builder
	 * @return
	 */
	private <B extends ExperimentBuilder<B, C>, C> B appendMonitoringRestart(B builder) {
		if (!properties.omitSutRestart()) {
			return builder.append(TargetSystem.restart(Application.MONITORING, properties.getOrchestratorSatelliteHost())) //
					.append(new Delay(300000)).append(TargetSystem.waitFor(Application.MONITORING, properties.getOrchestratorHost(), "8182", 1800000)).append(new Delay(120000));
		} else {
			return builder.append(NoopAction.INSTANCE);
		}
	}

	/**
	 * Restarts the continuity.jmeter service and waits for the defined delay.
	 *
	 * @param builder
	 * @return
	 */
	private <B extends ExperimentBuilder<B, C>, C> B appendJMeterRestart(B builder) {
		if (!properties.omitSutRestart()) {
			return builder.append(TargetSystem.restart(Application.CONTINUITY_JMETER, properties.getOrchestratorSatelliteHost())) //
					.append(new Delay(300000)).append(TargetSystem.waitFor(Application.CONTINUITY_JMETER, properties.getOrchestratorHost(), "8083", 1800000))
					.append(new Delay(properties.getDelayBetweenExecutions()));
		} else {
			return builder.append(NoopAction.INSTANCE);
		}
	}

	/**
	 * Executes the load test defined by the order
	 *
	 * @param builder
	 * @param order
	 * @return
	 * @throws MalformedURLException
	 */
	private <B extends ExperimentBuilder<B, C>, C> B appendTestExecution(B builder, Order order, IDataHolder<LinkExchangeModel> source, IDataHolder<String> traceHolder) throws MalformedURLException {
		List<String> metrics = Arrays.asList("request_duration_seconds_count", "process_resident_memory_bytes", "process_cpu_seconds_total", "process_cpu_usage", "jvm_memory_used_bytes");
		List<String> allServicesToMonitor = Arrays.asList("shipping", "payment", "user", "cart", "orders", "catalogue", "frontend", "jmeter");

		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("test-execution-order-response", OrderResponse.class);

		ContextChange prometheusContext = new ContextChange("prometheus");

		// We need to subtract an hour from the start and end time due to different clock of Docker
		// containers
		return builder.append(Clock.takeTime(testStartDate, -1, 2, 0)) // skip the first two minutes
																		// (warm up)
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), order, orderResponse, source)) //
				.append(new Delay(properties.getLoadTestDuration() * 1000)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), StaticDataHolder.of(order), orderResponse, orderReport,
						properties.getOrderReportTimeout())) //
				.append(Clock.takeTime(testEndDate, -1, -2, 0)) // skip the last two minutes (cool
																// down)
				.append(prometheusContext.append()) //
				.append(new PrometheusDataExporter(metrics, properties.getPrometheusHost(), properties.getPrometheusPort(), properties.getOrchestratorHost(), properties.getOrchestratorPort(),
						allServicesToMonitor, properties.getLoadTestDuration())) //
				.append(prometheusContext.remove()) //
				.append(OpenXtrace.download(properties.getExternalTraceSourceLink(), testStartDate, testEndDate, properties.getDateFormat(), traceHolder));
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

	private StableExperimentBuilder appendNonModularizedSessionLogsCreation(StableExperimentBuilder builder) {
		ContextChange context = new ContextChange(CONTEXT_NON_MODULARIZED_SESSION_LOGS);

		builder = builder.append(context.append()).append(EmailReport.send());

		IDataHolder<Path> pathHolder = new SimpleDataHolder<>("non-modularized-open-xtraces-path", Path.class);
		IDataHolder<String> traceHolder = new SimpleDataHolder<>("non-modularized-open-xtraces", String.class);
		IDataHolder<String> traceLinkHolder = new SimpleDataHolder<>("non-modularized-open-xtraces-link", String.class);

		IDataHolder<LinkExchangeModel> sessionLogsSource = traceLinkHolder.processing("non-modularized-session-logs-source", link -> {
			LinkExchangeModel source = new LinkExchangeModel();
			source.getMeasurementDataLinks().setLink(link);
			source.getMeasurementDataLinks().setLinkType(MeasurementDataLinkType.OPEN_XTRACE);
			source.getMeasurementDataLinks().setTimestamp(new Date());
			return source;
		});

		List<TestExecution> serviceCombinations = testExecutions.stream().filter(exec -> exec.getModularizationApproach() != null).map(TestExecution::getServicesUnderTest).distinct()
				.map(l -> new TestExecution(ModularizationApproach.SESSION_LOGS, l.toArray(new String[] {}))).collect(Collectors.toList());

		SequentialListDataHolder<TestExecution> serviceCombinationHolder = new SequentialListDataHolder<>("service-combinations", serviceCombinations);
		ContextChange serviceCombinationContext = new ContextChange(
				serviceCombinationHolder.processing("service-combination-context", exec -> exec.getServicesUnderTest().stream().collect(Collectors.joining("-"))));
		IDataHolder<Order> orderHolder = new SimpleDataHolder<>("non-modularized-session-logs-order", Order.class);

		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("non-modularized-session-logs-creation-order-response", OrderResponse.class);

		builder = builder.append(ctxt -> {
			Path traceFile = ctxt.toPath().resolve(CONTEXT_MODULARIZED_LOADTESTS).resolve("non-modularized").resolve("test-execution").resolve("open-xtraces.json");
			pathHolder.set(traceFile);
		}) //
				.append(LocalFile.read(pathHolder, traceHolder)) //
				.append(DataBuffer.upload(properties.getOrchestratorSatelliteHost(), traceHolder, traceLinkHolder)) //

				.loop(serviceCombinations.size()) //
				.append(serviceCombinationContext.rename()) //
				.append(ctxt -> {
					initLoadTestCreationOrder(serviceCombinationHolder, orderHolder);
					orderHolder.get().setGoal(OrderGoal.CREATE_SESSION_LOGS);
				}) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse, sessionLogsSource)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse, NoopDataHolder.instance(),
						properties.getOrderReportTimeout())) //
				.append(serviceCombinationContext.renameBack()) //
				.append(serviceCombinationHolder::next) //
				.append(new DataInvalidation(orderHolder, orderResponse)) //
				.endLoop();

		builder = builder.append(context.remove());

		return builder;
	}

}
