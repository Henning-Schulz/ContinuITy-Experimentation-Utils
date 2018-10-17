package org.continuity.experimentation.data;

import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Common interface for holders of data to be exchanged between experiment actions.
 *
 * @author Henning Schulz
 *
 */
public interface IDataHolder<T> {

	/**
	 * Sets the content.
	 *
	 * @param data
	 *            Content to be set.
	 */
	void set(T data);

	/**
	 * Reads the content.
	 *
	 * @return Contained content.
	 * @throws AbortInnerException
	 */
	T get() throws AbortInnerException;

	/**
	 * Returns whether the data already has been set.
	 *
	 * @return {@code true} if the data has been set or {@code false} otherwise.
	 */
	boolean isSet();

	/**
	 * Returns whether the data has not been set yet.
	 *
	 * @return {@code false} if the data has been set or {@code true} otherwise.
	 */
	default boolean isNotSet() {
		return !isSet();
	}

	/**
	 * Invalidates the stored data.
	 */
	void invalidate();

}
