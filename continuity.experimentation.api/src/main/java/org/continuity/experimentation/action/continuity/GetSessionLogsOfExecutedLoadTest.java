package org.continuity.experimentation.action.continuity;

import java.net.URI;
import java.nio.charset.Charset;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.io.FileUtils;
import org.continuity.api.entities.config.Order;
import org.continuity.api.entities.config.OrderGoal;
import org.continuity.api.entities.config.OrderMode;
import org.continuity.api.entities.links.ExternalDataLinkType;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.report.OrderReport;
import org.continuity.api.entities.report.OrderResponse;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.builder.ExperimentBuilderImpl;
import org.continuity.experimentation.builder.StableExperimentBuilder;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Action extracts session logs from the traces, which were invoced by the already executed
 * JMeter test.
 * 
 * @author Tobias Angerstein
 *
 */
public class GetSessionLogsOfExecutedLoadTest extends AbstractRestAction {

	private static Logger LOGGER = LoggerFactory.getLogger(GetSessionLogsOfExecutedLoadTest.class);

	private int waitForOrderTimeout;

	/**
	 * Order Report
	 */
	private IDataHolder<OrderReport> orderReport;

	private static final String DEST_DATE_FORMAT = "yyyy/MM/dd/HH:mm:ss";
	private static final String ORIG_DATE_FORMAT = "yyyy/MM/dd HH:mm:ss.SSS";

	private String tag;

	private String measurementDataLink;

	private IDataHolder<String> referenceSessionLogsLink;

	public GetSessionLogsOfExecutedLoadTest(String host, String port, String tag, IDataHolder<OrderReport> orderReport, String measurementDataLink, int timeout, IDataHolder<String> referenceSessionLogsLink) {
		super(host, port);
		this.tag = tag;
		this.orderReport = orderReport;
		this.measurementDataLink = measurementDataLink;
		this.waitForOrderTimeout = timeout;
		this.referenceSessionLogsLink = referenceSessionLogsLink;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		URI uri = new URI(orderReport.get().getCreatedArtifacts().getLoadTestLinks().getReportLink());
		String loadTestReport = get(uri.getPath(), String.class);
		String[] entries = loadTestReport.split("\n");
		String from = LocalDateTime.parse(entries[1].split("\t")[0], DateTimeFormatter.ofPattern(ORIG_DATE_FORMAT)).format(DateTimeFormatter.ofPattern(DEST_DATE_FORMAT));
		String to = LocalDateTime.parse(entries[entries.length-1].split("\t")[0], DateTimeFormatter.ofPattern(ORIG_DATE_FORMAT)).format(DateTimeFormatter.ofPattern(DEST_DATE_FORMAT));

		LinkExchangeModel source = new LinkExchangeModel();
		source.getMeasurementDataLinks().setLink(measurementDataLink + "?fromDate=" + from + "&toDate=" + to);
		source.getMeasurementDataLinks().setLinkType(ExternalDataLinkType.OPEN_XTRACE);
		source.getMeasurementDataLinks().setTimestamp(Date.valueOf(LocalDate.now()));
		
		LOGGER.info("The following link is used to get the resulting session logs of the executed load test:{}", source.getMeasurementDataLinks().getLink());
		Order order = new Order();
		order.setTag(tag);
		order.setGoal(OrderGoal.CREATE_SESSION_LOGS);
		order.setMode(OrderMode.PAST_SESSIONS);
		order.setSource(source);

		IDataHolder<OrderResponse> orderResponse = new SimpleDataHolder<OrderResponse>("OrderResponse", OrderResponse.class);
		IDataHolder<OrderReport> sessionLogsOrderReport = new SimpleDataHolder<OrderReport>("SessionLogsOrderReport", OrderReport.class);

		OrderSubmission orderSubmission = new OrderSubmission(super.getHost(), super.getPort(), order, orderResponse);
		WaitForOrderReport waitForOrderReport = new WaitForOrderReport(super.getHost(), super.getPort(), orderResponse, sessionLogsOrderReport, waitForOrderTimeout);

		StableExperimentBuilder getSessionLogsExperimentBuilder = new ExperimentBuilderImpl("getSessionLogs");

		getSessionLogsExperimentBuilder.append(orderSubmission).append(waitForOrderReport);

		getSessionLogsExperimentBuilder.build().execute();

		URI sessionLogsLink = new URI(sessionLogsOrderReport.get().getCreatedArtifacts().getSessionLogsLinks().getLink());
		
		referenceSessionLogsLink.set("http://orchestrator:80"+sessionLogsLink.getPath());

		String sessionLogs = get(sessionLogsLink.getPath(), String.class);

		FileUtils.writeStringToFile(context.toPath().resolve("result-sessions.dat").toFile(), sessionLogs, Charset.defaultCharset());

	}

}
