package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends a context.
 *
 * @author Henning Schulz
 *
 */
public class AppendContext implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppendContext.class);

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
		LOGGER.info("Changed context to {} by appending {}.", context, contextHolder.get());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Append \"" + contextHolder + "\"";
	}

}
