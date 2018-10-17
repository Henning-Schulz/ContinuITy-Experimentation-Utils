package org.continuity.experimentation.data;

/**
 * Holder that simply stores the content and replaces it if {@link #set(Object)} is called a second
 * time.
 *
 * @author Henning Schulz
 *
 */
public class SimpleDataHolder<T> extends AbstractDataHolder<T> {

	private T data;

	private final T defaultValue;

	public SimpleDataHolder(String name, Class<T> dataType) {
		super(name, dataType);
		this.defaultValue = null;
	}

	public SimpleDataHolder(String name, T initialData) {
		this(name, initialData, false);
	}

	@SuppressWarnings("unchecked")
	public SimpleDataHolder(String name, T initialData, boolean useInitialAsDefault) {
		super(name, (Class<T>) initialData.getClass());

		if (useInitialAsDefault) {
			this.defaultValue = initialData;
		} else {
			this.defaultValue = null;
		}

		this.data = initialData;
		notifyWrite();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected T getWithoutNotification() {
		return this.data;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setWithoutNotification(T data) {
		this.data = data;
	}

	@Override
	public void invalidate() {
		super.invalidate();

		if (defaultValue != null) {
			set(defaultValue);
		}
	}

}
