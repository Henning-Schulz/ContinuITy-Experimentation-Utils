package org.continuity.experimentation.data;

import java.util.Objects;

import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Holds a static prefix and increments a counter each time {@link #invalidate()} is called.
 *
 * @author Henning Schulz
 *
 */
public class CountingDataHolder implements IDataHolder<String> {

	private final String prefix;

	private int counter = 1;

	private CountingDataHolder(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Creates a new instance.
	 *
	 * @param prefix
	 *            The prefix to be used. Will be transformed to a string using {@link #toString()}.
	 * @return The new data holder instance.
	 */
	public static CountingDataHolder of(Object prefix) {
		return new CountingDataHolder(Objects.toString(prefix));
	}

	@Override
	public void set(String data) {
		// do nothing
	}

	@Override
	public String get() throws AbortInnerException {
		return prefix + "-" + counter;
	}

	@Override
	public boolean isSet() {
		return true;
	}

	@Override
	public void invalidate() {
		counter++;
	}

	@Override
	public String toString() {
		return "Counting: " + prefix + "-" + counter;
	}

}
