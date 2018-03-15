package org.continuity.experimentation.action.continuity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Executes a JMeter test plan.
 *
 * @author Henning Schulz
 *
 */
public class JMeterTestPlanExecution extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterTestPlanExecution.class);

	private final IDataHolder<TestPlanBundle> testPlanBundle;

	/**
	 * Constructor.
	 *
	 * @param host
	 *            The host of the ContinuITy frontend.
	 * @param port
	 *            The port of the ContinuITy frontend.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public JMeterTestPlanExecution(String host, String port, IDataHolder<TestPlanBundle> testPlanBundle) {
		super(host, port);
		this.testPlanBundle = testPlanBundle;
	}

	/**
	 * Constructor using the default port 8080.
	 *
	 * @param host
	 *            The host of the ContinuITy frontend.
	 * @param testPlanBundle
	 *            The JMeter test plan bundle to be executed.
	 */
	public JMeterTestPlanExecution(String host, IDataHolder<TestPlanBundle> testPlanBundle) {
		this(host, "8080", testPlanBundle);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonGenerationException
	 * @throws RuntimeException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException, JsonGenerationException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.writer().writeValue(context.toPath().resolve("jmeter-testplan.json").toFile(), testPlanBundle.get());

		String response = post("loadtest/jmeter/execute", String.class, testPlanBundle.get());
		LOGGER.info("Response from frontend: {}", response);
	}

	/**
	 * JMeter test plan bundle.
	 *
	 * @author Henning Schulz
	 *
	 */
	public static class TestPlanBundle {

		@JsonProperty("test-plan")
		private String testPlan;

		private File file;

		private Map<String, String[][]> behaviors;

		/**
		 * Gets {@link #testPlan}.
		 *
		 * @return {@link #testPlan}
		 */
		public String getTestPlan() {
			return this.testPlan;
		}

		/**
		 * Sets {@link #testPlan}.
		 *
		 * @param testPlan
		 *            New value for {@link #testPlan}
		 */
		public void setTestPlan(String testPlan) {
			this.testPlan = testPlan;
		}

		/**
		 * Gets {@link #behaviors}.
		 *
		 * @return {@link #behaviors}
		 */
		public Map<String, String[][]> getBehaviors() {
			return this.behaviors;
		}

		/**
		 * Sets {@link #behaviors}.
		 *
		 * @param behaviors
		 *            New value for {@link #behaviors}
		 */
		public void setBehaviors(Map<String, String[][]> behaviors) {
			this.behaviors = behaviors;
		}

		public TestPlanBundle(File file) {
			this.file = file;
			readFromJSON(file);
		}

		/**
		 * Gets file
		 *
		 * @return file
		 */
		public File getFile() {
			return file;
		}

		/**
		 * Reads TestPlan from JSON
		 *
		 * @param file
		 *            JSON file
		 */
		public void readFromJSON(File file) {
			try {
				String json = FileUtils.readFileToString(file, Charset.defaultCharset());
				ObjectMapper mapper = new ObjectMapper();
				ObjectNode node = mapper.readValue(json, ObjectNode.class);
				Map<String, String[][]> behaviors = new HashMap<String, String[][]>();
				Iterator<String> fieldNameIterator = node.get("behaviors").fieldNames();
				for(JsonNode behaviorNode: node.get("behaviors")) {
					String[][] behaviorModelArray = new String[behaviorNode.size()][];

					for(int i=0; i< behaviorNode.size(); i++) {
						String [] strings = new String[behaviorNode.get(i).size()];
						for (int j=0; j< behaviorNode.get(i).size(); j++) {
							strings[j] = behaviorNode.get(i).get(j).asText();
						}
						behaviorModelArray[i] = strings;
					}
					behaviors.put(fieldNameIterator.next().toString(), behaviorModelArray);
				}
				setBehaviors(behaviors);
				setTestPlan(node.get("test-plan").asText());
			} catch (IOException e) {
				LOGGER.error("Error when reading from JSON!", e);
			}
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Execute \"" + testPlanBundle + "\" via JMeter host " + super.toString();
	}

}
