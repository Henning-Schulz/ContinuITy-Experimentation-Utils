package org.continuity.experimentation.entity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class JMeterLogEntry {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(JMeterLog.DATE_FORMAT);

	private final Map<String, Integer> headers;

	private final String[] row;

	protected JMeterLogEntry(Map<String, Integer> headers, String[] row) {
		this.headers = headers;
		this.row = row;
	}

	public String getValue(String property) {
		if (headers.containsKey(property)) {
			return row[headers.get(property)];
		} else {
			return null;
		}
	}

	public int getIntValue(String property) {
		String value = getValue(property);

		if (value == null) {
			throw new IllegalArgumentException("There is no value for property " + property);
		} else {
			return Integer.parseInt(value);
		}
	}

	public Date getTimestamp() {
		try {
			return DATE_FORMAT.parse(getValue("timeStamp"));
		} catch (ParseException e) {
			return null;
		}
	}

}
