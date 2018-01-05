package continuity.experimentation.action.continuity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.continuity.experimentation.action.continuity.WorkloadModelGeneration;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * @author Henning Schulz
 *
 */
public class WorkloadModelGenerationTest {

	private static final String RETURN_MESSAGE = "A very meaningful message.";
	private static final String RETURN_LINK = "/very/meaningful/link-42";

	WorkloadModelGeneration workingGenerator;
	WorkloadModelGeneration errorGenerator;

	private RestTemplate restMock;

	private SimpleDataHolder<String> input = new SimpleDataHolder<>("dataLink", "foo");
	private SimpleDataHolder<String> output = new SimpleDataHolder<>("workloadLink", String.class);

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		IDataHolder<Boolean> workloadGenBroken = new SimpleDataHolder<>("workload-gen-broken", false);

		restMock = Mockito.mock(RestTemplate.class);
		workingGenerator = new WorkloadModelGeneration(restMock, "localhost", "8080", "ok", "WorkloadModelGenerationTest", input, new SimpleDataHolder<>("startTime", new Date()),
				new SimpleDataHolder<>("startTime", new Date()), output, workloadGenBroken);
		errorGenerator = new WorkloadModelGeneration(restMock, "localhost", "8080", "error", "WorkloadModelGenerationTest", input, new SimpleDataHolder<>("startTime", new Date()),
				new SimpleDataHolder<>("startTime", new Date()), output, workloadGenBroken);

		Map<String, String> returnMap = new HashMap<>();
		returnMap.put("message", RETURN_MESSAGE);
		returnMap.put("link", RETURN_LINK);
		Mockito.when(restMock.postForEntity(ArgumentMatchers.eq("http://localhost:8080/workloadmodel/ok/create"), ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
		.thenReturn(new ResponseEntity<>(returnMap, HttpStatus.ACCEPTED));

		Mockito.when(restMock.postForEntity(ArgumentMatchers.eq("http://localhost:8080/workloadmodel/error/create"), ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
		.thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}

	@Test
	public void testWorking() {
		workingGenerator.execute();

		assertThat(output.get()).isEqualTo(RETURN_LINK);
	}

	@Test(expected = RuntimeException.class)
	public void testError() {
		errorGenerator.execute();
	}

}
