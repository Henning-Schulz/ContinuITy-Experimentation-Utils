package org.continuity.experimentation.experiment.apichanger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.continuity.annotation.dsl.ContinuityModelElement;
import org.continuity.annotation.dsl.WeakReference;
import org.continuity.annotation.dsl.ann.ExtractedInput;
import org.continuity.annotation.dsl.ann.InterfaceAnnotation;
import org.continuity.annotation.dsl.ann.ParameterAnnotation;
import org.continuity.annotation.dsl.ann.RegExExtraction;
import org.continuity.annotation.dsl.ann.SystemAnnotation;
import org.continuity.annotation.dsl.system.HttpInterface;
import org.continuity.annotation.dsl.system.HttpParameter;
import org.continuity.annotation.dsl.system.HttpParameterType;
import org.continuity.annotation.dsl.system.ServiceInterface;
import org.continuity.annotation.dsl.system.SystemModel;
import org.continuity.annotation.dsl.yaml.ContinuityYamlSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class ApiChanger {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiChanger.class);

	private static final int NUM_ITERATIONS = 18;

	private static final int CHANGE_NUM_LOWER_BOUND = 1;

	private static final int CHANGE_NUM_UPPER_BOUND = 5;

	private static final Path BASE_PATH = Paths.get("heat-clinic", "versions");

	private static final String systemModelFilename = "system-model-heat-clinic.yml";

	private static final String annotationFilename = "annotation-heat-clinic.yml";

	private static final Random RAND = new Random('c' + 'o' + 'n' + 't' + 'i' + 'n' + 'u' + 'I' + 'T' + 'y');

	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException {
		ContinuityYamlSerializer<SystemModel> systemSerializer = new ContinuityYamlSerializer<>(SystemModel.class);
		ContinuityYamlSerializer<SystemAnnotation> annotationSerializer = new ContinuityYamlSerializer<>(SystemAnnotation.class);

		SystemModel system = systemSerializer.readFromYaml(BASE_PATH.resolve("v2").resolve(systemModelFilename));
		SystemAnnotation annotation = annotationSerializer.readFromYaml(BASE_PATH.resolve("v2").resolve(annotationFilename));

		ApiChanger changer = new ApiChanger(system, annotation, ".*csrfToken.*", "doLoginUsingPOST_password_REQ_PARAM", "doLoginUsingPOST_remember_me_REQ_PARAM",
				"doLoginUsingPOST_username_REQ_PARAM", ".*itemAttribute.*");

		changer.addChanges();
	}

	private final SystemModel system;

	private final SystemAnnotation annotation;

	private final List<HttpInterface> addedInterfaces = new ArrayList<>();

	private final List<Pair<HttpInterface, HttpParameter>> addedParameters = new ArrayList<>();

	private final List<String> excludedPatterns;

	public ApiChanger(SystemModel system, SystemAnnotation annotation, String... excludedPatterns) {
		this.system = system;
		this.annotation = annotation;
		this.excludedPatterns = Arrays.asList(excludedPatterns);
	}

	public void addChanges() {
		List<Integer> changeNumSequence = new ArrayList<>(NUM_ITERATIONS);

		for (int i = 0; i < NUM_ITERATIONS; i++) {
			changeNumSequence.add(RAND.nextInt(CHANGE_NUM_UPPER_BOUND - CHANGE_NUM_LOWER_BOUND) + CHANGE_NUM_LOWER_BOUND);
		}

		int changeSequenceLength = changeNumSequence.stream().reduce((a, b) -> a + b).get();
		LOGGER.info("Change sequence length is {}.", changeSequenceLength);

		List<ApiChangeType> sequence = generateChangeSequence(changeSequenceLength);
		LOGGER.info("Change sequence: {}", sequence);

		int changeIdx = 0;
		int innerIdx = 0;

		LOGGER.info("### v2 -> v3:");
		for (ApiChangeType changeType : sequence) {
			if (innerIdx == changeNumSequence.get(changeIdx)) {
				collectChanges(2 + changeIdx);

				changeIdx++;
				innerIdx = 0;

				LOGGER.info("### v{} -> v{}:", 2 + changeIdx, 3 + changeIdx);
			}

			applyChange(changeType);

			innerIdx++;
		}
	}

	private List<ApiChangeType> generateChangeSequence(int length) {
		List<ApiChangeType> sequence = new ArrayList<>(length);

		for (int i = 0; i < length; i++) {
			sequence.add(ApiChangeType.getRandom(RAND.nextDouble()));
		}

		return sequence;
	}

	private void applyChange(ApiChangeType changeType) {
		switch (changeType) {
		case ADD_INTERFACE:
			HttpInterface origInterf = (HttpInterface) selectRandom(system.getInterfaces());
			HttpInterface newInterf = cloneInterface(origInterf);

			addedInterfaces.add(newInterf);
			system.addInterface(newInterf);

			InterfaceAnnotation origInterfAnn = findAnnotation(origInterf);
			if (origInterfAnn != null) {
				annotation.getInterfaceAnnotations().add(cloneInterfaceAnnotation(origInterfAnn, newInterf));
			}

			addToExtractedInputs(newInterf, origInterf);

			LOGGER.info("Cloned interface {} to {}.", origInterf.getId(), newInterf.getId());
			break;
		case ADD_PARAMETER:
			List<Pair<HttpInterface, HttpParameter>> params = system.getInterfaces().stream()
					.flatMap(i -> i.getParameters().stream().filter(this::isIncluded).map(p -> Pair.of((HttpInterface) i, (HttpParameter) p))).collect(Collectors.toList());
			Pair<HttpInterface, HttpParameter> origParam = selectRandom(params);
			HttpParameter newParam = cloneParameter(origParam.getRight());

			addedParameters.add(origParam);
			origParam.getLeft().addParameter(newParam);

			origInterfAnn = findAnnotation(origParam.getLeft());
			ParameterAnnotation origParamAnn = findAnnotation(origParam.getRight(), origInterfAnn);
			origInterfAnn.addParameterAnnotation(cloneParameterAnnotation(origParamAnn, newParam));

			LOGGER.info("Cloned parameter {} of interface {} to {}.", origParam.getRight().getId(), origParam.getLeft().getId(), newParam.getId());
			break;
		case CHANGE_INTERFACE_PATH:
			HttpInterface interf = (HttpInterface) selectRandom(system.getInterfaces());

			String newPath = interf.getPath() + "/changed";
			interf.setPath(newPath);

			LOGGER.info("Changed path of {} to {}.", interf.getId(), newPath);
			break;
		case CHANGE_PARAMETER_NAME:
			List<HttpParameter> parameters = system.getInterfaces().stream().map(ServiceInterface::getParameters).flatMap(List::stream).map(p -> (HttpParameter) p)
					.filter(p -> p.getParameterType() != HttpParameterType.URL_PART).filter(this::isIncluded).collect(Collectors.toList());
			HttpParameter param = selectRandom(parameters);

			String newName = param.getName() + "-changed";
			param.setName(newName);

			LOGGER.info("Changed name of {} to {}.", param.getId(), newName);
			break;
		case REMOVE_INTERFACE:
			HttpInterface toRemove = selectRandom(addedInterfaces);

			addedInterfaces.remove(toRemove);
			boolean found = system.getInterfaces().remove(toRemove);

			if (!found) {
				LOGGER.warn("There was no interface {} in the system model!", toRemove.getId());
			} else {
				origInterfAnn = findAnnotation(toRemove);
				annotation.getInterfaceAnnotations().remove(origInterfAnn);

				removeFromExtractedInputs(toRemove);

				LOGGER.info("Removed interface {}.", toRemove.getId());
			}
			break;
		case REMOVE_PARAMETER:
			Pair<HttpInterface, HttpParameter> paramToRemove = selectRandom(addedParameters);

			paramToRemove.getLeft().getParameters().remove(paramToRemove.getRight());

			origInterfAnn = findAnnotation(paramToRemove.getLeft());
			origParamAnn = findAnnotation(paramToRemove.getRight(), origInterfAnn);
			origInterfAnn.getParameterAnnotations().remove(origParamAnn);

			LOGGER.info("Removed parameter {}.", paramToRemove.getRight().getId());
			break;
		default:
			LOGGER.warn("Asked to apply {}!", changeType);
			break;
		}
	}

	private void collectChanges(int version) {
		// TODO
	}

	private <T> T selectRandom(List<T> list) {
		int randomIndex = RAND.nextInt(list.size());
		return list.get(randomIndex);
	}

	private HttpInterface cloneInterface(HttpInterface origInterf) {
		HttpInterface newInterf = new HttpInterface();
		newInterf.setDomain(origInterf.getDomain());
		newInterf.setEncoding(origInterf.getEncoding());
		newInterf.setHeaders(new ArrayList<>(origInterf.getHeaders()));
		newInterf.setId(origInterf.getId() + "_CLONE");
		newInterf.setMethod(origInterf.getMethod());
		newInterf.setPath(origInterf.getPath() + "/clone");
		newInterf.setPort(origInterf.getPort());
		newInterf.setProtocol(origInterf.getProtocol());

		for (HttpParameter param : origInterf.getParameters()) {
			HttpParameter newParam = cloneParameter(param);
			newParam.setId(newInterf.getId() + "_" + param.getId());
			newInterf.getParameters().add(newParam);
		}

		return newInterf;
	}

	private HttpParameter cloneParameter(HttpParameter param) {
		HttpParameter newParam = new HttpParameter();
		newParam.setId(param.getId() + "_CLONE");
		newParam.setName(param.getName());
		newParam.setParameterType(param.getParameterType());
		return newParam;
	}

	private boolean isIncluded(ContinuityModelElement element) {
		for (String pattern : excludedPatterns) {
			if ((element.getId() != null) && element.getId().matches(pattern)) {
				return false;
			}
		}

		return true;
	}

	private InterfaceAnnotation findAnnotation(HttpInterface interf) {
		for (InterfaceAnnotation ann : annotation.getInterfaceAnnotations()) {
			if (ann.getAnnotatedInterface().getId().equals(interf.getId())) {
				return ann;
			}
		}

		return null;
	}

	private ParameterAnnotation findAnnotation(HttpParameter param, InterfaceAnnotation interfAnn) {
		for (ParameterAnnotation ann : interfAnn.getParameterAnnotations()) {
			if (ann.getAnnotatedParameter().getId().equals(param.getId())) {
				return ann;
			}
		}

		return null;
	}

	private InterfaceAnnotation cloneInterfaceAnnotation(InterfaceAnnotation origAnn, HttpInterface newInterf) {
		InterfaceAnnotation newAnn = new InterfaceAnnotation();
		newAnn.setAnnotatedInterface(WeakReference.create(newInterf));
		newAnn.setOverrides(new ArrayList<>(origAnn.getOverrides()));

		HttpInterface origInterf = (HttpInterface) origAnn.getAnnotatedInterface().resolve(system);
		List<ParameterAnnotation> paramAnns = new ArrayList<>();

		for (HttpParameter newParam : newInterf.getParameters()) {
			HttpParameter origParam = origInterf.getParameters().stream().filter(p -> p.getName().equals(newParam.getName())).reduce((a, b) -> a).get();

			ParameterAnnotation origParamAnn = findAnnotation(origParam, origAnn);
			ParameterAnnotation newParamAnn = cloneParameterAnnotation(origParamAnn, newParam);

			paramAnns.add(newParamAnn);
		}

		newAnn.setParameterAnnotations(paramAnns);
		return newAnn;
	}

	private ParameterAnnotation cloneParameterAnnotation(ParameterAnnotation origAnn, HttpParameter newParam) {
		ParameterAnnotation newAnn = new ParameterAnnotation();
		newAnn.setAnnotatedParameter(WeakReference.create(newParam));
		newAnn.setInput(origAnn.getInput());
		newAnn.setOverrides(new ArrayList<>(origAnn.getOverrides()));
		return newAnn;
	}

	private void addToExtractedInputs(HttpInterface newInterf, HttpInterface origInterf) {
		List<Pair<ExtractedInput, List<RegExExtraction>>> extractions = annotation.getInputs().stream().filter(in -> in instanceof ExtractedInput).map(in -> (ExtractedInput) in)
				.map(in -> Pair.of(in, in.getExtractions().stream().filter(ex -> ex.getFrom().getId().equals(origInterf.getId())).collect(Collectors.toList())))
				.filter(pair -> !pair.getRight().isEmpty()).collect(Collectors.toList());

		for (Pair<ExtractedInput, List<RegExExtraction>> pair : extractions) {
			for (RegExExtraction origExtraction : pair.getRight()) {
				RegExExtraction newExtraction = new RegExExtraction();
				newExtraction.setFallbackValue(origExtraction.getFallbackValue());
				newExtraction.setFrom(WeakReference.create(newInterf));
				newExtraction.setMatchNumber(origExtraction.getMatchNumber());
				newExtraction.setPattern(origExtraction.getPattern());
				newExtraction.setResponseKey(origExtraction.getResponseKey());
				newExtraction.setTemplate(origExtraction.getResponseKey());

				pair.getLeft().getExtractions().add(newExtraction);
			}
		}
	}

	private void removeFromExtractedInputs(HttpInterface interfToRemove) {
		List<Pair<ExtractedInput, List<RegExExtraction>>> extractions = annotation.getInputs().stream().filter(in -> in instanceof ExtractedInput).map(in -> (ExtractedInput) in)
				.map(in -> Pair.of(in, in.getExtractions().stream().filter(ex -> ex.getFrom().getId().equals(interfToRemove.getId())).collect(Collectors.toList())))
				.filter(pair -> !pair.getRight().isEmpty()).collect(Collectors.toList());

		for (Pair<ExtractedInput, List<RegExExtraction>> pair : extractions) {
			for (RegExExtraction extractionToRemove : pair.getRight()) {
				pair.getLeft().getExtractions().remove(extractionToRemove);
			}
		}
	}

}
