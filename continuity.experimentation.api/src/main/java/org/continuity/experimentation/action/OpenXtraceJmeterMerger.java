package org.continuity.experimentation.action;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.continuity.commons.idpa.RequestUriMapper;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.NoopDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.entity.JMeterLog;
import org.continuity.experimentation.entity.JMeterLogEntry;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.continuity.idpa.application.Application;
import org.continuity.idpa.application.HttpEndpoint;
import org.continuity.idpa.yaml.IdpaYamlSerializer;
import org.json.JSONException;
import org.spec.research.open.xtrace.api.core.SubTrace;
import org.spec.research.open.xtrace.api.core.Trace;
import org.spec.research.open.xtrace.api.core.callables.Callable;
import org.spec.research.open.xtrace.api.core.callables.HTTPRequestProcessing;
import org.spec.research.open.xtrace.dflt.impl.core.LocationImpl;
import org.spec.research.open.xtrace.dflt.impl.core.SubTraceImpl;

import open.xtrace.OPENxtraceUtils;

public class OpenXtraceJmeterMerger implements IExperimentAction {

	private static final String LABEL_UNKNOWN = "UNKNOWN";

	private static final long MAX_TIME_SHIFT = 100;

	private final IDataHolder<JMeterLog> jmeterLog;

	private final IDataHolder<List<Trace>> traces;

	private final IDataHolder<Application> application;

	private OpenXtraceJmeterMerger(IDataHolder<JMeterLog> jmeterLog, IDataHolder<List<Trace>> traces, IDataHolder<Application> application) {
		this.jmeterLog = jmeterLog;
		this.traces = traces;
		this.application = application;
	}

	public static void main(String[] args) throws AbortInnerException, AbortException, Exception {
		Path dir = Paths.get("/Users/hsh/ownCloud/continuity-eval-modularization-hpi/exp-modularization/4-modularized-load-tests/non-modularized/test-execution");
		// Path dir =
		// Paths.get("/Users/hsh/ownCloud/continuity-eval-modularization-hpi/exp-modularization/2-reference-load-test");

		IdpaYamlSerializer<Application> serializer = new IdpaYamlSerializer<>(Application.class);
		Application application = serializer.readFromYaml(Paths.get("/Users/hsh/git", "ModularizationExperimentArtifacts", "experiment-conductor", "idpa", "application-sock-shop.yml"));

		List<Trace> traces = OPENxtraceUtils.deserializeIntoTraceList(new String(Files.readAllBytes(dir.resolve("open-xtraces.json"))));
		JMeterLog jmeterLog = JMeterLog.fromCsv(Files.readAllLines(dir.resolve("load-test-report.csv"), Charset.defaultCharset()));
		OpenXtraceJmeterMerger merger = new OpenXtraceJmeterMerger(new SimpleDataHolder<>("jmeter", jmeterLog), new SimpleDataHolder<>("traces", traces), StaticDataHolder.of(application));

		// merger.execute(new Context());
		merger.test2();
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
				traces = OPENxtraceUtils.deserializeIntoTraceList(new String(Files.readAllBytes(dir.resolve(subFolder).resolve("test-execution").resolve("open-xtraces.json"))));
			} catch (JSONException e) {
				e.printStackTrace();
				continue;
			}

			JMeterLog jmeterLog = JMeterLog.fromCsv(Files.readAllLines(dir.resolve(subFolder).resolve("test-execution").resolve("load-test-report.csv"), Charset.defaultCharset()));
			OpenXtraceJmeterMerger merger = new OpenXtraceJmeterMerger(new SimpleDataHolder<>("jmeter", jmeterLog), new SimpleDataHolder<>("traces", traces), NoopDataHolder.instance());

			merger.test();

			System.out.println();
		}
	}

	private void test() throws AbortInnerException {
		Collections.sort(jmeterLog.get().getEntries(), (a, b) -> getTimestampPlus1h(a).compareTo(getTimestampPlus1h(b)));
		Collections.sort(traces.get(), (a, b) -> Long.compare(a.getRoot().getRoot().getTimestamp(), b.getRoot().getRoot().getTimestamp()));

		Date start = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).min().getAsLong() - 3600000);
		Date end = new Date(traces.get().stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).max().getAsLong() - 3600000);
		List<JMeterLogEntry> filteredEntries = jmeterLog.get().stream().filter(e -> getTimestampPlus1h(e).after(start) && getTimestampPlus1h(e).before(end)).collect(Collectors.toList());

		System.out.println("JMeter: " + filteredEntries.stream().count());
		// System.out.println("JMeter (catalogueGetTagsUsingGET): " +
		// jmeterLog.get().stream().filter(entry ->
		// "catalogueGetTagsUsingGET".equals(entry.getValue("label"))).count());
		Date jmeterFirstDate = getTimestampPlus1h(filteredEntries.get(0));
		Date jmeterLastDate = getTimestampPlus1h(filteredEntries.get(filteredEntries.size() - 1));
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

	private void test2() throws AbortInnerException {
		System.out.println("#### Analysis ####");
		System.out.println("------------------");
		System.out.println();

		labelRequestsWitEndpoints();
		List<Trace> labeledTraces = new ArrayList<>();
		List<Trace> unlabeledTraces = new ArrayList<>();

		traces.get().forEach(t -> {
			if (LABEL_UNKNOWN.equals(getBt(t))) {
				unlabeledTraces.add(t);
			} else {
				labeledTraces.add(t);
			}
		});

		System.out.println("Unlabeled traces:");
		summarize(unlabeledTraces.stream(), this::getUri).entrySet().forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));
		System.out.println();

		Date start = new Date(labeledTraces.stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).min().getAsLong());
		Date end = new Date(labeledTraces.stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).max().getAsLong());

		List<JMeterLogEntry> filteredEntries = jmeterLog.get().stream().filter(e -> getTimestampPlus1h(e).after(start) && getTimestampPlus1h(e).before(end)).collect(Collectors.toList());

		System.out.println("Erroneous JMeter requests:");

		List<String> okCodes = java.util.Arrays.asList("200", "201");

		filteredEntries.stream().map(JMeterLogEntry::getTimestamp).mapToLong(Date::getTime).min().getAsLong();

		filteredEntries.stream().filter(req -> !okCodes.contains(req.getValue("responseCode"))).collect(groupingBy(e -> e.getValue("label"))).entrySet().forEach(entry -> {
			System.out.print("- ");
			System.out.print(entry.getKey());
			System.out.print(":\t");
			System.out.print(entry.getValue().size());
			System.out.print(" (");
			System.out.print(new Date(entry.getValue().stream().map(JMeterLogEntry::getTimestamp).mapToLong(Date::getTime).min().getAsLong()));
			System.out.print(" - ");
			System.out.print(new Date(entry.getValue().stream().map(JMeterLogEntry::getTimestamp).mapToLong(Date::getTime).max().getAsLong()));
			System.out.println(")");
		});

		Map<String, Long> jmeterRequestsPerLabel = summarize(filteredEntries.stream().filter(req -> okCodes.contains(req.getValue("responseCode"))), e -> e.getValue("label"));
		Map<String, List<Trace>> tracesPerBt = labeledTraces.stream().collect(groupingBy(this::getBt));

		int maxLength = jmeterRequestsPerLabel.keySet().stream().mapToInt(String::length).max().orElse(0);

		System.out.println();
		System.out.println("Matched requests:");

		DecimalFormat percentFormat = new DecimalFormat("##0.00");

		jmeterRequestsPerLabel.keySet().forEach(label -> {
			long jmeterRequests = jmeterRequestsPerLabel.get(label);
			List<Trace> traceList = Optional.ofNullable(tracesPerBt.get(label)).orElse(Collections.emptyList());
			long traceRequests = traceList.size();

			String filler = new String(new char[maxLength - label.length()]).replace("\0", " ");

			System.out.print("- ");
			System.out.print(label);
			System.out.print(filler);
			System.out.print(":\t JMeter ");
			System.out.print(String.format("%1$6s", jmeterRequests));
			System.out.print(", Traces ");
			System.out.print(String.format("%1$6s", traceRequests));
			System.out.print(", diff ");
			System.out.print(String.format("%1$6s", jmeterRequests - traceRequests));
			System.out.print(" (");
			System.out.print(String.format("%1$6s", percentFormat.format((((double) jmeterRequests - traceRequests) / jmeterRequests) * 100.0)));
			System.out.print("%)\t Traces from ");
			System.out.print(new Date(traceList.stream().map(Trace::getRoot).map(SubTrace::getRoot).mapToLong(Callable::getTimestamp).min().getAsLong()));
			System.out.print(" to ");
			System.out.print(new Date(traceList.stream().map(Trace::getRoot).map(SubTrace::getRoot).mapToLong(Callable::getTimestamp).max().getAsLong()));
			System.out.println();
		});

		System.out.println();
		System.out.println("Unmatched requests:");

		Set<String> traceLabels = new HashSet<>(tracesPerBt.keySet());
		traceLabels.removeAll(jmeterRequestsPerLabel.keySet());
		traceLabels.forEach(label -> {
			System.out.print("- ");
			System.out.print(label);
			System.out.print(tracesPerBt.get(label));
		});
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		System.out.println("#### Analysis ####");
		System.out.println("------------------");
		System.out.println();

		labelRequestsWitEndpoints();
		List<Trace> labeledTraces = new ArrayList<>();
		List<Trace> unlabeledTraces = new ArrayList<>();

		traces.get().forEach(t -> {
			if (LABEL_UNKNOWN.equals(getBt(t))) {
				unlabeledTraces.add(t);
			} else {
				labeledTraces.add(t);
			}
		});

		System.out.println("Unlabeled traces:");
		summarize(unlabeledTraces.stream(), this::getUri).entrySet().forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));
		System.out.println();

		Date start = new Date(labeledTraces.stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).min().getAsLong());
		Date end = new Date(labeledTraces.stream().mapToLong(t -> t.getRoot().getRoot().getTimestamp()).max().getAsLong());

		List<JMeterLogEntry> filteredEntries = jmeterLog.get().stream().filter(e -> getTimestampPlus1h(e).after(start) && getTimestampPlus1h(e).before(end)).collect(Collectors.toList());

		Map<String, List<JMeterLogEntry>> entriesPerThread = splitByThreadAndSortByTimestamp(filteredEntries);
		Map<String, List<Trace>> requestsPerSession = splitBySessionAndSortByTimestamp(labeledTraces);

		for (Entry<String, List<JMeterLogEntry>> entry : entriesPerThread.entrySet()) {
			System.out.print("### Session ");
			System.out.print(entry.getKey());
			System.out.println(" ###");

			for (Triple<String, List<Trace>, List<String>> triple : findMatchingTraces(entry.getValue(), requestsPerSession)) {
				System.out.print(triple.getLeft());
				System.out.print(" - Missing: ");
				System.out.println(triple.getRight());
			}

			System.out.println();
		}
	}

	private <T> Map<String, Long> summarize(Stream<T> stream, Function<T, String> valueSupplier) {
		return stream.collect(groupingBy(t -> valueSupplier.apply(t), HashMap::new, mapping(t -> valueSupplier.apply(t), counting())));
	}

	/**
	 *
	 * @param thread
	 * @param requestsPerSession
	 * @return List of (sessionId, traces, skipped)
	 */
	private List<Triple<String, List<Trace>, List<String>>> findMatchingTraces(List<JMeterLogEntry> thread, Map<String, List<Trace>> requestsPerSession) {
		List<Triple<String, List<Trace>, List<String>>> result = new ArrayList<>();

		for (Entry<String, List<Trace>> entry : requestsPerSession.entrySet()) {
			List<Trace> session = entry.getValue();

			Iterator<JMeterLogEntry> threadIter = thread.iterator();
			Iterator<Trace> sessionIter = session.iterator();
			List<String> skipped = new ArrayList<>();

			boolean matching = true;

			while (matching && threadIter.hasNext() && sessionIter.hasNext()) {
				JMeterLogEntry jmeterReq = threadIter.next();
				Trace trace = sessionIter.next();

				while (!match(jmeterReq, trace) && threadIter.hasNext()) {
					skipped.add(jmeterReq.getValue("label"));
					jmeterReq = threadIter.next();
				}

				if (!match(jmeterReq, trace)) {
					matching = false;
				}
			}

			if (matching) {
				result.add(Triple.of(entry.getKey(), session, skipped));
			}
		}

		return result;
	}

	private boolean match(JMeterLogEntry jmeterReq, Trace trace) {
		boolean match = jmeterReq.getValue("label").equals(getBt(trace));
		match &= isApproximatelyBefore(getTimestampPlus1h(jmeterReq), new Date(trace.getRoot().getRoot().getTimestamp()));

		Date jmeterEndDate = DateUtils.addMilliseconds(getTimestampPlus1h(jmeterReq), jmeterReq.getIntValue("elapsed"));
		match &= isApproximatelyBefore(new Date(((HTTPRequestProcessing) trace.getRoot().getRoot()).getExitTime()), jmeterEndDate);

		return match;
	}

	private boolean isApproximatelyBefore(Date first, Date second) {
		return (first.getTime() - second.getTime()) < MAX_TIME_SHIFT;
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
			Collections.sort(entries, (a, b) -> getTimestampPlus1h(a).compareTo(getTimestampPlus1h(b)));
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

	private void labelRequestsWitEndpoints() throws AbortInnerException {
		RequestUriMapper mapper = new RequestUriMapper(application.get());

		for (Trace trace : traces.get()) {
			String label = LABEL_UNKNOWN;
			HttpEndpoint endpoint = mapper.map(getUri(trace), ((HTTPRequestProcessing) trace.getRoot().getRoot()).getRequestMethod().get().toString());

			if (endpoint != null) {
				label = endpoint.getId();
			}

			LocationImpl location;

			if (trace.getRoot().getLocation() == null) {
				location = new LocationImpl();
				((SubTraceImpl) trace.getRoot()).setLocation(location);
			} else {
				location = (LocationImpl) ((SubTraceImpl) trace.getRoot()).getLocation();
			}

			location.setBusinessTransaction(label);
		}
	}

	private String getBt(Trace trace) {
		return trace.getRoot().getLocation().getBusinessTransaction().get();
	}

	private String getUri(Trace trace) {
		return ((HTTPRequestProcessing) trace.getRoot().getRoot()).getUri();
	}

	private Date getTimestampPlus1h(JMeterLogEntry entry) {
		return DateUtils.addHours(entry.getTimestamp(), 1);
	}

}
