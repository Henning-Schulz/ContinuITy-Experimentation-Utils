package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Date;

import org.continuity.annotation.dsl.system.SystemModel;
import org.continuity.annotation.dsl.yaml.ContinuityYamlSerializer;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Henning Schulz
 *
 */
public class UploadSystemModel extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadAnnotation.class);

	private static final ContinuityYamlSerializer<SystemModel> SERIALIZER = new ContinuityYamlSerializer<>(SystemModel.class);

	private final IDataHolder<SystemModel> system;

	private final IDataHolder<String> tag;

	private final IDataHolder<String> report;

	private UploadSystemModel(String host, String port, IDataHolder<SystemModel> system, IDataHolder<String> tag, IDataHolder<String> report) {
		super(host, port);
		this.system = system;
		this.report = report;
		this.tag = tag;
	}

	private UploadSystemModel(String host, IDataHolder<SystemModel> system, IDataHolder<String> tag, IDataHolder<String> report) {
		this(host, "8080", system, tag, report);
	}

	public static Builder from(IDataHolder<Path> path, IDataHolder<String> tag) {
		IDataHolder<SystemModel> sysHolder = new IDataHolder<SystemModel>() {

			@Override
			public void set(SystemModel data) {
			}

			@Override
			public SystemModel get() throws AbortInnerException {
				SystemModel sys;

				try {
					sys = SERIALIZER.readFromYaml(path.get());
				} catch (IOException | AbortInnerException e) {
					LOGGER.error("Could not read system model!", e);
					sys = null;
				}

				return sys;
			}

			@Override
			public boolean isSet() {
				return true;
			}

			@Override
			public void invalidate() {
			}
		};

		return new Builder(sysHolder, tag);
	}

	public static UploadSystemModel as(String host, String port, IDataHolder<SystemModel> system, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadSystemModel(host, port, system, tag, report);
	}

	public static UploadSystemModel as(String host, IDataHolder<SystemModel> system, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadSystemModel(host, system, tag, report);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		SystemModel systemModel = system.get();
		systemModel.setTimestamp(new Date()); // TODO
		String response = post("/annotation/" + tag.get() + "/system", String.class, systemModel);
		report.set(response);

		Path path = context.toPath().resolve("system-upload-report.json");
		Files.write(path, Arrays.asList(response.split("\\n")), StandardOpenOption.CREATE);

		LOGGER.info("Uploaded system model {}.", systemModel.getId());
	}

	public static class Builder {

		private final IDataHolder<SystemModel> system;

		private final IDataHolder<String> tag;

		private Builder(IDataHolder<SystemModel> system, IDataHolder<String> tag) {
			this.system = system;
			this.tag = tag;
		}

		public UploadSystemModel to(String host, String port, IDataHolder<String> report) {
			return new UploadSystemModel(host, port, system, tag, report);
		}

		public UploadSystemModel to(String host, IDataHolder<String> report) {
			return new UploadSystemModel(host, system, tag, report);
		}

	}

}
