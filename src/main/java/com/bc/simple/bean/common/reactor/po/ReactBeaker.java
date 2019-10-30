package com.bc.simple.bean.common.reactor.po;

import java.io.Serializable;

import com.bc.simple.bean.common.reactor.Reactor;

public class ReactBeaker implements Serializable {

	private static final long serialVersionUID = 8259614338098234302L;

	private Reactor reactor;

	private Beaker beaker;

	public ReactBeaker(Reactor reactor, Beaker beaker) {
		this.reactor = reactor;
		this.beaker = beaker;
	}

	public Reactor getReactor() {
		return reactor;
	}

	public void setReactor(Reactor reactor) {
		this.reactor = reactor;
	}

	public Beaker getBeaker() {
		return beaker;
	}

	public void setBeaker(Beaker beaker) {
		this.beaker = beaker;
	}

}
