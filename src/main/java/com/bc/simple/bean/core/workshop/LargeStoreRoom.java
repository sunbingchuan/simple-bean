package com.bc.simple.bean.core.workshop;

public class LargeStoreRoom<X,Y,Z,M,N> extends StoreRoom<X, Y, Z> {

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
	
	private M m;
	public M getM() {
		return m;
	}
	public void setM(M m) {
		this.m=m;
	}
	
	private N n;
	public N getN() {
		return n;
	}
	public void setN(N n) {
		this.n=n;
	}
	
	public LargeStoreRoom() {
	}
	public LargeStoreRoom(X x,Y y,Z z,M m,N n) {
		this.x=x;
		this.y=y;
		this.z=z;
		this.m=m;
		this.n=n;
	}
	
}
