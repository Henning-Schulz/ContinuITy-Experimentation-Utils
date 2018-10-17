package org.continuity.experimentation.action;

import java.util.function.BooleanSupplier;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Subsumes means for aborting an experiment or an inner context under certain conditions.
 *
 * @author Henning Schulz
 *
 */
public class Abort {

	private Abort() {
	}

	/**
	 * Aborts the experiment when a condition holds.
	 *
	 * @param condition
	 *            The condition to be evaluated.
	 * @param message
	 *            A message to be contained in the thrown exception.
	 * @return An action to be used for conditional aborting.
	 */
	public static IExperimentAction allIf(BooleanSupplier condition, String message) {
		return new AbortIf(condition, message);
	}

	/**
	 * Aborts the current inner context of the experiment when a condition holds.
	 *
	 * @param condition
	 *            The condition to be evaluated.
	 * @param message
	 *            A message to be contained in the thrown exception.
	 * @return An action to be used for conditional aborting.
	 */
	public static IExperimentAction innerIf(BooleanSupplier condition, String message) {
		return new AborInnertIf(condition, message);
	}

	private abstract static class AbstractAbortIf implements IExperimentAction {

		private final BooleanSupplier condition;

		private final String message;

		public AbstractAbortIf(BooleanSupplier condition, String message) {
			this.condition = condition;
			this.message = message;
		}

		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			if (condition.getAsBoolean()) {
				throwException(context, message);
			}
		}

		protected abstract void throwException(Context context, String message) throws AbortInnerException, AbortException;

	}

	private static class AbortIf extends AbstractAbortIf {

		public AbortIf(BooleanSupplier condition, String message) {
			super(condition, message);
		}

		@Override
		protected void throwException(Context context, String message) throws AbortInnerException, AbortException {
			throw new AbortException(context, message);
		}

	}

	private static class AborInnertIf extends AbstractAbortIf {

		public AborInnertIf(BooleanSupplier condition, String message) {
			super(condition, message);
		}

		@Override
		protected void throwException(Context context, String message) throws AbortInnerException, AbortException {
			throw new AbortInnerException(context, message);
		}

	}

}
