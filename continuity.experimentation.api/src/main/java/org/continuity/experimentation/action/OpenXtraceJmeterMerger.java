package org.continuity.experimentation.action;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Triple;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.entity.JMeterLog;
import org.continuity.experimentation.entity.JMeterLogEntry;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.json.JSONException;
import org.spec.research.open.xtrace.api.core.Trace;
import org.spec.research.open.xtrace.api.core.callables.HTTPRequestProcessing;

import open.xtrace.OPENxtraceUtils;

public class OpenXtraceJmeterMerger implements IExperimentAction {

	private final IDataHolder<JMeterLog> jmeterLog;

	private final IDataHolder<List<Trace>> traces;

	private OpenXtraceJmeterMerger(IDataHolder<JMeterLog> jmeterLog, IDataHolder<List<Trace>> traces) {
		this.jmeterLog = jmeterLog;
		this.traces = traces;
	}

	public static void main(String[] args) throws AbortInnerException, AbortException, Exception {
		// Path dir =
		// Paths.get("/Users/hsh/ownCloud/continuity-eval-modularization-hpi/exp-modularization/4-modularized-load-tests/session-logs-carts/test-execution");
		Path dir = Paths.get("/Users/hsh/ownCloud/continuity-eval-modularization-hpi/exp-modularization/2-reference-load-test");

		List<Trace> traces = OPENxtraceUtils.deserializeIntoTraceList(new String(Files.readAllBytes(dir.resolve("open-xtraces.json"))));
		JMeterLog jmeterLog = JMeterLog.fromCsv(Files.readAllLines(dir.resolve("load-test-report.csv"), Charset.defaultCharset()));
		OpenXtraceJmeterMerger merger = new OpenXtraceJmeterMerger(new SimpleDataHolder<>("jmeter", jmeterLog), new SimpleDataHolder<>("traces", traces));

		// merger.execute(new Context());
		merger.test();
	}

	public static void main2(String[] args) throws AbortInnerException, AbortException, Exception {
		Path dir = Paths.get("/Users/hsh/ownCloud/continuity-eval-modularization-hpi/exp-modularization/4-modularized-load-tests");

		for (String subFolder : dir.toFile().list()) {
			if (subFolder.startsWith(".") || !dir.resolve(subFolder).toFile().isDirectory()) {
				continue;
			}

			System.out.println(":: " + subFolder + " ::");

			List<Trace> traces;
			try {
				traces = OPENxtraceUtils
						.deserializeIntoTraceList(new String(Files.readAllBytes(dir.resolve(subFolder).resolve("test-execution").resolve("open-xtraces.json"))));
			} catch (JSONException e) {
				e.printStackTrace();
				continue;
			}

			JMeterLog jmeterLog = JMeterLog.fromCsv(Files.readAllLines(dir.resolve(subFolder).resolve("test-execution").resolve("load-test-report.csv"), Charset.defaultCharset()));
			OpenXtraceJmeterMerger merger = new OpenXtraceJmeterMerger(new SimpleDataHolder<>("jmeter", jmeterLog), new SimpleDataHolder<>("traces", traces));

			merger.test();

			System.out.println();
		}
	}

	private void test() throws AbortInnerException {
		Collections.sort(jmeterLog.get().getEntries(), (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
		Collections.sort(traces.get(), (a, b) -> Long.compare(a.getRoot().getRoot().getTimestamp(), b.getRoot().getRoot().getTimestamp()));

		Date start = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).min().getAsLong() - 3600000);
		Date end = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).max().getAsLong() - 3600000);
		List<JMeterLogEntry> filteredEntries = jmeterLog.get().stream().filter(e -> e.getTimestamp().after(start) && e.getTimestamp().before(end)).collect(Collectors.toList());

		System.out.println("JMeter: " + filteredEntries.stream().count());
		// System.out.println("JMeter (catalogueGetTagsUsingGET): " +
		// jmeterLog.get().stream().filter(entry ->
		// "catalogueGetTagsUsingGET".equals(entry.getValue("label"))).count());
		Date jmeterFirstDate = filteredEntries.get(0).getTimestamp();
		Date jmeterLastDate = filteredEntries.get(filteredEntries.size() - 1).getTimestamp();
		System.out.println(" - from " + jmeterFirstDate + " to " + jmeterLastDate);

		List<String> jmeterEndpoints = filteredEntries.stream().map(e -> e.getValue("label")).distinct().collect(Collectors.toList());
		System.out.println(" - endpoints: " + jmeterEndpoints);

		List<Trace> filteredTraces = traces.get().stream().filter(t -> !"/metrics".equals(((HTTPRequestProcessing) t.getRoot().getRoot()).getUri())).collect(Collectors.toList());

		System.out.println("Traces: " + filteredTraces.size());
		if (!filteredTraces.isEmpty()) {
			Date traceFirstDate = new Date(filteredTraces.get(0).getRoot().getRoot().getTimestamp());
			Date traceLastDate = new Date(filteredTraces.get(filteredTraces.size() - 1).getRoot().getRoot().getTimestamp());
			System.out.println(" - from " + traceFirstDate + " to " + traceLastDate);

			List<String> traceEndpoints = filteredTraces.stream().map(t -> t.getRoot().getLocation().getPort() + ((HTTPRequestProcessing) t.getRoot().getRoot()).getUri()).distinct()
					.collect(Collectors.toList());
			System.out.println(" - endpoints: " + traceEndpoints);
		}
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		Date start = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).min().getAsLong() - 3600000);
		Date end = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).max().getAsLong() - 3600000);

		List<JMeterLogEntry> filteredEntries = jmeterLog.get().stream().filter(e -> e.getTimestamp().after(start) && e.getTimestamp().before(end)).collect(Collectors.toList());

		Map<String, List<JMeterLogEntry>> entriesPerThread = splitByThreadAndSortByTimestamp(filteredEntries);
		Map<String, List<Trace>> requestsPerSession = splitBySessionAndSortByTimestamp(traces.get());

		for (Entry<String, List<JMeterLogEntry>> entry : entriesPerThread.entrySet()) {
			for (Triple<String, List<Trace>, List<String>> triple : findMatchingTraces(entry.getValue(), requestsPerSession)) {
				System.out.print(triple.getLeft());
				System.out.print(" - Missing: ");
				System.out.println(triple.getRight());
			}
		}
	}

	private List<Triple<String, List<Trace>, List<String>>> findMatchingTraces(List<JMeterLogEntry> thread, Map<String, List<Trace>> requestsPerSession) {
		List<Triple<String, List<Trace>, List<String>>> result = new ArrayList<>();

		for (List<Trace> session : requestsPerSession.values()) {
			Iterator<JMeterLogEntry> threadIter = thread.iterator();
			Iterator<Trace> sessionIter = session.iterator();

			boolean matching = true;

			while (matching && threadIter.hasNext() && sessionIter.hasNext()) {
				JMeterLogEntry jmeterReq = threadIter.next();
				Trace trace = sessionIter.next();

				matching &= jmeterReq.getValue("label").equals(null); // TODO
				matching &= jmeterReq.getTimestamp().before(new Date(trace.getRoot().getRoot().getTimestamp()));
			}

			if (matching) {

			}
		}

		return null;
	}

	private Map<String, List<JMeterLogEntry>> splitByThreadAndSortByTimestamp(List<JMeterLogEntry> log) {
		Map<String, List<JMeterLogEntry>> entriesPerThread = new HashMap<>();

		for (JMeterLogEntry entry : log) {
			List<JMeterLogEntry> list = entriesPerThread.get(entry.getValue("threadName"));

			if (list == null) {
				list = new ArrayList<>();
				entriesPerThread.put(entry.getValue("threadName"), list);
			}

			list.add(entry);
		}

		for (List<JMeterLogEntry> entries : entriesPerThread.values()) {
			Collections.sort(entries, (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
		}

		return entriesPerThread;
	}

	private Map<String, List<Trace>> splitBySessionAndSortByTimestamp(List<Trace> traces) {
		Map<String, List<Trace>> requestsPerSession = new HashMap<>();

		for (Trace trace : traces) {
			String sessionId = OPENxtraceUtils.extractSessionIdFromCookies(((HTTPRequestProcessing) trace.getRoot().getRoot()).getHTTPHeaders().get().get("cookie"));
			List<Trace> list = requestsPerSession.get(sessionId);

			if (list == null) {
				list = new ArrayList<>();
				requestsPerSession.put(sessionId, list);
			}

			list.add(trace);
		}

		for (List<Trace> traceList : requestsPerSession.values()) {
			Collections.sort(traceList, (a, b) -> Long.compare(a.getRoot().getRoot().getTimestamp(), b.getRoot().getRoot().getTimestamp()));
		}

		return requestsPerSession;
	}

}
