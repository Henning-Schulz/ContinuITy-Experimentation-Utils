package org.continuity.experimentation.action.continuity;

import java.net.URL;
import java.nio.file.Path;

import org.continuity.api.entities.report.OrderReport;
import org.continuity.api.entities.report.OrderResponse;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * @param orderResponse
	 *            order response
	 * @param orderReport
	 *            order report
	 * @param timeout
	 *            time in millis, how long to wait for the report
	 */
	public WaitForOrderReport(String host, String port, IDataHolder<OrderResponse> orderResponse, IDataHolder<OrderReport> orderReport, long timeout) {
		super(host, port);
		this.orderResponse = orderResponse;
		this.orderReport = orderReport;
		this.timeout = timeout;

	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(Feature.MINIMIZE_QUOTES).enable(Feature.USE_NATIVE_OBJECT_ID));
		if (orderResponse.isSet() && orderResponse.get().getWaitLink() != null) {
			URL url = new URL(orderResponse.get().getWaitLink());
			LOGGER.info("Wait for order to be finished");
			orderReport.set(get(url.toURI().getPath() + "?timeout=" + timeout, OrderReport.class));
			Path basePath = context.toPath();
			ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
			writer.writeValue(basePath.resolve(FILENAME + FILE_EXT).toFile(), orderReport.get());
		}

	}

}
