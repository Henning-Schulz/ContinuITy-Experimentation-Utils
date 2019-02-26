package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Does nothing. To be used as a placeholder.
 *
 * @author Henning Schulz
 *
 */
public class NoopAction implements IExperimentAction {

	public static final NoopAction INSTANCE = new NoopAction();

	private NoopAction() {
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
	}

}
