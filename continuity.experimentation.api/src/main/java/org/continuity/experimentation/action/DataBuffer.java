package org.continuity.experimentation.action;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Uses the possibility of the satellite to buffer data.
 *
 * @author Henning Schulz
 *
 */
public class DataBuffer {

	private DataBuffer() {
	}

	/**
	 * Uploads the data to the buffer using the default port 8765.
	 *
	 * @param host
	 *            The host name of the satellite.
	 * @param data
	 *            [IN] The data to be uploaded.
	 * @param link
	 *            [OUT] The URL where the data can be retrieved.
	 * @return
	 */
	public static <T> Uploader<T> upload(String host, IDataHolder<T> data, IDataHolder<String> link) {
		return new Uploader<>(host, data, link);
	}

	/**
	 * Uploads the data to the buffer.
	 *
	 * @param host
	 *            The host name of the satellite.
	 * @param port
	 *            The port number of the satellite.
	 * @param data
	 *            [IN] The data to be uploaded.
	 * @param link
	 *            [OUT] The URL where the data can be retrieved.
	 * @return
	 */
	public static <T> Uploader<T> upload(String host, String port, IDataHolder<T> data, IDataHolder<String> link) {
		return new Uploader<>(host, port, data, link);
	}

	public static class Uploader<T> extends AbstractRestAction {

		private static final String UPLOAD_PATH = "/buffer/upload";

		private final IDataHolder<T> data;
		private final IDataHolder<String> link;

		private Uploader(String host, IDataHolder<T> data, IDataHolder<String> link) {
			this(host, "8765", data, link);
		}

		private Uploader(String host, String port, IDataHolder<T> data, IDataHolder<String> link) {
			super(host, port);
			this.data = data;
			this.link = link;
		}

		@Override
		public void execute(Context context) throws AbortInnerException, AbortException, Exception {
			String response = post(UPLOAD_PATH, String.class, data);
			link.set(UriComponentsBuilder.fromPath(response).host(getHost()).port(getPort()).build().toString());
		}

	}

}
