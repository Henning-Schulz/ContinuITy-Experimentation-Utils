package org.continuity.experimentation.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JMeterLog implements Iterable<JMeterLogEntry> {

	public static final String DATE_FORMAT = "yyyy/MM/dd HH:mm:ss.SSS";

	private static final Map<String, Integer> DEFAULT_HEADERS = toHeadersMap(new String[] { "timeStamp", "elapsed", "label", "responseCode", "responseMessage", "threadName", "dataType", "success",
			"bytes", "sentBytes", "grpThreads", "allThreads", "Latency", "SampleCount", "ErrorCount", "Connect", "\"SESSION_ID\"", "\"REFERENCE\"" });

	private final List<JMeterLogEntry> entries;

	private JMeterLog(List<JMeterLogEntry> entries) {
		this.entries = entries;
	}

	public static JMeterLog fromCsv(List<String> csv) {
		List<String[]> rows = csv.stream().map(row -> row.split("\\t")).collect(Collectors.toList());
		Map<String, Integer> headers;

		if ("timeStamp".equals(rows.get(0)[0])) {
			headers = toHeadersMap(rows.get(0));
			rows = rows.subList(1, rows.size());
		} else {
			headers = DEFAULT_HEADERS;
		}

		return new JMeterLog(rows.stream().map(r -> new JMeterLogEntry(headers, r)).collect(Collectors.toList()));
	}

	private static Map<String, Integer> toHeadersMap(String[] headersRow) {
		Map<String, Integer> headers = new HashMap<>();

		int index = 0;

		for (String property : headersRow) {
			headers.put(property, index);
			index++;
		}

		return headers;
	}

	@Override
	public Iterator<JMeterLogEntry> iterator() {
		return entries.iterator();
	}

	public Stream<JMeterLogEntry> stream() {
		return entries.stream();
	}

	public List<JMeterLogEntry> getEntries() {
		return entries;
	}

}
