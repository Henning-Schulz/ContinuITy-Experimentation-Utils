package org.continuity.experimentation.action;

import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

public class Clock implements IExperimentAction {

	private final IDataHolder<Date> dateOutput;

	private final int hourOffset;

	private final int minuteOffset;

	private final int secondOffset;

	private Clock(IDataHolder<Date> dateOutput, int hourOffset, int minuteOffset, int secondOffset) {
		this.dateOutput = dateOutput;
		this.hourOffset = hourOffset;
		this.minuteOffset = minuteOffset;
		this.secondOffset = secondOffset;
	}

	private Clock(IDataHolder<Date> dateOutput) {
		this(dateOutput, 0, 0, 0);
	}

	public static Clock takeTime(IDataHolder<Date> dateOutput) {
		return new Clock(dateOutput);
	}

	public static Clock takeTime(IDataHolder<Date> dateOutput, int hourOffset, int minuteOffset, int secondOffset) {
		return new Clock(dateOutput, hourOffset, minuteOffset, secondOffset);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		Date date = new Date();

		if (hourOffset != 0) {
			date = DateUtils.addHours(date, hourOffset);
		}

		if (minuteOffset != 0) {
			date = DateUtils.addMinutes(date, minuteOffset);
		}

		if (secondOffset != 0) {
			date = DateUtils.addSeconds(date, secondOffset);
		}

		dateOutput.set(date);
	}

}
