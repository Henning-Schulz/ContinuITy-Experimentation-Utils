package org.continuity.experimentation.experiment.apichanger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarkovTemplate {

	private final List<List<String>> template;

	public MarkovTemplate(Path allowedTransitionsFilePath) throws IOException {
		this.template = readMatrixTemplate(allowedTransitionsFilePath);
	}

	public List<String> getRow(String rowName) {
		for (List<String> row : template) {
			if (row.get(0).equals(rowName)) {
				return row;
			}
		}

		return null;
	}

	public void insertRow(List<String> row) {
		template.add(row);
	}

	public void removeRow(String rowName) {
		template.remove(getRow(rowName));
	}

	public List<String> getColumn(String columnName) {
		int colIdx = getColumnIndex(columnName);

		if (colIdx < 0) {
			return null;
		}

		List<String> column = new ArrayList<>();

		for (List<String> row : template) {
			column.add(row.get(colIdx));
		}

		return column;
	}

	public void insertColumn(List<String> column) {
		int rowIdx = 0;
		int colIdx = template.get(0).size() - 1; // Last is $

		for (List<String> row : template) {
			row.add(colIdx, column.get(rowIdx));
			rowIdx++;
		}
	}

	public void removeColumn(String columnName) {
		int colIdx = getColumnIndex(columnName);

		if (colIdx < 0) {
			return;
		}

		for (List<String> row : template) {
			row.remove(colIdx);
		}
	}

	private int getColumnIndex(String columnName) {
		int colIdx = 0;
		boolean found = false;

		for (String columnHead : template.get(0)) {
			if (columnHead.equals(columnName)) {
				found = true;
				break;
			}

			colIdx++;
		}

		return found ? colIdx : -1;
	}

	public void writeToFile(Path path) throws IOException {
		List<String> lines = template.stream().map(l -> l.stream().reduce((a, b) -> a + "," + b).get()).collect(Collectors.toList());

		Files.write(path, lines, StandardOpenOption.CREATE);
	}

	private List<List<String>> readMatrixTemplate(Path allowedTransitionsFilePath) throws IOException {
		List<List<String>> matrixAsList = new ArrayList<>();

		for (String line : Files.readAllLines(allowedTransitionsFilePath)) {
			matrixAsList.add(new ArrayList<>(Arrays.asList(line.split("\\,"))));
		}

		return matrixAsList;
	}

}
