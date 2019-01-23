package org.continuity.experimentation.satellite;

import java.util.HashMap;
import java.util.Map;

public enum KnownApplication {

	DVD_STORE("dvdstore", "sudo service dvdstore restart", "UNDEFINED"), HEAT_CLINIC("heat-clinic", "restartHeatClinic.sh", "checkoutHeatClinicVersion.sh"), CMR("cmr", "sudo service cmr restart",
			"UNDEFINED"), CMR_DOCKER_SWARM("cmr", "docker service scale monitoring_cmr=0; docker service scale monitoring_cmr=1;",
					"UNDEFINED"), SOCK_SHOP("sock-shop", "docker stack services -q sock-shop   | while read service; do docker service update --force $service; done;", "UNDEFINED");

	private static final Map<String, KnownApplication> APP_PER_KEY = new HashMap<>();

	static {
		for (KnownApplication app : values()) {
			APP_PER_KEY.put(app.key, app);
		}
	}

	public static KnownApplication forKey(String key) {
		return APP_PER_KEY.get(key);
	}

	private final String key;
	private final String restartCommand;
	private final String gitCheckoutCommand;

	private KnownApplication(String key, String restartCommand, String gitCheckoutCommand) {
		this.key = key;
		this.restartCommand = restartCommand;
		this.gitCheckoutCommand = gitCheckoutCommand;
	}

	public String getRestartCommand() {
		return restartCommand;
	}

	public String getGitCheckoutCommand() {
		return gitCheckoutCommand;
	}

}
