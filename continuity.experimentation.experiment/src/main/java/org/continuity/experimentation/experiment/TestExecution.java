package org.continuity.experimentation.experiment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.continuity.api.entities.config.ModularizationApproach;

public class TestExecution {

	private final List<String> servicesUnderTest;
	private final boolean modularized;
	private final ModularizationApproach modularizationApproach;

	public TestExecution(ModularizationApproach modularizationApproach, String... service) {
		this.modularized = true;
		this.modularizationApproach = modularizationApproach;
		this.servicesUnderTest = Arrays.asList(service);
	}

	/**
	 * Use, if non modularized load test is going to be tested.
	 */
	public TestExecution(boolean modularized) {
		this.modularized = modularized;
		this.modularizationApproach = null;
		this.servicesUnderTest = Collections.emptyList();
	}

	public List<String> getServicesUnderTest() {
		return servicesUnderTest;
	}

	public boolean isModularized() {
		return modularized;
	}

	public ModularizationApproach getModularizationApproach() {
		return modularizationApproach;
	}

	public String toContext() {
		if (modularized) {
			StringBuilder builder = new StringBuilder();
			builder.append(modularizationApproach.toPrettyString());

			for (String service : servicesUnderTest) {
				builder.append("-");
				builder.append(service);
			}

			return builder.toString();
		} else {
			return "non-modularized";
		}
	}

}
