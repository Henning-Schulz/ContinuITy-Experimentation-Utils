package org.continuity.experimentation.action;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Exports values of a certain metric and a different services as separate files
 *
 * @author Tobias Angerstein
 *
 */
public class PrometheusDataExporter extends AbstractRestAction {

	private static final String URI = "/api/v1/query?query=";

	private static final String TIME_STAMP = "timestamp";

	private static final String FILE_EXT = ".csv";

	private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusDataExporter.class);

	/**
	 * The desired metrics, which is gonna be exported
	 */
	private List<String> metrics;

	/**
	 * The desired services, which gonna be measured
	 */
	private List<String> services;

	/**
	 * The time frame since now in minutes
	 */
	private long timeframe;

	/**
	 * Constructor
	 *
	 * @param metrics
	 *            the metrics
	 * @param host
	 *            the host of prometheus
	 * @param port
	 *            the port of prometheus
	 * @param services
	 *            the names of the services, which are under test
	 * @param timeframe
	 *            the time frame
	 */
	public PrometheusDataExporter(List<String> metrics, String host, String port, String orchestratorHost, String orchestratorPort, List<String> services, long timeframe) {
		super(host, port);
		this.metrics = metrics;
		this.services = services;
		this.timeframe = timeframe+20;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		//getLoadTestTimeStamps();
		RestTemplate restTemplate = new RestTemplate();
		StringBuffer url = new StringBuffer("http://");
		url.append(getHost());
		url.append(":");
		url.append(getPort());
		url.append(URI);
		url.append(buildQuery());
		URI uri = UriComponentsBuilder.fromUriString(url.toString()).build().encode().toUri();
		LOGGER.info("Retrieve prometheus results with url: {}", uri);
		ResponseEntity<ObjectNode> response = restTemplate.exchange(uri, HttpMethod.GET, null, ObjectNode.class);
		if (response.getStatusCode().is2xxSuccessful() && response.getBody().get("status").textValue().equals("success")) {
			safeAsCSV(context.toPath(), response);
		} else {
			throw new Exception("Prometheus query was not successful");
		}
	}

	private void safeAsCSV(Path basePath, ResponseEntity<ObjectNode> response) throws IOException {
		HashMap<Path, String> csvInputs = new HashMap<Path, String>();

		JsonNode results = response.getBody().get("data").get("result");
		// Iterate over all results
		for (JsonNode result : results) {
			StringBuffer csvInput;
			if (result.get("metric").has("route")) {
				csvInput = new StringBuffer(TIME_STAMP + "," + result.get("metric").get("__name__").textValue() + "_" + result.get("metric").get("route").textValue() + "\n");
			} else {
				csvInput = new StringBuffer(TIME_STAMP + "," + result.get("metric").get("__name__").textValue() + "\n");
			}
			// Iterate over all values
			for (JsonNode value : result.get("values")) {
				csvInput.append(value.get(0));
				csvInput.append(",");
				csvInput.append(value.get(1));
				csvInput.append("\n");
			}
			// Delete last \n
			csvInput.delete(csvInput.length() - 1, csvInput.length() - 1);
			if (result.get("metric").has("route")) {
				csvInputs.put(basePath.resolve(result.get("metric").get("job").textValue() + "_" + result.get("metric").get("__name__").textValue() + "_" + result.get("metric").get("route").textValue().replaceAll("/", "#") + "_"+result.get("metric").get("status_code")+ FILE_EXT), csvInput.toString());
			} else {
				csvInputs.put(basePath.resolve(result.get("metric").get("job").textValue() + "_" + result.get("metric").get("__name__").textValue() + FILE_EXT), csvInput.toString());
			}
		}
		// Create CSV files
		for (Entry<Path, String> fileToExport : csvInputs.entrySet()) {
			FileUtils.writeStringToFile(fileToExport.getKey().toFile(), fileToExport.getValue(), Charset.defaultCharset());
			LOGGER.debug("Saved prometheus metric to {}.", fileToExport.getKey());
		}

	}

	/**
	 * Builds prometheus query
	 *
	 * @return
	 */
	private String buildQuery() {
		StringBuffer query = new StringBuffer();
		query.append("{__name__=~\"");
		for (int i = 0; i < metrics.size(); i++) {
			query.append(metrics.get(i));
			if (i != (metrics.size() - 1)) {
				query.append("|");
			}
		}
		query.append("\",job=~\"");
		for (int j = 0; j < services.size(); j++) {
			query.append(services.get(j));
			if (j != (services.size() - 1)) {
				query.append("|");
			}
		}
		query.append("\"}[");
		query.append(timeframe);
		query.append("m]");
		return query.toString();
	}
}
