package org.continuity.experimentation.data;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves items from a predefined list in a sequential manner. In the beginning, it will return
 * the first item of the list. Each call to {@link #invalidate()} will replace it with the next
 * item. Calls to {@link #set(Object)} will add a new element to the list that will be returned
 * after the formerly last element.
 *
 * @author Henning Schulz
 *
 * @param <T>
 */
public class SequentialListDataHolder<T> implements IDataHolder<T> {

	private static final Logger LOGGER = LoggerFactory.getLogger(SequentialListDataHolder.class);

	private final String name;
	private final Collection<T> originalItems;
	private Queue<T> queue;

	private T current;

	public SequentialListDataHolder(String name, Collection<T> items) {
		this.name = name;
		this.originalItems = items;

		invalidate();
	}

	@Override
	public void set(T data) {
		queue.offer(data);
	}

	@Override
	public T get() throws AbortInnerException {
		return current;
	}

	@Override
	public boolean isSet() {
		return current != null;
	}

	/**
	 * Resets the list to the originally first item.
	 */
	@Override
	public void invalidate() {
		this.queue = new LinkedList<>(originalItems);
		next(null);
	}

	/**
	 * Replaces the current item with the next one.
	 *
	 * @param context
	 *            The context. For usage as a lambda action.
	 */
	public void next(Context context) {
		current = queue.poll();

		if (current == null) {
			LOGGER.warn("[{}] There is no next item!", name);
		}
	}

	/**
	 * Returns the number of currently contained items.
	 *
	 * @return the number of contained items.
	 */
	public int size() {
		return queue.size();
	}

	@Override
	public String toString() {
		return name + ": currently \'" + current;
	}

}
