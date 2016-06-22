package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.concurrent.Callable;

public class MockJavaEqualityCheck implements Callable<DispatchResult> {

	private int x;
	
	private int y;	
	
	@Override
	public DispatchResult call() throws Exception {
		Boolean result = (x == y);
		DispatchResult dr = new DispatchResult();
		dr.setValue(result.toString());
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
