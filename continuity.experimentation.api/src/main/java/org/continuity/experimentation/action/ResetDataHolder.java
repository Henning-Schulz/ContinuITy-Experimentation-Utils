package org.continuity.experimentation.action;

import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;

/**
 * @author Henning Schulz
 *
 */
public class ResetDataHolder<T> implements IExperimentAction {

	private final IDataHolder<T> holder;

	private final T defaultValue;

	public ResetDataHolder(IDataHolder<T> holder, T defaultValue) {
		this.holder = holder;
		this.defaultValue = defaultValue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() {
		holder.set(defaultValue);
	}

}
