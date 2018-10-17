package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Creates a random Markov chain based on the specified allowed transitions.
 *
 * @author Henning Schulz
 *
 */
public class RandomMarkovChain implements IExperimentAction {

	private final IDataHolder<Path> allowedTransitionsFilePath;

	private final long averageThinkTimeMs;

	private final IDataHolder<String[][]> outputDataHolder;

	private final Random random = new Random();

	private final DecimalFormat decimalFormat = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.ENGLISH));

	private RandomMarkovChain(IDataHolder<Path> allowedTransitionsFilePath, long averageThinkTimeMs, IDataHolder<String[][]> outputDataHolder) {
		this.allowedTransitionsFilePath = allowedTransitionsFilePath;
		this.averageThinkTimeMs = averageThinkTimeMs;
		this.outputDataHolder = outputDataHolder;
	}

	/**
	 * Gets an action to create a random Markov chain based on the specified parameters. <br>
	 * <br>
	 *
	 * The {@code allowedTransitions} matrix has to have the following format, separated by ',':
	 * <br>
	 * <table>
	 * <tr>
	 * <td></td>
	 * <td>INITIAL</td>
	 * <td>foo</td>
	 * <td>bar</td>
	 * <td>$</td>
	 * </tr>
	 * <tr>
	 * <td>INITIAL*</td>
	 * <td>0</td>
	 * <td>1</td>
	 * <td>1</td>
	 * <td>0</td>
	 * </tr>
	 * <tr>
	 * <td>foo</td>
	 * <td>0</td>
	 * <td>1</td>
	 * <td>0</td>
	 * <td>1</td>
	 * </tr>
	 * <tr>
	 * <td>bar</td>
	 * <td>0</td>
	 * <td>0</td>
	 * <td>1</td>
	 * <td>1</td>
	 * </tr>
	 * </table>
	 *
	 * @param allowedTransitionsFilePath
	 *            Holds the allowed transitions from each state to each other (1=allowed, 0=not
	 *            allowed).
	 * @param averageThinkTimeMs
	 *            The average think time to be used. Will be multiplied with a random factor between
	 *            0.5 and 1.5 for each transition.
	 * @param outputDataHolder
	 *            The data holder that will hold the created Markov chain.
	 * @return The action to be used for creating the Markov chain.
	 */
	public static RandomMarkovChain create(IDataHolder<Path> allowedTransitionsFilePath, long averageThinkTimeMs, IDataHolder<String[][]> outputDataHolder) {
		return new RandomMarkovChain(allowedTransitionsFilePath, averageThinkTimeMs, outputDataHolder);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		String[][] template = readMatrixTemplate();

		int[][] allowedTransitions = extractAllowedTransitions(template);
		String[][] markovChain = new String[template.length][template[0].length];

		markovChain[0] = template[0];

		// transitions from INITIAL
		markovChain[1] = createBehaviorRow(allowedTransitions[0], template[1][0], 0);

		for (int row = 2; row < markovChain.length; row++) {
			markovChain[row] = createBehaviorRow(allowedTransitions[row - 1], template[row][0], averageThinkTimeMs);
		}

		outputDataHolder.set(markovChain);

		saveMarkovChain(markovChain, context);
	}

	private String[] createBehaviorRow(int[] allowedTransitions, String requestName, long thinkTime) {
		double[] markovRow = createMarkovRow(allowedTransitions);

		String[] behaviorRow = new String[allowedTransitions.length + 1];
		behaviorRow[0] = requestName;

		for (int col = 1; col < (behaviorRow.length - 1); col++) {
			behaviorRow[col] = formatEntry(markovRow[col - 1], thinkTime);
		}

		// transitions to $
		behaviorRow[behaviorRow.length - 1] = formatEntry(markovRow[behaviorRow.length - 2], 0);

		return behaviorRow;
	}

	private String[][] readMatrixTemplate() throws IOException, AbortInnerException {
		List<String[]> matrixAsList = new ArrayList<>();

		for (String line : Files.readAllLines(allowedTransitionsFilePath.get())) {
			matrixAsList.add(line.split("\\,"));
		}

		return matrixAsList.toArray(new String[][] {});
	}

	private int[][] extractAllowedTransitions(String[][] template) {
		int[][] allowedTransitions = new int[template.length - 1][template[0].length - 1];

		for (int row = 0; row < allowedTransitions.length; row++) {
			for (int col = 0; col < allowedTransitions[0].length; col++) {
				allowedTransitions[row][col] = Integer.parseInt(template[row + 1][col + 1]);
			}
		}

		return allowedTransitions;
	}

	private double[] createMarkovRow(int[] allowedTransitions) {
		double[] row = new double[allowedTransitions.length];

		for (int i = 0; i < row.length; i++) {
			row[i] = random.nextInt(100) * allowedTransitions[i];
		}

		double sum = Arrays.stream(row).sum();

		for (int i = 0; i < row.length; i++) {
			row[i] = row[i] / sum;
		}

		return row;
	}

	private String formatEntry(double prob, long thinkTime) {
		return decimalFormat.format(prob) + "; " + createThinkTimeString(prob > 0 ? thinkTime : 0);
	}

	private String createThinkTimeString(long averageThinkTimeMs) {
		double factor = random.nextDouble() + 0.5;
		double thinkTime = averageThinkTimeMs * factor;
		return "norm(" + decimalFormat.format(thinkTime) + " " + decimalFormat.format(thinkTime / 2.0) + ")";
	}

	private void saveMarkovChain(String[][] markovChain, Context context) throws IOException {
		Path folder = context.toPath();
		String origFile = "random-markov-chain";
		int counter = 1;
		String currFile = origFile + ".csv";

		while (folder.resolve(currFile).toFile().exists()) {
			currFile = origFile + "-" + ++counter + ".csv";
		}

		Files.write(folder.resolve(currFile), Arrays.stream(markovChain).map(Arrays::stream).map(s -> s.reduce((a, b) -> a + "," + b)).map(Optional::get).collect(Collectors.toList()),
				StandardOpenOption.CREATE);
	}

	@Override
	public String toString() {
		return "Create random Markov chain based on " + allowedTransitionsFilePath + " with average think time " + averageThinkTimeMs + " and store it in " + outputDataHolder;
	}

}
