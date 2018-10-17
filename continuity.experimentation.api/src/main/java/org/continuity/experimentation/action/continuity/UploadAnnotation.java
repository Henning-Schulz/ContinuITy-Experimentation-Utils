package org.continuity.experimentation.action.continuity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.continuity.api.rest.RestApi;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.continuity.idpa.annotation.ApplicationAnnotation;
import org.continuity.idpa.yaml.IdpaYamlSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Henning Schulz
 *
 */
public class UploadAnnotation extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadAnnotation.class);

	private static final IdpaYamlSerializer<ApplicationAnnotation> SERIALIZER = new IdpaYamlSerializer<>(ApplicationAnnotation.class);

	private final IDataHolder<ApplicationAnnotation> annotation;

	private final IDataHolder<String> tag;

	private final IDataHolder<String> report;

	private UploadAnnotation(String host, String port, IDataHolder<ApplicationAnnotation> annotation, IDataHolder<String> tag, IDataHolder<String> report) {
		super(host, port);
		this.annotation = annotation;
		this.report = report;
		this.tag = tag;
	}

	private UploadAnnotation(String host, IDataHolder<ApplicationAnnotation> annotation, IDataHolder<String> tag, IDataHolder<String> report) {
		this(host, "8080", annotation, tag, report);
	}

	public static Builder from(IDataHolder<Path> path, IDataHolder<String> tag) {
		IDataHolder<ApplicationAnnotation> annHolder = new IDataHolder<ApplicationAnnotation>() {

			@Override
			public void set(ApplicationAnnotation data) {
			}

			@Override
			public ApplicationAnnotation get() throws AbortInnerException {
				ApplicationAnnotation ann;

				try {
					ann = SERIALIZER.readFromYaml(path.get());
				} catch (IOException | AbortInnerException e) {
					LOGGER.error("Could not read annotation!", e);
					ann = null;
				}

				return ann;
			}

			@Override
			public boolean isSet() {
				return true;
			}

			@Override
			public void invalidate() {
			}
		};

		return new Builder(annHolder, tag);
	}

	public static UploadAnnotation as(String host, String port, IDataHolder<ApplicationAnnotation> annotation, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadAnnotation(host, port, annotation, tag, report);
	}

	public static UploadAnnotation as(String host, IDataHolder<ApplicationAnnotation> annotation, IDataHolder<String> tag, IDataHolder<String> report) {
		return new UploadAnnotation(host, annotation, tag, report);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		String response = post(RestApi.Orchestrator.Idpa.UPDATE_ANNOTATION.path(tag.get()), String.class, annotation.get());
		report.set(response);

		Path path = context.toPath().resolve("annotation-upload-report.json");
		Files.write(path, Arrays.asList(response.split("\\n")), StandardOpenOption.CREATE);

		LOGGER.info("Uploaded annotation {}.", annotation.get().getId());
	}

	public static class Builder {

		private final IDataHolder<ApplicationAnnotation> annotation;

		private final IDataHolder<String> tag;

		private Builder(IDataHolder<ApplicationAnnotation> annotation, IDataHolder<String> tag) {
			this.annotation = annotation;
			this.tag = tag;
		}

		public UploadAnnotation to(String host, String port, IDataHolder<String> report) {
			return new UploadAnnotation(host, port, annotation, tag, report);
		}

		public UploadAnnotation to(String host, IDataHolder<String> report) {
			return new UploadAnnotation(host, annotation, tag, report);
		}

	}

}
