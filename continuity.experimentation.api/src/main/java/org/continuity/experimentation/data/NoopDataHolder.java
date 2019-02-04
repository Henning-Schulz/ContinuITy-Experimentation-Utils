package org.continuity.experimentation.data;

import org.continuity.experimentation.exception.AbortInnerException;

/**
 * Data holder that does nothing. If {@link #get()} is called, {@code null} will be returned.
 *
 * @author Henning Schulz
 *
 * @param <T>
 */
public class NoopDataHolder<T> implements IDataHolder<T> {

	@SuppressWarnings("rawtypes")
	private static final NoopDataHolder INSTANCE = new NoopDataHolder<>();

	private NoopDataHolder() {
	}

	@SuppressWarnings("unchecked")
	public static <T> NoopDataHolder<T> instance() {
		return INSTANCE;
	}

	@Override
	public void set(T data) {
	}

	@Override
	public T get() throws AbortInnerException {
		return null;
	}

	@Override
	public boolean isSet() {
		return false;
	}

	@Override
	public void invalidate() {
	}

}
