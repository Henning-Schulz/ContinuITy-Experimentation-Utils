package org.continuity.experimentation.action.inspectit;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates new storage and starts new recording.
 *
 * @author Tobias Angerstein
 *
 */
public class StartNewRecording extends AbstractRestAction {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(StartNewRecording.class);

	/**
	 * storage name.
	 */
	private String storageName;

	/**
	 * Flag, which determine, whether storage name is set.
	 */
	private boolean storageNameSet = false;

	/**
	 * Time data holder.
	 */
	private final IDataHolder<Date> timeDataHolder;

	/**
	 * Constructor.
	 *
	 * @param storageName
	 *            name of new storage
	 * @param timeDataHolder
	 *            [OUTPUT] Holds the start time.
	 * @param host
	 *            Host name of the inspectIT CMR.
	 * @param port
	 *            Port of the inspectIT CMR.
	 */
	public StartNewRecording(String storageName, IDataHolder<Date> timeDataHolder, String host, String port) {
		super(host, port);
		this.storageName = storageName;
		this.storageNameSet = true;
		this.timeDataHolder = timeDataHolder;
	}

	/**
	 * Constructor. Uses the default port 8182.
	 *
	 * @param storageName
	 *            name of new storage
	 * @param timeDataHolder
	 *            [OUTPUT] Holds the start time.
	 * @param host
	 *            Host name of the inspectIT CMR.
	 */
	public StartNewRecording(String storageName, IDataHolder<Date> timeDataHolder, String host) {
		super(host, "8182");
		this.storageName = storageName;
		this.storageNameSet = true;
		this.timeDataHolder = timeDataHolder;
	}

	/**
	 * Constructor. Uses the default port 8182.
	 *
	 * @param storageName
	 *            name of new storage
	 * @param timeDataHolder
	 *            [OUTPUT] Holds the start time.
	 * @param host
	 *            Host name of the inspectIT CMR.
	 */
	public StartNewRecording(IDataHolder<Date> timeDataHolder, String host) {
		super(host, "8182");
		this.storageNameSet = false;
		this.timeDataHolder = timeDataHolder;
	}

	@Override
	public void execute(Context context) throws JsonParseException, JsonMappingException, IOException {
		Date currentDate = new Date();
		String pattern = "yyyy-MM-dd-HH-mm-ss";
		SimpleDateFormat format = new SimpleDateFormat(pattern);
		if (!storageNameSet) {
			this.storageName = context.toString().replace(Context.SEPARATOR, "-").replace("#", "-") + "--" + format.format(currentDate);
		}
		// Set startDate
		timeDataHolder.set(new Date());

		// Create new storage
		String creationResponse = get("/rest/storage/" + storageName + "/create", String.class);

		// Get id of created storage
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.readValue(creationResponse, ObjectNode.class);
		String storageId = node.get("storage").get("id").asText();

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("/rest/storage/start").queryParam("id", storageId);
		get(builder.build().encode().toUri().toString(), String.class);

		LOGGER.info("Recording '" + storageName + "' started");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Start new inspectIT recording " + " with storage name " + storageName + " at " + super.toString() + " and stores current time to \"" + timeDataHolder + "\"";
	}
}
