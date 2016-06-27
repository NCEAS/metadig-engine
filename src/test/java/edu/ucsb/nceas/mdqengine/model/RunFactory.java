package edu.ucsb.nceas.mdqengine.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RunFactory {

	public static Run getMockRun() {
		// make a run
		Run run = new Run();
		run.setId("run.2.1");

		List<Result> results = new ArrayList<Result>();
		Check check = null;
		Result r = null;
		
		// make some results! 
		check = new Check();
		check.setLevel(Level.INFO);
		r = new Result();
		r.setStatus(Status.FAILURE);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setLevel(Level.WARN);
		r = new Result();
		r.setStatus(Status.SKIP);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setLevel(Level.INFO);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setLevel(Level.SEVERE);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setLevel(Level.WARN);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setLevel(Level.WARN);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		run.setResult(results);

		return run;
	}
}
