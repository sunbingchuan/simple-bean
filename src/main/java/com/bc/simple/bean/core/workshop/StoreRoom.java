package com.bc.simple.bean.core.workshop;

public class StoreRoom<X,Y,Z> {

	private X x;
	public X getX() {
		return x;
	}
	public void setX(X x) {
		this.x=x;
	}
	
	private Y y;
	public Y getY() {
		return y;
	}
	public void setY(Y y) {
		this.y=y;
	}
	
	private Z z;
	public Z getZ() {
		return z;
	}
	public void setZ(Z z) {
		this.z=z;
	}
	
	public StoreRoom() {
	}
	public StoreRoom(X x,Y y,Z z) {
		this.x=x;
		this.y=y;
		this.z=z;
	}
	
}
