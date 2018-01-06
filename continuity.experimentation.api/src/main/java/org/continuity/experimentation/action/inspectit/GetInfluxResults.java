package org.continuity.experimentation.action.inspectit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetInfluxResults implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(GetInfluxResults.class);
	/**
	 * reference loadtest report path
	 */
	private static final String REFERENCE_LOADTEST_REPORT_PATH = "/referenceLoadtest/";

	/**
	 * generated loadtest report path
	 */

	private static final String GENERATED_LOADTEST_REPORT_PATH = "/generatedLoadtest/";
	private static final String BUSINESS_TRANSACTIONS_MEASUREMENT = "businessTransactions";
	private static final String MEMORY_MEASUREMENT = "memory";
	private static final String CPU_MEASUREMENT = "cpu";
	private static final String INFLUX_USER = "inspectit";
	private static final String INFLUX_PASSWORD = "inspectit";

	private static final String DB_NAME = INFLUX_USER;

	private static final String FILE_EXT = ".csv";
	private static final String SEPARATOR = ";";

	/**
	 * InfluxDB connector.
	 */
	private InfluxDB influxDB;

	/**
	 * StartTime
	 */
	private IDataHolder<Date> startTime;

	/**
	 * StopTime
	 */
	private IDataHolder<Date> stopTime;

	private boolean generatedLoadtest;

	// Will be increased before the first iteration
	private static int runCount = -1;

	public GetInfluxResults(String host, String port, boolean generatedLoadtest, IDataHolder<Date> startTime, IDataHolder<Date> stopTime) {
		influxDB = InfluxDBFactory.connect("http://" + host + ':' + port, INFLUX_USER, INFLUX_PASSWORD);
		influxDB.setDatabase(DB_NAME);
		this.startTime = startTime;
		this.stopTime = stopTime;
		this.generatedLoadtest = generatedLoadtest;
	}

	@Override
	public void execute() {
		try {
			String path = "";
			if (generatedLoadtest) {
				path = "run#" + runCount + GENERATED_LOADTEST_REPORT_PATH;
			} else {
				runCount++;
				// Increase count before each reference load test.
				// (Workload model generation can fail and then, no generated load test will be
				// executed)
				path = "run#" + runCount + REFERENCE_LOADTEST_REPORT_PATH;
			}

			FileUtils.writeStringToFile(new File(path + CPU_MEASUREMENT + FILE_EXT), getMeasurementResults(CPU_MEASUREMENT), Charset.defaultCharset());
			FileUtils.writeStringToFile(new File(path + MEMORY_MEASUREMENT + FILE_EXT), getMeasurementResults(MEMORY_MEASUREMENT), Charset.defaultCharset());
			FileUtils.writeStringToFile(new File(path + BUSINESS_TRANSACTIONS_MEASUREMENT + FILE_EXT), getMeasurementResults(BUSINESS_TRANSACTIONS_MEASUREMENT), Charset.defaultCharset());

		} catch (IOException e) {
			LOGGER.error("Error during getting Influx results for {} test of run {}.", (generatedLoadtest ? "generated" : "reference"), runCount);
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		// from: 2018-01-05 21:16:20
		// to : 2018-01-05 21:26:17
		SimpleDataHolder<Date> start = new SimpleDataHolder<Date>("start", Date.class);
		SimpleDataHolder<Date> stop = new SimpleDataHolder<Date>("stop", Date.class);
		Calendar calendar = Calendar.getInstance();

		calendar.set(2018, 0, 5, 21, 20, 00);
		Date fromDate = calendar.getTime();
		calendar.set(2018, 0, 5, 21, 25, 00);
		Date toDate = calendar.getTime();
		start.set(fromDate);
		stop.set(toDate);
		GetInfluxResults h = new GetInfluxResults("172.16.145.68", "8086", false, start, stop);
		h.execute();
	}

	private String getMeasurementResults(String measurement) {
		String queryString = String.format("SELECT * FROM %s WHERE time >= %d AND time <= %d", measurement, startTime.get().getTime() * 1000000, stopTime.get().getTime() * 1000000);
		Query query = new Query(queryString, DB_NAME);
		QueryResult queryResult = influxDB.query(query);
		List<Result> result = queryResult.getResults();
		Series series = result.get(0).getSeries().get(0);

		StringBuilder builder = new StringBuilder();

		boolean first = true;
		for (String header : series.getColumns()) {
			if (first) {
				first = false;
			} else {
				builder.append(SEPARATOR);
			}

			builder.append(header);
		}

		builder.append("\n");

		for (List<Object> row : series.getValues()) {
			first = true;

			for (Object value : row) {
				if (first) {
					first = false;
				} else {
					builder.append(SEPARATOR);
				}

				builder.append(value);
			}

			builder.append("\n");
		}

		return builder.toString();
	}
}
