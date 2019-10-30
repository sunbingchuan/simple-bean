package com.bc.simple.bean.common.reactor;

import com.bc.simple.bean.common.reactor.po.Beaker;
import com.bc.simple.bean.common.reactor.po.ReactBeaker;

public interface Reactor {

	ReactBeaker[] pipe();

	void react(Beaker parameters);

}
