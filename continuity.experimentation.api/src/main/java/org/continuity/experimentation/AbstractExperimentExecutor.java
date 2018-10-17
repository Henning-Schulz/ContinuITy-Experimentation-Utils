package org.continuity.experimentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.continuity.experimentation.action.EmailReport;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subsumes common means for executing {@link IExperimentElement} chains.
 *
 * @author Henning Schulz
 *
 */
public abstract class AbstractExperimentExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractExperimentExecutor.class);

	private final IExperimentElement first;

	private List<Exception> caughtExceptions = new ArrayList<>();
	private AbortException abortException = null;

	private Context context;

	protected AbstractExperimentExecutor(IExperimentElement first) {
		this.first = first;
	}

	/**
	 * Gets {@link #first}.
	 *
	 * @return {@link #first}
	 */
	public IExperimentElement getFirst() {
		return this.first;
	}

	/**
	 * Executes the experiment.
	 *
	 * @throws AbortException
	 *
	 */
	public void execute() throws AbortException {
		execute(new Context());
	}

	/**
	 * Executes the experiment in an initial context.
	 *
	 * @param initialContext
	 *            The initial context.
	 * @throws AbortException
	 */
	public void execute(Context initialContext) throws AbortException {
		this.context = initialContext;

		IExperimentElement current = first;

		while ((current != null) && !current.isEnd()) {
			current.updateContext(context);

			context.toPath().toFile().mkdirs();

			if (current.hasAction()) {
				try {
					current.getAction().execute(context);
				} catch (AbortInnerException e) {
					caughtExceptions.add(e);
					current = handleAbortInnerException(e, context);
					continue;
				} catch (AbortException e) {
					abort(e);
				} catch (Exception e) {
					caughtExceptions.add(e);
					LOGGER.warn("Action '{}' threw an exception. Ignoring and continuing.", current.getAction(), e);
				}
			}

			current = current.getNext();
		}
	}

	private IExperimentElement handleAbortInnerException(AbortInnerException e, Context context) throws AbortException {
		IExperimentElement element = context.resetToUpmostElement();

		if (element != null) {
			IExperimentElement next = element.handleAborted(e);

			if (next == null) {
				handleAbortInnerException(e, context);
			} else {
				return next;
			}
		} else {
			AbortException abortException = new AbortException(context, e);
			abort(abortException);
		}

		return null;
	}

	private void abort(AbortException exception) throws AbortException {
		LOGGER.error("The experiment has been aborted!", exception);

		this.abortException = exception;
		try {
			EmailReport.send().execute(context);
		} catch (Exception e) {
			LOGGER.error("Could not send final report!", e);
		}

		throw exception;
	}

	public ExperimentReport createReport() {
		List<Exception> uncaughtExceptions;

		if (abortException == null) {
			uncaughtExceptions = Collections.emptyList();
		} else {
			uncaughtExceptions = Collections.singletonList(abortException);
		}

		ExperimentReport report = new ExperimentReport(context, caughtExceptions, uncaughtExceptions);
		caughtExceptions = new ArrayList<>();
		return report;
	}

}
