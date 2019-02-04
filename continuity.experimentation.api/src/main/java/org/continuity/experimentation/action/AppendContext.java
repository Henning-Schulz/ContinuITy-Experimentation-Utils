package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Appends a context.
 *
 * @author Henning Schulz
 *
 */
public class AppendContext implements IExperimentAction {

	private final IDataHolder<String> contextHolder;

	public AppendContext(IDataHolder<String> contextHolder) {
		this.contextHolder = contextHolder;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		context.append(this.contextHolder.get());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Append \"" + contextHolder + "\"";
	}

}
