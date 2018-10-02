package org.continuity.experimentation.action.continuity;

import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.continuity.api.entities.report.OrderReport;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

public class GetJmeterReport extends AbstractRestAction {
	
	private static final String FILE_NAME = "jmeter-report.csv"; 
	
	private IDataHolder<OrderReport> orderReport;

	/**
	 * Constructor
	 * 
	 * @param host
	 *            host of jmeter
	 * @param port
	 *            host of port
	 */
	public GetJmeterReport(String host, String port, IDataHolder<OrderReport> orderReport) {
		super(host, port);
		this.orderReport = orderReport;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		if(orderReport.isSet() && orderReport.get().isSuccessful() && orderReport.get().getCreatedArtifacts().getLoadTestLinks().getReportLink() != null) {
			URI uri = new URI(orderReport.get().getCreatedArtifacts().getLoadTestLinks().getReportLink());
			String jmeterResult = get(uri.getPath(), String.class);
			FileUtils.writeStringToFile(context.toPath().resolve(FILE_NAME).toFile(), jmeterResult, "UTF-8");
		}
		
	}

}
