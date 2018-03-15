package org.continuity.experimentation.action.continuity;

import java.nio.file.Path;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.action.continuity.JMeterTestPlanExecution.TestPlanBundle;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges a markov chain into a test plan.
 *
 * @author Henning Schulz
 *
 */
public class MarkovChainIntoTestPlan implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(MarkovChainIntoTestPlan.class);

	private final IDataHolder<Path> testPlanPathHolder;

	private final IDataHolder<String[][]> markovChainHolder;

	private final String behaviorModelKey;

	private final IDataHolder<TestPlanBundle> outputHolder;

	private MarkovChainIntoTestPlan(IDataHolder<Path> testPlanPathHolder, IDataHolder<String[][]> markovChainHolder, String behaviorModelKey, IDataHolder<TestPlanBundle> outputHolder) {
		this.testPlanPathHolder = testPlanPathHolder;
		this.markovChainHolder = markovChainHolder;
		this.behaviorModelKey = behaviorModelKey;
		this.outputHolder = outputHolder;
	}

	/**
	 * Gets an action for merging the specified Markov chain into a test plan.
	 *
	 * @param testPlanPathHolder
	 *            [in] Holds the test plan.
	 * @param markovChainHolder
	 *            [in] Holds the Markov chain.
	 * @param behaviorModelKey
	 *            Holds the key to be used for the behavior model in the test plan (has to be the
	 *            same as specified in the test plan).
	 * @param outputHolder
	 *            [out] Will hold the test plan bundle with the Markov chain.
	 * @return An action to be executed for merging the test plan with the Markov chain.
	 */
	public static MarkovChainIntoTestPlan merge(IDataHolder<Path> testPlanPathHolder, IDataHolder<String[][]> markovChainHolder, String behaviorModelKey, IDataHolder<TestPlanBundle> outputHolder) {
		return new MarkovChainIntoTestPlan(testPlanPathHolder, markovChainHolder, behaviorModelKey, outputHolder);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		LOGGER.info("Merging the Markov chain {} into test plan {} with key {}.", markovChainHolder, testPlanPathHolder, behaviorModelKey);

		TestPlanBundle bundle = new TestPlanBundle(testPlanPathHolder.get().toFile());
		bundle.getBehaviors().clear();
		bundle.getBehaviors().put(behaviorModelKey + ".csv", markovChainHolder.get());

		outputHolder.set(bundle);

		LOGGER.info("Markov chain and test plan merged.");
	}

	@Override
	public String toString() {
		return "Merge Markov chain " + markovChainHolder + " into test plan " + testPlanPathHolder + " with key " + behaviorModelKey + " and store the result in " + outputHolder;
	}

}
