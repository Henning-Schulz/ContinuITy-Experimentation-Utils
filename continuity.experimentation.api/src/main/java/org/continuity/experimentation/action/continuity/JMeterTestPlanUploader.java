package org.continuity.experimentation.action.continuity;
import static org.continuity.api.rest.RestApi.JMeter.TestPlan.Paths.POST;

import java.io.IOException;

import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.rest.RestApi;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Executes a JMeter test plan.
 *
 * @author Henning Schulz
 *
 */
public class JMeterTestPlanUploader extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterTestPlanUploader.class);

	private final IDataHolder<String> testPlanBundle;

	private String tag;

	private IDataHolder<LinkExchangeModel> source;

	/**
	 * Constructor.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param port
	 *            The port of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public JMeterTestPlanUploader(String host, String port, IDataHolder<String> testPlanBundle, String tag, IDataHolder<LinkExchangeModel> source) {
		super(host, port);
		this.testPlanBundle = testPlanBundle;
		this.tag = tag;
		this.source = source;
	}

	/**
	 * Constructor using the default port 8080.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public JMeterTestPlanUploader(String host, IDataHolder<String> testPlanBundle, String tag, IDataHolder<LinkExchangeModel> source) {
		this(host, "8080", testPlanBundle, tag, source);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 * @throws RuntimeException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			LinkExchangeModel linkExchangeModel = post(RestApi.Orchestrator.Loadtest.POST.requestUrl("jmeter", tag).getURI(), LinkExchangeModel.class, mapper.readTree(testPlanBundle.get()));
			LOGGER.info("Response from Orchestrator service: {}", linkExchangeModel);
			source.set(linkExchangeModel);
		} catch (RuntimeException | IOException e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Upload \"" + testPlanBundle + "\" via JMeter service " + super.toString();
	}

}
