package continuity.experimentation.action.continuity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.continuity.WorkloadModelGeneration;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortInnerException;
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

	private Context context;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		restMock = Mockito.mock(RestTemplate.class);
		workingGenerator = new WorkloadModelGeneration(restMock, "localhost", "8080", "ok", StaticDataHolder.of("WorkloadModelGenerationTest"), input, output);
		errorGenerator = new WorkloadModelGeneration(restMock, "localhost", "8080", "error", StaticDataHolder.of("WorkloadModelGenerationTest"), input, output);

		Map<String, String> returnMap = new HashMap<>();
		returnMap.put("message", RETURN_MESSAGE);
		returnMap.put("link", RETURN_LINK);
		Mockito.when(restMock.postForEntity(ArgumentMatchers.eq("http://localhost:8080/workloadmodel/ok/create"), ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
		.thenReturn(new ResponseEntity<>(returnMap, HttpStatus.ACCEPTED));

		Mockito.when(restMock.postForEntity(ArgumentMatchers.eq("http://localhost:8080/workloadmodel/error/create"), ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
		.thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

		Mockito.when(restMock.getForEntity(ArgumentMatchers.anyString(), ArgumentMatchers.any())).thenReturn(ResponseEntity.ok(null));

		context = new Context();
	}

	@Test
	public void testWorking() throws AbortInnerException {
		workingGenerator.execute(context);

		assertThat(output.get()).isEqualTo(RETURN_LINK);
	}

	@Test(expected = RuntimeException.class)
	public void testError() throws AbortInnerException {
		errorGenerator.execute(context);
	}

}
