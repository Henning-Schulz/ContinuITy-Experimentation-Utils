package org.continuity.experimentation.data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.continuity.experimentation.exception.AbortInnerException;

public class PathHolder implements IDataHolder<Path> {

	private final List<IDataHolder<String>> pathSegments = new ArrayList<>();

	private PathHolder() {
	}

	public static PathHolder newPath() {
		return new PathHolder();
	}

	public PathHolder resolveStatic(String pathSegment) {
		pathSegments.add(StaticDataHolder.of(pathSegment));
		return this;
	}

	public PathHolder resolveDynamic(IDataHolder<String> pathSegment) {
		pathSegments.add(pathSegment);
		return this;
	}

	@Override
	public void set(Path data) {
		// do nothing
	}

	@Override
	public Path get() throws AbortInnerException {
		Path path = Paths.get(".");

		for (IDataHolder<String> segment : pathSegments) {
			path = path.resolve(segment.get());
		}

		return path;
	}

	@Override
	public boolean isSet() {
		return pathSegments.stream().map(IDataHolder::isSet).reduce((a, b) -> a && b).get();
	}

	@Override
	public void invalidate() {
		// do nothing
	}

}
