package org.continuity.experimentation.action;

import org.continuity.experimentation.IExperimentAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Provides means for executing REST requests.
 *
 * @author Henning Schulz
 *
 */
public abstract class AbstractRestAction implements IExperimentAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRestAction.class);

	private final String host;
	private final String port;

	private final RestTemplate restTemplate;

	public AbstractRestAction(String host, String port, RestTemplate restTemplate) {
		this.host = host;
		this.port = port;

		if (restTemplate != null) {
			this.restTemplate = restTemplate;
		} else {
			this.restTemplate = new RestTemplate();
		}
	}

	public AbstractRestAction(String host, String port) {
		this(host, port, null);
	}

	public AbstractRestAction(String host) {
		this(host, "80");
	}

	/**
	 * Performs a GET request and returns the response as entity.
	 *
	 * @param host
	 *            The host name. Replaces the object's attribute.
	 * @param port
	 *            The port number. Replaces the object's attribute.
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @return The retrieved entity.
	 */
	protected <T> ResponseEntity<T> getAsEntity(String host, String port, String uri, Class<T> responseType) {
		ResponseEntity<T> response;

		try {
			String url = "http://" + host + ":" + port + uri;
			LOGGER.debug("Submitting a GET request to {}...", url);
			response = restTemplate.getForEntity(url, responseType);
		} catch (HttpStatusCodeException e) {
			response = ResponseEntity.status(e.getStatusCode()).build();
		}

		return response;
	}

	/**
	 * Performs a GET request and returns the response as entity.
	 *
	 * @param host
	 *            The host name. Replaces the object's attribute.
	 * @param port
	 *            The port number. Replaces the object's attribute.
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @return The retrieved entity.
	 */
	protected <T> ResponseEntity<T> getAsEntity(String host, int port, String uri, Class<T> responseType) {
		return getAsEntity(host, Integer.toString(port), uri, responseType);
	}

	/**
	 * Performs a GET request and returns the response as entity.
	 *
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @return The retrieved entity.
	 */
	protected <T> ResponseEntity<T> getAsEntity(String uri, Class<T> responseType) {
		return getAsEntity(this.host, this.port, uri, responseType);
	}

	/**
	 * Performs a GET request.
	 *
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @return The retrieved entity.
	 * @throws RuntimeException
	 *             If the response code was not a 2xx.
	 */
	protected <T> T get(String uri, Class<T> responseType) throws RuntimeException {
		String url = "http://" + host + ":" + port + uri;
		LOGGER.debug("Submitting a GET request to {}...", url);
		ResponseEntity<T> response = restTemplate.getForEntity(url, responseType);
		return response.getBody();
	}

	/**
	 * Performs a POST request.
	 *
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @param body
	 *            The body to be sent.
	 * @return The retrieved entity.
	 * @throws RuntimeException
	 *             If the response code was not a 2xx.
	 */
	protected <S> ResponseEntity<String> postForEntity(String uri, S body) throws RuntimeException {
		ResponseEntity<String> response;

		try {
			String url = "http://" + host + ":" + port + uri;
			LOGGER.debug("Submitting a POST request to {}...", url);
			response = restTemplate.postForEntity(url, body, String.class);
		} catch (HttpStatusCodeException e) {
			response = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
		}

		return response;
	}

	/**
	 * Performs a POST request.
	 *
	 * @param uri
	 *            The URI. Should start with a /.
	 * @param responseType
	 *            The response type.
	 * @param body
	 *            The body to be sent.
	 * @return The retrieved entity.
	 * @throws RuntimeException
	 *             If the response code was not a 2xx.
	 */
	protected <T, S> T post(String uri, Class<T> responseType, S body) throws RuntimeException {
		String url = "http://" + host + ":" + port + uri;
		LOGGER.debug("Submitting a POST request to {}...", url);
		ResponseEntity<T> response = restTemplate.postForEntity(url, body, responseType);

		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new RuntimeException("Return code was " + response.getStatusCode());
		}

		return response.getBody();
	}

	protected String getHost() {
		return host;
	}

	protected String getPort() {
		return port;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Request to " + host + ":" + port;
	}

}
