package edu.ucsb.nceas.mdqengine.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RunFactory {

	public static Run getMockRun() {
		// make a run
		Run run = new Run();
		run.setId("run.2.1");
		run.setObjectIdentifier("sciMeta.1.1");
		run.setTimestamp(Calendar.getInstance().getTime());

		List<Result> results = new ArrayList<Result>();
		Check check = null;
		Result r = null;
		
		// make some results! 
		check = new Check();
		check.setId("check.1");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.INFO);
		r = new Result();
		r.setStatus(Status.FAILURE);
		r.setOutput(new Output("false"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setId("check.2");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.OPTIONAL);
		r = new Result();
		r.setStatus(Status.SKIP);
		r.setOutput(new Output("false"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setId("check.3");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.INFO);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setOutput(new Output("true"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setId("check.4");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.REQUIRED);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setOutput(new Output("true"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setId("check.5");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.OPTIONAL);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setOutput(new Output("true"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		check = new Check();
		check.setId("check.6");
		check.setEnvironment("r");
		check.setType("metadata");
		check.setLevel(Level.OPTIONAL);
		r = new Result();
		r.setStatus(Status.SUCCESS);
		r.setOutput(new Output("true"));
		r.setTimestamp(Calendar.getInstance().getTime());
		r.setCheck(check);
		results.add(r);
		
		run.setResult(results);

		return run;
	}
}
