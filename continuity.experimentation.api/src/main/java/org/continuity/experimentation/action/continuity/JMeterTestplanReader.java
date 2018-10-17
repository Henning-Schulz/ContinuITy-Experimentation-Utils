package org.continuity.experimentation.action.continuity;

import java.io.File;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.ListedHashTree;
import org.continuity.api.entities.artifact.JMeterTestPlanBundle;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.markov4jmeter.testplangenerator.util.CSVHandler;

public class JMeterTestplanReader implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterTestplanReader.class);

	private final CSVHandler csvHandler = new CSVHandler(CSVHandler.LINEBREAK_TYPE_UNIX);

	private final IDataHolder<Path> dirPath;

	private final IDataHolder<JMeterTestPlanBundle> testplanBundle;

	/**
	 * Use {@link JMeterTestplan#read(IDataHolder, IDataHolder)} instead of this constructor.
	 *
	 * @param dirPath
	 *            [in] Path to the directory holding the test plan. The directory must hold exactly
	 *            one file ending with .jmx and potentially multiple files ending with .csv as
	 *            behavior models.
	 * @param testplanBundle
	 *            [out] The read test plan.
	 */
	protected JMeterTestplanReader(IDataHolder<Path> dirPath, IDataHolder<JMeterTestPlanBundle> testplanBundle) {
		this.dirPath = dirPath;
		this.testplanBundle = testplanBundle;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		File dir = dirPath.get().toFile();

		LOGGER.info("Reading the test plan from directory {}...", dir);

		if (!dir.exists() || !dir.isDirectory()) {
			throw new NotDirectoryException(dirPath.get().toAbsolutePath().toString());
		}

		ListedHashTree testPlan = null;
		Map<String, String[][]> behaviors = new HashMap<>();

		for (File file : dir.listFiles()) {
			if (file.getName().endsWith(".jmx")) {
				testPlan = (ListedHashTree) SaveService.loadTree(file);
			} else if (file.getName().endsWith(".csv")) {
				behaviors.put(file.getName(), csvHandler.readValues(file.getAbsolutePath()));
			}
		}

		if (testPlan == null) {
			throw new AbortInnerException(context, "No .jmx test plan found!");
		}

		testplanBundle.set(new JMeterTestPlanBundle(testPlan, behaviors));

		LOGGER.info("Successfully read the test plan {} from directory {}.", testplanBundle, dir);
	}

	@Override
	public String toString() {
		return "Read the testplan from \"" + dirPath + "\" into \"" + testplanBundle + "\".";
	}

}
