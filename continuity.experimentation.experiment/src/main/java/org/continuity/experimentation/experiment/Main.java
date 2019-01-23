package org.continuity.experimentation.experiment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.jmeter.util.JMeterUtils;
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
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AppendContext;
import org.continuity.experimentation.action.DataInvalidation;
import org.continuity.experimentation.action.Delay;
import org.continuity.experimentation.action.RemoveContext;
import org.continuity.experimentation.action.TargetSystem;
import org.continuity.experimentation.action.TargetSystem.Application;
import org.continuity.experimentation.action.continuity.GetJmeterReport;
import org.continuity.experimentation.action.continuity.GetSessionLogsOfExecutedLoadTest;
import org.continuity.experimentation.action.continuity.JMeterTestplan;
import org.continuity.experimentation.action.continuity.OrderSubmission;
import org.continuity.experimentation.action.continuity.PrometheusDataExporter;
import org.continuity.experimentation.action.continuity.WaitForOrderReport;
import org.continuity.experimentation.builder.ExperimentBuilderImpl;
import org.continuity.experimentation.builder.StableExperimentBuilder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import net.sf.markov4jmeter.testplangenerator.JMeterEngineGateway;
/**
 * @author Tobias Angerstein
 *
 */
public class Main {

	private static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static int ORDER_REPORT_TIMEOUT;
	private static String EXTERNAL_TRACE_SOURCE_LINK;
	private static String ORCHESTRATOR_HOST;
	private static String ORCHESTRATOR_PORT;
	private static String REFERENCE_LOAD_TEST_FILE_PATH;
	private static String PROMETHEUS_HOST;
	private static String PROMETHEUS_PORT;
	private static long LOAD_TEST_DURATION;
	private static int LOAD_TEST_NUM_USER;
	private static int LOAD_TEST_RAMPUP;
	private static long DELAY_BETWEEN_EXECUTIONS;
	private static String TARGET_SERVER_HOST;
	private static String TARGET_SERVER_PORT;
	private static String SATELLITE_HOST;

	private static final String TAG = "sock-shop";
	private static final String DATE_FORMAT = "yyyy/MM/dd/HH:mm:ss";

	private static String TRACE_DATA_START_TIME;
	private static String TRACE_DATA_END_TIME;

	private static List<TestExecution> testExecutions;
	private static IDataHolder<String> referenceSessionLogsLink = new SimpleDataHolder<>("referenceSessionLogsLink", String.class);

	static {

		try {
			loadProperties();
			setupTestExecutions();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void initJMeter() {
		JMeterEngineGateway.getInstance().initJMeter("jmeter", "bin/jmeter.properties", Locale.ENGLISH);
		JMeterUtils.initLogging();
	}

	private static List<TestExecution> setupTestExecutions() {
		Main main = new Main();
		String testExecutionsCSV = "";
		try {
			testExecutionsCSV = FileUtils.readFileToString(new File("test-executions.csv"), "UTF-8");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<TestExecution> testExecutions = new ArrayList<TestExecution>();

		for(String row : testExecutionsCSV.split("\n")) {
			String modularizationApproach = row.split(",")[0];
			String[] servicesUnderTest = Arrays.copyOfRange(row.split(","), 1, row.split(",").length);
			testExecutions.add(main.new TestExecution(ModularizationApproach.fromPrettyString(modularizationApproach), servicesUnderTest));
			LOGGER.info("Added test case {}: {}", modularizationApproach, servicesUnderTest);
		}

		return testExecutions;
	}

	public static void main(String[] args) throws Exception {
		initJMeter();

		// Initialize dataholders
		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<OrderResponse>("orderResponse", OrderResponse.class);
		IDataHolder<OrderReport> orderReport = new SimpleDataHolder<OrderReport>("orderReport", OrderReport.class);
		IDataHolder<JMeterTestPlanBundle> testPlanBundle = new SimpleDataHolder<>("JmeterTestplanBundle", JMeterTestPlanBundle.class);
		IDataHolder<LinkExchangeModel> source = new SimpleDataHolder<>("source", LinkExchangeModel.class);

		// !!! expects a folder containing a .jmx - not the .json anymore !!!
		JMeterTestplan.read(StaticDataHolder.of(Paths.get(REFERENCE_LOAD_TEST_FILE_PATH)), testPlanBundle).execute(new Context());

		// Actions
		WaitForOrderReport waitForOrderReport = new WaitForOrderReport(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, orderResponse, orderReport, ORDER_REPORT_TIMEOUT);

		List<String> metrics = Arrays.asList("request_duration_seconds_count", "process_resident_memory_bytes", "process_cpu_seconds_total", "process_cpu_usage", "jvm_memory_used_bytes");
		List<String> allServicesToMonitor = Arrays.asList("shipping", "payment", "user", "cart", "orders", "catalogue", "frontend", "jmeter");
		PrometheusDataExporter prometheusDataExporter = new PrometheusDataExporter(metrics, PROMETHEUS_HOST, PROMETHEUS_PORT, ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, allServicesToMonitor,
				LOAD_TEST_DURATION, orderReport);

		GetJmeterReport getJmeterReport = new GetJmeterReport(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, orderReport);
		GetSessionLogsOfExecutedLoadTest getSessionLogs = new GetSessionLogsOfExecutedLoadTest(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, TAG, orderReport, EXTERNAL_TRACE_SOURCE_LINK, ORDER_REPORT_TIMEOUT,
				referenceSessionLogsLink);

		LOGGER.info("Starting creating load tests");

		// run reference load test
		runReferenceLoadTest(orderResponse, testPlanBundle, waitForOrderReport, prometheusDataExporter, getJmeterReport, getSessionLogs);

		// Create load test
		for (TestExecution testExecution : testExecutions) {
			if (testExecution.loadTestLink == null) {
				new ExperimentBuilderImpl("create-load-tests").append(new AppendContext(testExecution.getCurrentContext()))
						.append(new OrderSubmission(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, testExecution.getOrderCreateLoadTest(), orderResponse)).append(waitForOrderReport)
						.append(new RemoveContext(testExecution.getCurrentContext())).build().execute();
				LOGGER.info("The load test is ready: {} ", orderReport.get().getCreatedArtifacts().getLoadTestLinks().getLink());
				testExecution.setLoadTestLink(UriComponentsBuilder.fromHttpUrl(orderReport.get().getCreatedArtifacts().getLoadTestLinks().getLink()).host("orchestrator").port(80).build().toString());
			} else {
				LOGGER.info("Load test link is already provided for {}", testExecution.getCurrentContext());
			}
		}

		LOGGER.info("Finished creating load tests");



		executeLoadTests(orderResponse, orderReport, source, waitForOrderReport, prometheusDataExporter, getJmeterReport, getSessionLogs);

		LOGGER.info("Successfully finished experiment");
	}

	/**
	 * Executes the created load tests
	 * @param orderResponse
	 * @param orderReport
	 * @param source
	 * @param waitForOrderReport
	 * @param prometheusDataExporter
	 * @param getJmeterReport
	 * @param getSessionLogs
	 * @throws AbortInnerException
	 * @throws AbortException
	 */
	private static void executeLoadTests(IDataHolder<OrderResponse> orderResponse, IDataHolder<OrderReport> orderReport, IDataHolder<LinkExchangeModel> source, WaitForOrderReport waitForOrderReport,
			PrometheusDataExporter prometheusDataExporter, GetJmeterReport getJmeterReport, GetSessionLogsOfExecutedLoadTest getSessionLogs)
			throws AbortInnerException, AbortException {
		LOGGER.info("Started executing load tests");
		StableExperimentBuilder executeLoadTestsExperimentBuilder = new ExperimentBuilderImpl("execute-load-tests");
		// Execute Generated Load Tests
		for (TestExecution testExecution : testExecutions) {
			executeLoadTestsExperimentBuilder.append(TargetSystem.restart(Application.CMR_DOCKER_SWARM, SATELLITE_HOST)).append(new Delay(300000))
					.append(TargetSystem.restart(Application.SOCK_SHOP, SATELLITE_HOST)).append(TargetSystem.waitFor(Application.SOCK_SHOP, TARGET_SERVER_HOST, TARGET_SERVER_PORT, 1800000))
					.append(new Delay(DELAY_BETWEEN_EXECUTIONS)).append(new AppendContext(testExecution.getCurrentContext()))
					.append(new OrderSubmission(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, testExecution.getOrderExecuteLoadTest(), orderResponse, source)).append(new Delay(LOAD_TEST_DURATION * 1000))
					.append(waitForOrderReport).append(prometheusDataExporter).append(getJmeterReport).append(getSessionLogs).append(new RemoveContext(testExecution.getCurrentContext()))
					.append(new DataInvalidation(orderResponse, orderReport, source));
		}

		executeLoadTestsExperimentBuilder.build().execute();
		LOGGER.info("Finished executing load tests");
	}

	/**
	 * Runs reference load test
	 *
	 * @param orderResponse
	 * @param testPlanBundle
	 * @param source
	 * @param waitForOrderReport
	 * @param prometheusDataExporter
	 * @param getJmeterReport
	 */
	private static void runReferenceLoadTest(IDataHolder<OrderResponse> orderResponse, IDataHolder<JMeterTestPlanBundle> testPlanBundle, WaitForOrderReport waitForOrderReport,
			PrometheusDataExporter prometheusDataExporter, GetJmeterReport getJmeterReport, GetSessionLogsOfExecutedLoadTest getSessionLogs) {

		IDataHolder<LinkExchangeModel> source = new SimpleDataHolder<>("source", LinkExchangeModel.class);

		LOGGER.info("Starting reference load test at {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
		TRACE_DATA_START_TIME = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
		try {
			// Upload
			StableExperimentBuilder uploadLoadTestExperimentBuilder = new ExperimentBuilderImpl("reference_load_test_upload");
			uploadLoadTestExperimentBuilder.append(JMeterTestplan.upload(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, testPlanBundle, StaticDataHolder.of(TAG)));
			uploadLoadTestExperimentBuilder.build().execute();

			StableExperimentBuilder referenceLoadTestExecutionExperimentBuilder = new ExperimentBuilderImpl("reference_load_test_execution");

			// Execute
			referenceLoadTestExecutionExperimentBuilder
					.append(new OrderSubmission(ORCHESTRATOR_HOST, ORCHESTRATOR_PORT, generateLoadTestExecutionOrder(source.get(), LOAD_TEST_NUM_USER), orderResponse, source))
					.append(new Delay(LOAD_TEST_DURATION * 1000)).append(waitForOrderReport).append(prometheusDataExporter).append(getJmeterReport).append(getSessionLogs);

			LOGGER.info("Starting reference load test at {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
			TRACE_DATA_START_TIME = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
			referenceLoadTestExecutionExperimentBuilder.build().execute();
			TRACE_DATA_END_TIME = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
			LOGGER.info("Successfully finished reference load test at {}", TRACE_DATA_END_TIME);

		} catch (AbortException | AbortInnerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Rebuilds a map of service tags and the corresponding service hostname Assumes, that the
	 * models of the certain services are named as {APPLICATION_TAG}-{HOSTNAME}
	 *
	 * @param testCombination
	 *            a list of the services under test
	 * @return a map of service tags and the corresponding service hostname
	 */
	private static HashMap<String, String> createServicesUnderTest(List<String> testCombination) {
		HashMap<String, String> servicesUnderTest = new HashMap<String, String>();
		for (String service : testCombination) {
			servicesUnderTest.put(TAG + "-" + service, service);
		}
		return servicesUnderTest;

	}

	/**
	 * This order CREATES modularized load test against the given services.
	 *
	 * @param servicesUnderTest
	 * @param modularizationApproach
	 * @return
	 */
	private static Order generateModularizedLoadTestOrder(HashMap<String, String> servicesUnderTest, ModularizationApproach modularizationApproach) {
		Order order = generateLoadTestCreationOrder();
		ModularizationOptions modularizationOptions = new ModularizationOptions();
		modularizationOptions.setModularizationApproach(modularizationApproach);
		modularizationOptions.setServices(servicesUnderTest);
		OrderOptions orderOptions = new OrderOptions();
		orderOptions.setDuration(LOAD_TEST_DURATION);
		orderOptions.setLoadTestType(LoadTestType.JMETER);
		orderOptions.setWorkloadModelType(WorkloadModelType.WESSBAS);
		orderOptions.setRampup(LOAD_TEST_RAMPUP);
		order.setOptions(orderOptions);
		order.setModularizationOptions(modularizationOptions);
		if (modularizationApproach.equals(ModularizationApproach.WORKLOAD_MODEL)) {
			LOGGER.info("Reusing resulting session logs of reference load test");
			// Reference session logs can be reused
			try {
				order.getSource().getSessionLogsLinks().setLink(referenceSessionLogsLink.get());
			} catch (AbortInnerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return order;
	}

	/**
	 * This order CREATES a load test
	 *
	 * @return
	 */
	private static Order generateLoadTestCreationOrder() {
		Order order = new Order();
		order.setTag(TAG);
		order.setGoal(OrderGoal.CREATE_LOAD_TEST);
		order.setMode(OrderMode.PAST_SESSIONS);
		LinkExchangeModel linkExchangeModel = new LinkExchangeModel();
		linkExchangeModel.getMeasurementDataLinks().setLink(EXTERNAL_TRACE_SOURCE_LINK + "?fromDate=" + TRACE_DATA_START_TIME + "&toDate=" + TRACE_DATA_END_TIME);
		linkExchangeModel.getMeasurementDataLinks().setLinkType(ExternalDataLinkType.OPEN_XTRACE);
		linkExchangeModel.getMeasurementDataLinks().setTimestamp(Date.valueOf(LocalDate.now()));
		order.setSource(linkExchangeModel);
		OrderOptions orderOptions = new OrderOptions();
		orderOptions.setDuration(LOAD_TEST_DURATION);
		orderOptions.setLoadTestType(LoadTestType.JMETER);
		orderOptions.setWorkloadModelType(WorkloadModelType.WESSBAS);
		orderOptions.setRampup(LOAD_TEST_RAMPUP);
		order.setOptions(orderOptions);
		return order;
	}

	/**
	 * This order executes the given load test provided as link
	 *
	 * @param source
	 *            the link to the load test
	 * @return
	 */
	private static Order generateLoadTestExecutionOrder(LinkExchangeModel source, Integer numberOfUsers) {
		Order order = new Order();
		order.setTag(TAG);
		order.setGoal(OrderGoal.EXECUTE_LOAD_TEST);
		order.setMode(OrderMode.PAST_SESSIONS);
		order.setSource(source);
		OrderOptions orderOptions = new OrderOptions();
		orderOptions.setDuration(LOAD_TEST_DURATION);
		orderOptions.setLoadTestType(LoadTestType.JMETER);
		orderOptions.setWorkloadModelType(WorkloadModelType.WESSBAS);
		orderOptions.setRampup(LOAD_TEST_RAMPUP);
		if (null != numberOfUsers) {
			orderOptions.setNumUsers(numberOfUsers);
		}
		order.setOptions(orderOptions);
		return order;
	}

	/**
	 * Loads the configuration
	 *
	 * @throws IOException
	 */
	private static void loadProperties() throws IOException {
		InputStream fileInputStream = new FileInputStream("config.properties");
		Properties properties = new Properties();
		properties.load(fileInputStream);
		ORDER_REPORT_TIMEOUT = Integer.parseInt(properties.getProperty("order-report-timeout", "2000000"));
		EXTERNAL_TRACE_SOURCE_LINK = properties.getProperty("external-trace-source-link", "http://172.17.0.1:8182/rest/open-xtrace/get");
		ORCHESTRATOR_HOST = properties.getProperty("orchestrator-host", "172.20.0.6");
		ORCHESTRATOR_PORT = properties.getProperty("orchestrator-port", "80");
		REFERENCE_LOAD_TEST_FILE_PATH = properties.getProperty("reference-load-test-file-path", "/sock-shop-testplan");
		PROMETHEUS_HOST = properties.getProperty("prometheus-host", "localhost");
		PROMETHEUS_PORT = properties.getProperty("prometheus-port", "9090");
		LOAD_TEST_DURATION = Integer.parseInt(properties.getProperty("load-test-duration", "300"));
		LOAD_TEST_NUM_USER = Integer.parseInt(properties.getProperty("load-test-num-user", "40"));
		LOAD_TEST_RAMPUP = Integer.parseInt(properties.getProperty("load-test-rampup", "5"));
		DELAY_BETWEEN_EXECUTIONS = Integer.parseInt(properties.getProperty("delay-between-executions", "10"));
		TARGET_SERVER_HOST = properties.getProperty("target-server-host", "127.0.0.1");
		TARGET_SERVER_PORT = properties.getProperty("target-server-port", "80");
		SATELLITE_HOST = properties.getProperty("satellite-host", "127.0.0.1");
	}

	public class TestExecution {
		/**
		 * Services under test.
		 */
		private List<String> servicesUnderTest = new ArrayList<String>();

		/**
		 * ModularizationApproach.
		 */
		private ModularizationApproach modularizationApproach;

		private String loadTestLink = null;

		public TestExecution(ModularizationApproach modularizationApproach, String... service) {
			this.modularizationApproach = modularizationApproach;
			servicesUnderTest.addAll(Arrays.asList(service));
		}

		public TestExecution(ModularizationApproach modularizationApproach, Optional<String> loadTestLink, String... service) {
			this.modularizationApproach = modularizationApproach;
			servicesUnderTest.addAll(Arrays.asList(service));
			this.loadTestLink = loadTestLink.orElse(null);
		}

		/**
		 * Use, if non modularized load test is going to be tested.
		 */
		public TestExecution() {
			this.modularizationApproach = null;
			this.servicesUnderTest = Collections.emptyList();
		}

		public Order getOrderCreateLoadTest() {
			if (null != modularizationApproach) {
				return generateModularizedLoadTestOrder(createServicesUnderTest(servicesUnderTest), modularizationApproach);
			} else {
				LOGGER.info("Reusing resulting session logs of reference load test");
				Order order = generateLoadTestCreationOrder();
				try {
					order.getSource().getSessionLogsLinks().setLink(referenceSessionLogsLink.get());
				} catch (AbortInnerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return order;
			}
		}

		public Order getOrderExecuteLoadTest() {
			if (null != loadTestLink) {
				LinkExchangeModel source = new LinkExchangeModel();
				source.getLoadTestLinks().setLink(loadTestLink);
				source.getLoadTestLinks().setType(LoadTestType.JMETER);

				return generateLoadTestExecutionOrder(source, null);
			} else {
				throw new RuntimeException("Load test link is undefined! Please set the link before using this method");
			}
		}

		public void setLoadTestLink(String loadTestLink) {
			this.loadTestLink = loadTestLink;
		}

		public String getCurrentContext() {
			if (null != modularizationApproach) {
				return servicesUnderTest.get(0) + "/" + modularizationApproach.name();
			} else {
				return "non-modularized";
			}
		}

	}
}
