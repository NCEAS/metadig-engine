package edu.ucsb.nceas.mdqengine.dispatch;

import java.util.concurrent.Callable;

public class MockJavaEqualityCheck implements Callable<String> {

	private int x;
	
	private int y;	
	
	@Override
	public String call() throws Exception {
		Boolean result = (x == y);
		return result.toString();
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
