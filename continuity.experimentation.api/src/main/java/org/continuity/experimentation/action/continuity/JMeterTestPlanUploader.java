package org.continuity.experimentation.action.continuity;

import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.rest.RestApi;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a JMeter test plan.
 *
 * @author Henning Schulz
 *
 */
public class JMeterTestPlanUploader extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterTestPlanUploader.class);

	private final IDataHolder<JMeterTestPlanBundle> testPlanBundle;

	private IDataHolder<String> tag;

	/**
	 * Constructor. Use {@link JMeterTestplan#upload(String, String, IDataHolder, IDataHolder)}
	 * instead of this constructor.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param port
	 *            The port of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	protected JMeterTestPlanUploader(String host, String port, IDataHolder<JMeterTestPlanBundle> testPlanBundle, IDataHolder<String> tag) {
		super(host, port);
		this.testPlanBundle = testPlanBundle;
		this.tag = tag;
	}

	/**
	 * Constructor using the default port 8080. Use
	 * {@link JMeterTestplan#upload(String, IDataHolder, IDataHolder)} instead of this constructor.
	 *
	 * @param host
	 *            The host of the ContinuITy orchestrator.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	protected JMeterTestPlanUploader(String host, IDataHolder<JMeterTestPlanBundle> testPlanBundle, IDataHolder<String> tag) {
		this(host, "8080", testPlanBundle, tag);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 * @throws RuntimeException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		LOGGER.info("Uploading the test plan {} to {}...", testPlanBundle, RestApi.Orchestrator.Loadtest.POST.requestUrl("jmeter", tag.get()).withHost(getHost() + ":" + getPort()).get());
		LinkExchangeModel linkExchangeModel = post(RestApi.Orchestrator.Loadtest.POST.requestUrl("jmeter", tag.get()).getURI(), LinkExchangeModel.class, testPlanBundle.get());
		LOGGER.info("Response from Orchestrator service: {}", linkExchangeModel);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Upload \"" + testPlanBundle + "\" via JMeter service " + super.toString();
	}

}
