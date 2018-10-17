package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Date;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.continuity.idpa.application.Application;
import org.continuity.idpa.yaml.IdpaYamlSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Henning Schulz
 *
 */
public class UploadApplicationModel extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadAnnotation.class);

	private static final IdpaYamlSerializer<Application> SERIALIZER = new IdpaYamlSerializer<>(Application.class);

	private final IDataHolder<Application> application;

	private final IDataHolder<String> tag;

	private final IDataHolder<String> report;

	private UploadApplicationModel(String host, String port, IDataHolder<Application> application, IDataHolder<String> tag, IDataHolder<String> report) {
		super(host, port);
		this.application = application;
		this.report = report;
		this.tag = tag;
	}

	private UploadApplicationModel(String host, IDataHolder<Application> application, IDataHolder<String> tag, IDataHolder<String> report) {
		this(host, "8080", application, tag, report);
	}

	public static Builder from(IDataHolder<Path> path, IDataHolder<String> tag) {
		IDataHolder<Application> sysHolder = new IDataHolder<Application>() {

			@Override
			public void set(Application data) {
			}

			@Override
			public Application get() throws AbortInnerException {
				Application sys;

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

	public static UploadApplicationModel as(String host, String port, IDataHolder<Application> application, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadApplicationModel(host, port, application, tag, report);
	}

	public static UploadApplicationModel as(String host, IDataHolder<Application> application, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadApplicationModel(host, application, tag, report);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		Application systemModel = application.get();
		systemModel.setTimestamp(new Date()); // TODO
		String response = post("/annotation/" + tag.get() + "/system", String.class, systemModel);
		report.set(response);

		Path path = context.toPath().resolve("system-upload-report.json");
		Files.write(path, Arrays.asList(response.split("\\n")), StandardOpenOption.CREATE);

		LOGGER.info("Uploaded system model {}.", systemModel.getId());
	}

	public static class Builder {

		private final IDataHolder<Application> system;

		private final IDataHolder<String> tag;

		private Builder(IDataHolder<Application> system, IDataHolder<String> tag) {
			this.system = system;
			this.tag = tag;
		}

		public UploadApplicationModel to(String host, String port, IDataHolder<String> report) {
			return new UploadApplicationModel(host, port, system, tag, report);
		}

		public UploadApplicationModel to(String host, IDataHolder<String> report) {
			return new UploadApplicationModel(host, system, tag, report);
		}

	}

}
