package org.continuity.experimentation.experiment;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
import org.continuity.experimentation.action.ContextChange;
import org.continuity.experimentation.action.DataBuffer;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.LocalFile;
import org.continuity.experimentation.action.continuity.OrderSubmission;
import org.continuity.experimentation.action.continuity.UploadAnnotation;
import org.continuity.experimentation.action.continuity.UploadApplicationModel;
import org.continuity.experimentation.action.continuity.WaitForOrderReport;
import org.continuity.experimentation.builder.StableExperimentBuilder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.NoopDataHolder;
import org.continuity.experimentation.data.SequentialListDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;

public class ModularizationExperiment {

	private static final String CONTEXT_INITIAL_UPLOADS = "1-initial-uploads";
	private static final String CONTEXT_SESSION_LOGS_CREATION = "3-session-logs-creation";
	private static final String CONTEXT_MODULARIZED_LOADTESTS = "4-modularized-load-tests";

	private final ExperimentProperties properties;
	private final List<TestExecution> testExecutions;
	private final Experiment experiment;

	private IDataHolder<Date> testEndDate = new SimpleDataHolder<>("test-end-date", Date.class);
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
		builder = appendSessionLogsCreation(builder);
		builder = appendModularizedTestGeneration(builder);

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
				.append(new Delay(10000)) //
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

	private StableExperimentBuilder appendSessionLogsCreation(StableExperimentBuilder builder) throws MalformedURLException {
		IDataHolder<String> traceHolder = new SimpleDataHolder<>("open-xtraces", String.class);

		builder = builder.append(LocalFile.read(StaticDataHolder.of(Paths.get("reference-traces.json")), traceHolder));
		testEndDate.set(new Date()); // Will be used as data timestamp

		ContextChange context = new ContextChange(CONTEXT_SESSION_LOGS_CREATION);
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
		Order order = createOrder(OrderGoal.CREATE_SESSION_LOGS);

		builder = builder.append(DataBuffer.upload(properties.getOrchestratorSatelliteHost(), traceHolder, referenceTracesLink)) //
				.append(new DataInvalidation(traceHolder)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), order, orderResponse, sessionLogsSource)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), StaticDataHolder.of(order), orderResponse, referenceExecutionReport,
						properties.getOrderReportTimeout()));

		builder = builder.append(context.remove());

		return builder;
	}

	private StableExperimentBuilder appendModularizedTestGeneration(StableExperimentBuilder builder) throws MalformedURLException {
		ContextChange context = new ContextChange(CONTEXT_MODULARIZED_LOADTESTS);
		builder = builder.append(context.append());

		final SequentialListDataHolder<TestExecution> testExecutionsHolder = new SequentialListDataHolder<>("test-execution", testExecutions);
		IDataHolder<Order> orderHolder = new SimpleDataHolder<>("load-test-creation-order", Order.class);
		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<>("test-creation-order-response", OrderResponse.class);
		IDataHolder<OrderReport> orderReport = new SimpleDataHolder<>("test-creation-order-report", OrderReport.class);
		ContextChange innerContext = new ContextChange(testExecutionsHolder.processing("modularized-test-execution-context", TestExecution::toContext));

		builder = builder.loop(testExecutions.size()) //
				.append(innerContext.rename()) //
				.append(ctxt -> initLoadTestCreationOrder(testExecutionsHolder, orderHolder)) //
				.append(new OrderSubmission(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse)) //
				.append(new WaitForOrderReport(properties.getOrchestratorHost(), properties.getOrchestratorPort(), orderHolder, orderResponse, orderReport, properties.getOrderReportTimeout())) //
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
