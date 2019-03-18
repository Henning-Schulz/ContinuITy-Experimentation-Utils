package org.continuity.experimentation.experiment;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ExperimentProperties {

	private final Properties properties;

	private final String tag;
	private final String dateFormat;

	public ExperimentProperties(String filename, String tag, String dateFormat) throws IOException {
		this.properties = read(filename);
		this.tag = tag;
		this.dateFormat = dateFormat;
	}

	private Properties read(String filename) throws IOException {
		Properties properties = new Properties();

		InputStream fileInputStream = new FileInputStream(filename);
		properties.load(fileInputStream);

		return properties;
	}

	public int getOrderReportTimeout() {
		return Integer.parseInt(properties.getProperty("order-report-timeout", "2000000"));
	}

	public String getExternalTraceSourceLink() {
		return properties.getProperty("external-trace-source-link", "http://localhost:8182/rest/open-xtrace/get");
	}

	public String getOrchestratorHost() {
		return properties.getProperty("orchestrator-host", "localhost");
	}

	public String getOrchestratorPort() {
		return properties.getProperty("orchestrator-port", "80");
	}

	public String getInitLoadTestFilePath() {
		return properties.getProperty("init-load-test-file-path", "init-testplan");
	}

	public String getReferenceLoadTestFilePath() {
		return properties.getProperty("reference-load-test-file-path", "sock-shop-testplan");
	}

	public String getIdpaFilePath() {
		return properties.getProperty("idpa-file-path", "idpa");
	}

	public String getPrometheusHost() {
		return properties.getProperty("prometheus-host", "localhost");
	}

	public String getPrometheusPort() {
		return properties.getProperty("prometheus-port", "9090");
	}

	public long getLoadTestDuration() {
		return Integer.parseInt(properties.getProperty("load-test-duration", "300"));
	}

	public int getLoadTestNumUsers() {
		return Integer.parseInt(properties.getProperty("load-test-num-user", "40"));
	}

	public int getLoadTestRampup() {
		return Integer.parseInt(properties.getProperty("load-test-rampup", "5"));
	}

	public long getDelayBetweenExecutions() {
		return Integer.parseInt(properties.getProperty("delay-between-executions", "10"));
	}

	public String getTargetServerHost() {
		return properties.getProperty("target-server-host", "127.0.0.1");
	}

	public String getTargetServerPort() {
		return properties.getProperty("target-server-port", "80");
	}

	public String getOrchestratorSatelliteHost() {
		return properties.getProperty("satellite-host-orchestrator", "127.0.0.1");
	}

	public String getSutSatelliteHost() {
		return properties.getProperty("satellite-host-sut", "127.0.0.1");
	}

	public boolean omitSutRestart() {
		return Boolean.parseBoolean(properties.getProperty("omit-sut-restart", "false"));
	}

	public boolean omitReferenceTest() {
		return Boolean.parseBoolean(properties.getProperty("omit-reference-test", "false"));
	}

	public String getTag() {
		return tag;
	}

	public String getDateFormat() {
		return dateFormat;
	}

}
