package org.continuity.experimentation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Stack;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current context. Each action can add a context that will be appended to the current
 * one. A context could be {@code iteration#3/thread#1/foo}.
 *
 * @author Henning Schulz
 *
 */
public class Context implements Cloneable {

	public static final String SEPARATOR = "|";

	private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

	private final Stack<Pair<IExperimentElement, String>> contextStack;

	private String rootSuffix = "";

	public Context() {
		this.contextStack = new Stack<>();
	}

	protected Context(Stack<Pair<IExperimentElement, String>> contextStack) {
		this.contextStack = contextStack;
	}

	/**
	 * Gets {@link #contextStack}.
	 *
	 * @return {@link #contextStack}
	 */
	protected Stack<Pair<IExperimentElement, String>> getContextStack() {
		return this.contextStack;
	}

	/**
	 * Sets {@link #rootSuffix}.
	 *
	 * @param rootSuffix
	 *            New value for {@link #rootSuffix}
	 */
	public void setRootSuffix(String rootSuffix) {
		this.rootSuffix = rootSuffix;
	}

	/**
	 * Appends a context.
	 *
	 * @param context
	 *            The context to be appended.
	 */
	public void append(String context) {
		append(null, context);
	}

	/**
	 * Appends a context.
	 *
	 * @param element
	 *            The element corresponding to the context.
	 * @param context
	 *            The context to be appended.
	 */
	public void append(IExperimentElement element, String context) {
		contextStack.push(new ImmutablePair<>(element, context));
		LOGGER.info("Changed context to {}", toString());
	}

	/**
	 * Removes the current context if it is equal to the passed one. Otherwise, it throws an
	 * {@link IllegalArgumentException}.
	 *
	 * @param context
	 *            The context to be removed.
	 */
	public void remove(String context) {
		if (Objects.equals(contextStack.peek().getRight(), context)) {
			contextStack.pop();

			LOGGER.info("Removed context {}. Context is now {}", context, toString());
		} else {
			throw new IllegalArgumentException("Cannot remove context " + context + "! Current context is " + toString());
		}
	}

	/**
	 * Discards all contexts above the upmost element. That is, if the context is
	 * {@code (null, a)/(loop, b)/(null, c)}, the resulting context is {@code (null, a)/(loop, b)}
	 * and {@code loop} will be returned.
	 *
	 * @return The upmost element or {@code null}, if there is no element.
	 */
	public IExperimentElement resetToUpmostElement() {
		while ((contextStack.size() > 0) && (contextStack.peek().getLeft() == null)) {
			contextStack.pop();
		}

		if (contextStack.size() > 0) {
			return contextStack.peek().getLeft();
		} else {
			return null;
		}
	}

	/**
	 * Converts the context to a path, e.g., {@code iteration#3/branch#a/foo}.
	 *
	 * @return A path representing this context.
	 */
	public Path toPath() {
		Path path = Paths.get("");

		String suffix = rootSuffix;

		for (Pair<IExperimentElement, String> element : contextStack) {
			path = path.resolve(element.getRight() + suffix);
			suffix = "";
		}

		return path;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		boolean first = true;

		for (Pair<IExperimentElement, String> element : contextStack) {
			if (first) {
				first = false;
			} else {
				builder.append(SEPARATOR);
			}

			builder.append(element.getRight());
		}

		if (builder.length() == 0) {
			builder.append("<empty>");
		}

		return builder.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Context clone() {
		Context clone = new Context((Stack<Pair<IExperimentElement, String>>) contextStack.clone());
		clone.setRootSuffix(rootSuffix);
		return clone;
	}

}
