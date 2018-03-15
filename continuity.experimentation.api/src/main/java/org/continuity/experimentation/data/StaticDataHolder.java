package org.continuity.experimentation.data;

import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data holder that holds a single value that won't change.
 *
 * @author Henning Schulz
 *
 * @param <T>
 *            Type of the held data.
 */
public class StaticDataHolder<T> implements IDataHolder<T> {

	private static final Logger LOGGER = LoggerFactory.getLogger(StaticDataHolder.class);

	private final T content;

	private StaticDataHolder(T content) {
		this.content = content;
	}

	/**
	 * Creates a new data holder.
	 *
	 * @param content
	 *            The content to be held.
	 * @return The data holder.
	 */
	public static <T> StaticDataHolder<T> of(T content) {
		return new StaticDataHolder<>(content);
	}

	@Override
	public void set(T data) {
		LOGGER.warn("Tried to set the content to {}, but doesn't have any effect.", data);
	}

	@Override
	public T get() throws AbortInnerException {
		return content;
	}

	@Override
	public boolean isSet() {
		return true;
	}

	@Override
	public void invalidate() {
		LOGGER.warn("Tried to invalidate the content, but doesn't have any effect.");
	}

	@Override
	public String toString() {
		return "Static: " + content;
	}

}
