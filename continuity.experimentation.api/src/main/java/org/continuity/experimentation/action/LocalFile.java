package org.continuity.experimentation.action;

import java.nio.charset.Charset;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalFile {

	private LocalFile() {
	}

	public static ReadFile read(IDataHolder<Path> path, IDataHolder<String> fileContent) {
		return new ReadFile(path, fileContent);
	}

	public static CopyFile copy(IDataHolder<Path> fromPath, IDataHolder<Path> toPath) {
		return new CopyFile(fromPath, toPath);
	}

	/**
	 * Reads a file into a data holder.
	 *
	 * @author Henning Schulz
	 *
	 */
	public static class ReadFile implements IExperimentAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(LocalFile.ReadFile.class);

		private final IDataHolder<Path> path;
		private final IDataHolder<String> fileContent;

		/**
		 * @param fromPath
		 * @param toPath
		 */
		private ReadFile(IDataHolder<Path> path, IDataHolder<String> fileContent) {
			this.path = path;
			this.fileContent = fileContent;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			String content = FileUtils.readFileToString(path.get().toFile(), Charset.defaultCharset());
			fileContent.set(content);
			LOGGER.info("Read content of file {} to '{}'", path.get(), fileContent);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return "Read content of file \"" + path + "\" into \"" + fileContent + "\"";
		}

	}

	/**
	 * Copies a file from one location to another.
	 *
	 * @author Henning Schulz
	 *
	 */
	public static class CopyFile implements IExperimentAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(LocalFile.CopyFile.class);

		private final IDataHolder<Path> fromPath;
		private final IDataHolder<Path> toPath;

		/**
		 * @param fromPath
		 * @param toPath
		 */
		private CopyFile(IDataHolder<Path> fromPath, IDataHolder<Path> toPath) {
			this.fromPath = fromPath;
			this.toPath = toPath;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			FileUtils.copyFile(fromPath.get().toFile(), toPath.get().toFile());
			LOGGER.info("Copied file {} to {}.", fromPath.get(), toPath.get());
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return "Copy file from \"" + fromPath + "\" to \"" + toPath + "\"";
		}

	}

}
