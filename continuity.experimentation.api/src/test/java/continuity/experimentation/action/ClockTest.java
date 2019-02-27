package continuity.experimentation.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.assertj.core.data.Offset;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.Clock;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.junit.Test;

public class ClockTest {

	private static final long SECOND_MILLIS = 1000;
	private static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
	private static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;

	@Test
	public void test() throws AbortException, Exception {
		IDataHolder<Date> dateHolder = new SimpleDataHolder<>("date", Date.class);

		Date date = new Date();
		Clock.takeTime(dateHolder, 1, -2, 3).execute(new Context());

		assertThat(dateHolder.get().getTime()).isCloseTo(((date.getTime() + HOUR_MILLIS) - (2 * MINUTE_MILLIS)) + (3 * SECOND_MILLIS), Offset.offset(500L));
	}

}
