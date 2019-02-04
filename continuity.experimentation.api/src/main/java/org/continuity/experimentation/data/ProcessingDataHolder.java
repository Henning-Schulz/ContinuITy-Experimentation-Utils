package org.continuity.experimentation.data;

import java.util.function.Function;

import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data holder that processes the content of another holder in a defined way.
 *
 * @author Henning Schulz
 *
 * @param <I>
 *            The type of the input data holder.
 * @param <O>
 *            The output type.
 */
public class ProcessingDataHolder<I, O> implements IDataHolder<O> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingDataHolder.class);

	private final String name;

	private final IDataHolder<I> input;

	private final Function<I, O> processor;

	public ProcessingDataHolder(String name, IDataHolder<I> input, Function<I, O> processor) {
		this.name = name;
		this.input = input;
		this.processor = processor;
	}

	@Override
	public void set(O data) {
		LOGGER.warn("Tried to set the content to {}, but doesn't have any effect.", data);
	}

	@Override
	public O get() throws AbortInnerException {
		return processor.apply(input.get());
	}

	@Override
	public boolean isSet() {
		return input.isSet();
	}

	@Override
	public void invalidate() {
	}

	@Override
	public String toString() {
		return name + ": Processing '" + input + "'";
	}

}
