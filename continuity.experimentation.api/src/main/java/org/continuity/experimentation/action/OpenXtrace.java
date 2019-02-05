package org.continuity.experimentation.action;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenXtrace {

	private OpenXtrace() {
	}

	public static Downloader download(String host, String port, String basePath, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) {
		return new Downloader(host, port, basePath, startDate, endDate, dateFormat, traceHolder);
	}

	public static Downloader download(String urlString, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) throws MalformedURLException {
		URL url = new URL(urlString);
		return new Downloader(url.getHost(), Integer.toString(url.getPort()), url.getPath(), startDate, endDate, dateFormat, traceHolder);
	}

	public static class Downloader extends AbstractRestAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(OpenXtrace.Downloader.class);

		private final String url;
		private final IDataHolder<Date> startDate;
		private final IDataHolder<Date> endDate;
		private final DateFormat dateFormat;
		private final IDataHolder<String> traceHolder;

		private Downloader(String host, String port, String url, IDataHolder<Date> startDate, IDataHolder<Date> endDate, String dateFormat, IDataHolder<String> traceHolder) {
			super(host, port);
			this.url = url;
			this.startDate = startDate;
			this.endDate = endDate;
			this.dateFormat = new SimpleDateFormat(dateFormat);
			this.traceHolder = traceHolder;
		}


		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			String requestUrl = url + "?fromDate=" + dateFormat.format(startDate.get()) + "&toDate=" + dateFormat.format(endDate.get());
			LOGGER.info("Retrieving OPEN.xtraces from {}...", requestUrl);

			String traces = get(requestUrl, String.class);
			traceHolder.set(traces);

			FileUtils.writeStringToFile(context.toPath().resolve("open-xtraces.json").toFile(), traces, Charset.defaultCharset());

			LOGGER.info("Stored OPEN.xtraces to {}.", context.toPath().resolve("open-xtraces.json").toString());
		}

	}

}
