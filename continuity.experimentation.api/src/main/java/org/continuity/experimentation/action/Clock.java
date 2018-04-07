package org.continuity.experimentation.action;

import java.util.Date;

import org.continuity.experimentation.Context;
import org.continuity.experimentation.IExperimentAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

public class Clock implements IExperimentAction {

	private final IDataHolder<Date> dateOutput;

	private Clock(IDataHolder<Date> dateOutput) {
		this.dateOutput = dateOutput;
	}

	public static Clock takeTime(IDataHolder<Date> dateOutput) {
		return new Clock(dateOutput);
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		dateOutput.set(new Date());
	}

}
