package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.concurrent.Callable;

import edu.ucsb.nceas.mdqengine.model.Result;

public class MockJavaEqualityCheck implements Callable<Result> {

	private int x;
	
	private int y;	
	
	@Override
	public Result call() throws Exception {
		Boolean result = (x == y);
		Result dr = new Result();
		dr.setOutput(result.toString());
		return dr;
	}
	
	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	
	

}
