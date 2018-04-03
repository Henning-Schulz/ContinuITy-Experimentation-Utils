package org.continuity.experimentation.experiment.apichanger;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.continuity.annotation.dsl.ContinuityModelElement;
import org.continuity.annotation.dsl.system.HttpInterface;
import org.continuity.annotation.dsl.system.HttpParameter;

public enum ApiChangeType {

	ADD_INTERFACE(HttpInterface.class, 0.4152), REMOVE_INTERFACE(HttpInterface.class, 0.1565), CHANGE_INTERFACE_PATH(HttpInterface.class, 0.1152), ADD_PARAMETER(HttpParameter.class,
			0.1109), REMOVE_PARAMETER(HttpParameter.class, 0.0457), CHANGE_PARAMETER_NAME(HttpParameter.class, 0.0696);

	private static final Map<Class<? extends ContinuityModelElement>, Set<ApiChangeType>> changeTypesPerClass = new HashMap<>();

	static {
		for (ApiChangeType changeType : values()) {
			Set<ApiChangeType> set = changeTypesPerClass.get(changeType.getTargetType());

			if (set == null) {
				set = EnumSet.noneOf(ApiChangeType.class);
				changeTypesPerClass.put(changeType.getTargetType(), set);
			}

			set.add(changeType);
		}
	}

	private final Class<? extends ContinuityModelElement> targetType;

	private final double changeRatio;

	private ApiChangeType(Class<? extends ContinuityModelElement> targetType, double changeRatio) {
		this.targetType = targetType;
		this.changeRatio = changeRatio;
	}

	public static Set<ApiChangeType> getForType(Class<? extends ContinuityModelElement> targetType) {
		return changeTypesPerClass.get(targetType);
	}

	/**
	 *
	 * @param targetType
	 * @param random
	 *            Should be between 0 and 1 (e.g., by calling {@link Random#nextDouble()}.
	 * @return
	 */
	public static ApiChangeType getRandomForType(Class<? extends ContinuityModelElement> targetType, double random) {
		Set<ApiChangeType> changeTypes = getForType(targetType);
		return getRandomFromCollection(changeTypes, random);
	}

	public static ApiChangeType getRandom(double random) {
		return getRandomFromCollection(Arrays.asList(values()), random);
	}

	private static ApiChangeType getRandomFromCollection(Collection<ApiChangeType> coll, double random) {
		double ratioSum = coll.stream().map(ApiChangeType::getChangeRatio).reduce(Double::sum).get();

		double sum = 0;
		ApiChangeType last = null;

		for (ApiChangeType changeType : coll) {
			double ratio = changeType.getChangeRatio() / ratioSum;

			if ((sum <= random) && (random < (sum + ratio))) {
				return changeType;
			}

			sum += ratio;
			last = changeType;
		}

		return last;
	}

	public Class<? extends ContinuityModelElement> getTargetType() {
		return targetType;
	}

	public double getChangeRatio() {
		return changeRatio;
	}

}
