package org.continuity.experimentation.action;

import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;

/**
 * Encapsulates an {@link AppendContext} and {@link RemoveContext} for one context.
 *
 * @author Henning Schulz
 *
 */
public class ContextChange {

	private final IDataHolder<String> contextHolder;

	public ContextChange(String context) {
		this.contextHolder = StaticDataHolder.of(context);
	}

	public ContextChange(IDataHolder<String> contextHolder) {
		this.contextHolder = contextHolder;
	}

	public AppendContext append() {
		return new AppendContext(contextHolder);
	}

	public RemoveContext remove() {
		return new RemoveContext(contextHolder);
	}

	public RenameCurrentContext renameCurrent() {
		return new RenameCurrentContext(contextHolder);
	}

}
