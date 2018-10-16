package org.continuity.experimentation.experiment.changes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

public class ChangeCounter {

	private static final Map<String, String> TYPES = new HashMap<>();

	private static final Map<String, Integer> WEIGHTS = new HashMap<>();

	private static final double SCALE_FACTOR = 0.276445698;

	static {
		TYPES.put("- !<counter>", "CounterInput");
		TYPES.put("- !<direct>", "DirectInput");
		TYPES.put("  - from: ", "RegExExtraction");
		TYPES.put("- !<extracted>", "ExtractedInput");
		TYPES.put("- interface: ", "InterfaceAnnotation");
		TYPES.put("  - parameter: ", "ParameterAnnotation");

		WEIGHTS.put("CounterInput", 4);
		WEIGHTS.put("DirectInput", 2);
		WEIGHTS.put("RegExExtraction", 8);
		WEIGHTS.put("ExtractedInput", 2);
		WEIGHTS.put("InterfaceAnnotation", 1);
		WEIGHTS.put("ParameterAnnotation", 1);
	}

	public static void main(String[] args) throws IOException {
		List<String> table = new ArrayList<>();
		String header = "version;type;operation;amount;lines;scaled_lines;weight;overhead";
		table.add(header);

		for (int i = 1; i <= 20; i++) {
			table.addAll(countChanges(i));
		}

		System.out.println(table.stream().reduce((a, b) -> a + "\n" + b).get());

		Files.write(Paths.get("/Users/hsh/ownCloud/MyData/ContinuITy/SUTs/BroadleafHeatClinic/heat-clinic-versions/changes-classic.csv"), table, StandardOpenOption.CREATE);
	}

	private static List<String> countChanges(int version) throws IOException {
		String path = "/Users/hsh/ownCloud/MyData/ContinuITy/SUTs/BroadleafHeatClinic/heat-clinic-versions/v" + version + "/annotation-heat-clinic.yml";

		List<String> lines = Files.readAllLines(Paths.get(path));

		List<Pair<String, Integer>> startIndexes = new ArrayList<>();
		int idx = 0;

		for (String line : lines) {
			for (String start : TYPES.keySet()) {
				if (line.startsWith(start)) {
					startIndexes.add(Pair.of(TYPES.get(start), idx));
				}
			}

			idx++;
		}

		List<String> rows = new ArrayList<>();

		for (int i = 0; i < (startIndexes.size() - 1); i++) {
			Pair<String, Integer> thisPair = startIndexes.get(i);
			Pair<String, Integer> nextPair = startIndexes.get(i + 1);

			rows.add(toRow(version, thisPair.getLeft(), nextPair.getRight() - thisPair.getRight()));
		}

		Pair<String, Integer> lastPair = startIndexes.get(startIndexes.size() - 1);
		rows.add(toRow(version, lastPair.getLeft(), lines.size() - lastPair.getRight()));

		return rows;
	}

	private static String toRow(int version, String type, int lines) {
		double scaledLines = SCALE_FACTOR * lines;
		int weight = WEIGHTS.get(type);
		double overhead = weight * (1.0 + scaledLines);

		return version + ";" + type + ";ADD;1;" + lines + ";" + scaledLines + ";" + weight + ";" + overhead;
	}

}
