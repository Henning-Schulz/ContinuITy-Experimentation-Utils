package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenameCurrentContext implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(RenameCurrentContext.class);

	private final IDataHolder<String> contextHolder;

	public RenameCurrentContext(IDataHolder<String> contextHolder) {
		this.contextHolder = contextHolder;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		String[] contextParts = context.toString().split("\\-");
		String current = contextParts[contextParts.length - 1];

		context.remove(current);
		context.append(contextHolder.get());

		LOGGER.info("Changed context to {} by renaming the last part to {}.", context, contextHolder.get());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Rename current context to \"" + contextHolder + "\"";
	}

}
