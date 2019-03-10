package org.continuity.experimentation.action;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spec.research.open.xtrace.api.core.Trace;

import open.xtrace.OPENxtraceUtils;

public class OpenXtrace {

	private OpenXtrace() {
	}

	public static Downloader download(String host, String port, String basePath, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) {
		return new Downloader("http", host, port, basePath, startDate, endDate, dateFormat, traceHolder);
	}

	public static Downloader download(String urlString, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) throws MalformedURLException {
		URL url = new URL(urlString);
		int port = url.getPort() > 0 ? url.getPort() : 80;
		String protocol = url.getProtocol() == null ? "http" : url.getProtocol();

		return new Downloader(protocol, url.getHost(), Integer.toString(port), url.getPath(), startDate, endDate, dateFormat, traceHolder);
	}

	public static Deserializer deserialize(IDataHolder<String> input, IDataHolder<List<Trace>> output) {
		return new Deserializer(input, output);
	}

	public static class Downloader extends AbstractRestAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(OpenXtrace.Downloader.class);

		private final String path;
		private final IDataHolder<Date> startDate;
		private final IDataHolder<Date> endDate;
		private final DateFormat dateFormat;
		private final IDataHolder<String> traceHolder;

		private Downloader(String protocol, String host, String port, String path, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) {
			super(protocol, host, port);
			this.path = path;
			this.startDate = startDate;
			this.endDate = endDate;
			this.dateFormat = new SimpleDateFormat(dateFormat);
			this.traceHolder = traceHolder;
		}


		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			String requestUrl = path + "?fromDate=" + dateFormat.format(startDate.get()) + "&toDate=" + dateFormat.format(endDate.get());
			LOGGER.info("Retrieving OPEN.xtraces from {}{}...", super.toString(), requestUrl);

			String traces = get(requestUrl, String.class);
			traceHolder.set(traces);

			FileUtils.writeStringToFile(context.toPath().resolve("open-xtraces.json").toFile(), traces, Charset.defaultCharset());

			LOGGER.info("Stored OPEN.xtraces to {}.", context.toPath().resolve("open-xtraces.json").toString());
		}

	}

	public static class Deserializer implements IExperimentAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(OpenXtrace.Deserializer.class);

		private final IDataHolder<String> input;
		private final IDataHolder<List<Trace>> output;

		private Deserializer(IDataHolder<String> input, IDataHolder<List<Trace>> output) {
			this.input = input;
			this.output = output;
		}

		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			LOGGER.info("Deserializing OPEN.xtraces {} into {}.", input, output);

			List<Trace> traces = OPENxtraceUtils.deserializeIntoTraceList(input.get());
			output.set(traces);
		}

	}

}
