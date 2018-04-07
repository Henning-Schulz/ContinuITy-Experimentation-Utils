package org.continuity.experimentation.satellite.rest;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.continuity.experimentation.satellite.KnownApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Henning Schulz
 *
 */
@RestController
@RequestMapping(value = "git")
public class GitEndpoint {

	private static final Logger LOGGER = LoggerFactory.getLogger(GitEndpoint.class);

	@RequestMapping(value = "/{key}/checkout/{version}", method = GET)
	@ResponseBody
	public ResponseEntity<String> checkoutGitVersion(@PathVariable String key, @PathVariable String version) {
		KnownApplication app = KnownApplication.forKey(key);

		if (app == null) {
			LOGGER.error("Could not checkout application {}, because the key is not known!", key);

			return ResponseEntity.badRequest().body("Unknown key: " + key);
		}

		LOGGER.info("Checking out version {} of {}...", version, app);

		Process p;

		try {
			String[] command = { "bash", "-c", app.getGitCheckoutCommand(), version };
			LOGGER.info("Executing the command {}", Arrays.toString(command));

			p = Runtime.getRuntime().exec(command);

			p.waitFor();
			String result = "";
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				result += (line + "\n");
			}

			LOGGER.info("Version {} checked out. Output: {}", version, result);
			return new ResponseEntity<String>(result, HttpStatus.OK);
		} catch (IOException | InterruptedException e) {
			LOGGER.error("Error during checkout!", e);
			return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
