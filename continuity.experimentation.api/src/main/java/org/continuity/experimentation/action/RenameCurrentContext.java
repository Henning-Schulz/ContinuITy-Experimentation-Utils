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

	private String from;

	private String to;

	private boolean renameBack = false;

	public RenameCurrentContext(IDataHolder<String> to) {
		this.contextHolder = to;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AbortInnerException
	 */
	@Override
	public void execute(Context context) throws AbortInnerException {
		if (!renameBack) {
			String[] contextParts = context.toString().split("\\" + Context.SEPARATOR);
			from = contextParts[contextParts.length - 1];
			to = contextHolder.get();

			context.remove(from);
			context.append(to);

			LOGGER.info("Changed context to {} by renaming the last part to {}.", context, to);

			renameBack = true;
		} else {
			context.remove(to);
			context.append(from);

			LOGGER.info("Changed context to {} by renaming the last part back to {}.", context, from);

			renameBack = false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Rename current context to \"" + contextHolder + "\"";
	}

}
