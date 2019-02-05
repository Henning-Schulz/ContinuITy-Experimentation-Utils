package org.continuity.experimentation.satellite.rest;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Can be used for buffering any kind of data and retrieving it via REST (e.g., traces from a CMR).
 *
 * @author Henning Schulz
 *
 */
@RestController
@RequestMapping(value = "/buffer")
public class DataBuffer {

	private static final String LINK_PREFIX = "/buffer/data-";

	private final AtomicInteger counter = new AtomicInteger(0);

	private final Map<String, String> storage = new ConcurrentHashMap<>();

	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public ResponseEntity<String> upload(HttpServletRequest request, @RequestBody String data) {
		String key = LINK_PREFIX + counter.incrementAndGet();
		String url = UriComponentsBuilder.fromPath(key).host(request.getRemoteHost()).port(request.getRemotePort()).build().toString();
		storage.put(key, data);
		return ResponseEntity.created(URI.create(url)).body(key);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	public ResponseEntity<String> get(@PathVariable("id") String id) {
		String data = storage.get(id);

		if (data == null) {
			return ResponseEntity.notFound().build();
		} else {
			return ResponseEntity.ok(data);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<String> delete(@PathVariable("id") String id) {
		String data = storage.remove(id);

		if (data == null) {
			return ResponseEntity.notFound().build();
		} else {
			return ResponseEntity.ok(data);
		}
	}

}
