package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

/**
 * Subsumes means for restarting applications.
 *
 * @author Tobias Angerstein, Henning Schulz
 *
 */
public class TargetSystem {

	/**
	 * Restarts the application using the satellite at the specified host. Does not wait until the
	 * application is available. For this, please use {@link #waitFor(Application, String, String)}.
	 *
	 * @param app
	 *            The application to be restarted.
	 * @param host
	 *            The host of the satellite.
	 * @param port
	 *            The port of the satellite.
	 * @return An action to be used for restarting.
	 */
	public static IExperimentAction restart(Application app, String host, String port) {
		return new Restart(app, host, port);
	}

	/**
	 * Restarts the application using the satellite at the specified host using the default port
	 * 8765. Does not wait until the application is available. For this, please use
	 * {@link #waitFor(Application, String)}.
	 *
	 * @param app
	 *            The application to be restarted.
	 * @param host
	 *            The host of the satellite.
	 * @return An action to be used for restarting.
	 */
	public static IExperimentAction restart(Application app, String host) {
		return new Restart(app, host);
	}

	/**
	 * Waits for the application to be available. Waits as long as specified.
	 *
	 * @param app
	 *            The application to wait for.
	 * @param host
	 *            The host where the application is running.
	 * @param port
	 *            The port of the application.
	 * @param maxWaitMs
	 *            The maximum time to wait in ms.
	 * @return An action to be used to wait.
	 */
	public static IExperimentAction waitFor(Application app, String host, String port, long maxWaitMs) {
		return new WaitFor(app, host, port, maxWaitMs);
	}

	/**
	 * Waits for the application to be available. Waits at least 5 minutes.
	 *
	 * @param app
	 *            The application to wait for.
	 * @param host
	 *            The host where the application is running.
	 * @param port
	 *            The port of the application.
	 * @return An action to be used to wait.
	 */
	public static IExperimentAction waitFor(Application app, String host, String port) {
		return new WaitFor(app, host, port);
	}

	/**
	 * Waits for the application to be available assuming the default port 8080. Waits at least 5
	 * minutes.
	 *
	 * @param app
	 *            The application to wait for.
	 * @param host
	 *            The host where the application is running.
	 * @return An action to be used to wait.
	 */
	public static IExperimentAction waitFor(Application app, String host) {
		return new WaitFor(app, host);
	}

	public static IExperimentAction checkoutGitVersion(Application app, IDataHolder<String> version, String host, String port) {
		return new GitCheckout(app, version, host, port);
	}

	public static IExperimentAction checkoutGitVersion(Application app, IDataHolder<String> version, String host) {
		return new GitCheckout(app, version, host);
	}

	private static class Restart extends AbstractRestAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(Restart.class);

		private final Application app;

		private Restart(Application app, String host, String port) {
			super(host, port);
			this.app = app;
		}

		private Restart(Application app, String host) {
			this(app, host, "8765");
		}

		@Override
		public void execute(Context context) {
			LOGGER.info("Restarting {}...", app);

			String response = get("/restart/" + app, String.class);

			LOGGER.info("Restart initiated. Response from satellite: {}", response);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return "Restart the " + app + " at " + super.toString();
		}
	}

	private static class WaitFor extends AbstractRestAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(WaitFor.class);

		private final Application app;

		private final long maxWaitMs;

		public WaitFor(Application app, String host, String port, long maxWaitMs) {
			super(host, port);
			this.app = app;
			this.maxWaitMs = maxWaitMs;
		}

		public WaitFor(Application app, String host, String port) {
			this(app, host, port, 300000);
		}

		public WaitFor(Application app, String host) {
			this(app, host, "8080");
		}

		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			LOGGER.info("Waiting for the {} at {}:{}{} to be online...", app, getHost(), getPort(), app.getRootPath());

			ResponseEntity<String> response;
			boolean systemIsOnline = false;

			long startMs = System.currentTimeMillis();

			while (!systemIsOnline) {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					LOGGER.warn("Interrupted during waiting after DVDStore restart: {}", e.getMessage());
				}

				try {
					response = getAsEntity(app.getRootPath(), String.class);
				} catch (ResourceAccessException e) {
					LOGGER.info("The {} is not there, yet. Keep waiting...", app);
					continue;
				}

				if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()) {
					systemIsOnline = true;
					LOGGER.info("The {} at {}:{}{} is now online.", app, getHost(), getPort(), app.getRootPath());
					break;
				}

				if ((System.currentTimeMillis() - startMs) > maxWaitMs) {
					LOGGER.error("Waited 250 s for the {} to be online, but wasn't. Aborting.", app);
					throw new AbortInnerException(context, "Waited 250 s for the " + app + " to be online, but wasn't.");
				}
			}
		}

		@Override
		public String toString() {
			return "Wait for the " + app + " at " + super.toString() + app.getRootPath() + " to be online.";
		}

	}

	private static class GitCheckout extends AbstractRestAction {

		private static final Logger LOGGER = LoggerFactory.getLogger(GitCheckout.class);

		private final Application app;

		private final IDataHolder<String> version;

		private GitCheckout(Application app, IDataHolder<String> version, String host, String port) {
			super(host, port);
			this.app = app;
			this.version = version;
		}

		private GitCheckout(Application app, IDataHolder<String> version, String host) {
			this(app, version, host, "8765");
		}

		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			LOGGER.info("Checking version {} of application {} out...", version, app);

			String response = get("/git/" + app + "/checkout/" + version.get(), String.class);

			LOGGER.info("Checkout done. Response from satellite: {}", response);
		}

	}

	public static enum Application {

		DVD_STORE("dvdstore", "/dvdstore/home"), HEAT_CLINIC("heat-clinic", "/"), SOCK_SHOP("sock-shop", "/"), SOCK_SHOP_PINNED("sock-shop-pinned", "/"), CMR("cmr",
				"/rest/data/agents"), CMR_DOCKER_SWARM("cmr-docker", "/"), MONITORING("monitoring", "/"), CONTINUITY_JMETER("continuity-jmeter", "/available/");

		private final String name;

		private final String rootPath;

		private Application(String name, String rootPath) {
			this.name = name;
			this.rootPath = rootPath;
		}

		public String getRootPath() {
			return rootPath;
		}

		@Override
		public String toString() {
			return name;
		}
	}

}
