package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes a context.
 *
 * @author Henning Schulz
 *
 */
public class RemoveContext implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveContext.class);

	private final IDataHolder<String> contextHolder;

	public RemoveContext(IDataHolder<String> contextHolder) {
		this.contextHolder = contextHolder;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		context.remove(this.contextHolder.get());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Remove context \"" + contextHolder + "\"";
	}

}
