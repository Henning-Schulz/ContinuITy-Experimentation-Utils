package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

import org.continuity.api.entities.config.Order;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.links.LoadTestLinks;
import org.continuity.api.entities.links.SessionLogsLinks;
import org.continuity.api.entities.links.WorkloadModelLinks;
import org.continuity.api.entities.report.OrderReport;
import org.continuity.api.entities.report.OrderResponse;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.NoopDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;

public class WaitForOrderReport extends AbstractRestAction {

	/**
	 * Has to be provided in order to get the wait for order report link.
	 */
	private IDataHolder<OrderResponse> orderResponse;

	/**
	 * Retrieved response will be stored as {@link OrderReport}
	 */
	private IDataHolder<OrderReport> orderReport;

	/**
	 * Used to determine which artifacts have been newly created.
	 */
	private IDataHolder<Order> sentOrder;

	/**
	 * Timeout in Milliseconds
	 */
	private long timeout;

	/**
	 * File extension definition
	 */
	private static final String FILE_EXT = ".yml";

	/**
	 * File name of order report
	 */
	private static final String FILENAME = "order-report";

	/**
	 * Logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(WaitForOrderReport.class);

	/**
	 * Constructor
	 *
	 * @param host
	 *            host of the ContinuITy orchestrator
	 * @param port
	 *            port of the ContinuITy orchestrator
	 * @param sentOrder
	 *            sent order to wait for
	 * @param orderResponse
	 *            order response
	 * @param orderReport
	 *            order report
	 * @param timeout
	 *            time in millis, how long to wait for the report
	 */
	public WaitForOrderReport(String host, String port, IDataHolder<Order> sentOrder, IDataHolder<OrderResponse> orderResponse, IDataHolder<OrderReport> orderReport, long timeout) {
		super(host, port);
		this.orderResponse = orderResponse;
		this.orderReport = orderReport;
		this.timeout = timeout;
		this.sentOrder = sentOrder;
	}

	/**
	 * Constructor
	 *
	 * @param host
	 *            host of the ContinuITy orchestrator
	 * @param port
	 *            port of the ContinuITy orchestrator
	 * @param orderResponse
	 *            order response
	 * @param orderReport
	 *            order report
	 * @param timeout
	 *            time in millis, how long to wait for the report
	 */
	public WaitForOrderReport(String host, String port, IDataHolder<OrderResponse> orderResponse, IDataHolder<OrderReport> orderReport, long timeout) {
		this(host, port, NoopDataHolder.instance(), orderResponse, orderReport, timeout);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(Feature.MINIMIZE_QUOTES).enable(Feature.USE_NATIVE_OBJECT_ID));
		if (orderResponse.isSet() && (orderResponse.get().getWaitLink() != null)) {
			URL url = new URL(orderResponse.get().getWaitLink());
			LOGGER.info("Wait for order {} to be finished", orderResponse.get().getWaitLink());
			OrderReport receivedOrderReport = null;
			while(null == receivedOrderReport) {
				LOGGER.info("Waiting for {} millis", timeout);
				receivedOrderReport = get(url.toURI().getPath() + "?timeout=" + timeout, OrderReport.class);
			}

			orderReport.set(receivedOrderReport);
			Path basePath = context.toPath();
			ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
			writer.writeValue(basePath.resolve(FILENAME + FILE_EXT).toFile(), orderReport.get());

			Order order;

			if (sentOrder.isSet()) {
				order = sentOrder.get();
			} else {
				order = new Order();
				order.setSource(new LinkExchangeModel());
			}

			Path folderPath = context.toPath();
			storeIfNew(folderPath.resolve("session-logs.txt"), LinkExchangeModel::getSessionLogsLinks, SessionLogsLinks::getLink, sentOrder.get().getSource(), orderReport.get().getCreatedArtifacts());
			storeIfNew(folderPath.resolve("workload-model.json"), LinkExchangeModel::getWorkloadModelLinks, WorkloadModelLinks::getLink, sentOrder.get().getSource(),
					orderReport.get().getCreatedArtifacts());
			storeIfNew(folderPath.resolve("behavior-model.json"), LinkExchangeModel::getWorkloadModelLinks, WorkloadModelLinks::getBehaviorLink, sentOrder.get().getSource(),
					orderReport.get().getCreatedArtifacts());
			storeIfNew(folderPath.resolve("load-test.jmx"), LinkExchangeModel::getLoadTestLinks, LoadTestLinks::getLink, sentOrder.get().getSource(), orderReport.get().getCreatedArtifacts());
			storeIfNew(folderPath.resolve("load-test-report.csv"), LinkExchangeModel::getLoadTestLinks, LoadTestLinks::getReportLink, sentOrder.get().getSource(),
					orderReport.get().getCreatedArtifacts());
		}

	}

	private <T> void storeIfNew(Path path, Function<LinkExchangeModel, T> firstGetter, Function<T, String> secondGetter, LinkExchangeModel oldModel, LinkExchangeModel newModel)
			throws IOException {
		String oldLink = firstGetter.andThen(secondGetter).apply(oldModel);
		String newLink = firstGetter.andThen(secondGetter).apply(newModel);

		if ((oldLink == null) && (newLink != null)) {
			URL url = new URL(newLink);
			ResponseEntity<String> response = getAsEntity(url.getHost(), url.getPort(), url.getPath() + url.getQuery(), String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				Files.write(path, response.getBody().getBytes(), StandardOpenOption.CREATE);
				LOGGER.info("Successfully stored the artifact to {}.", path);
			} else {
				LOGGER.warn("Could not download {}: {} - {}", path, response.getStatusCodeValue(), response.getBody());
			}
		}
	}

}
