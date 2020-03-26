package com.bc.simple.bean.common.reactor;

import com.bc.simple.bean.common.reactor.po.Beaker;
import com.bc.simple.bean.common.reactor.po.ReactBeaker;
import com.bc.simple.bean.core.support.SimpleException;

public class Laboratory {
	private Beaker result;

	public static Beaker react(Reactor reactor, Beaker init, int resultBeakerSize) {
		return new Laboratory(resultBeakerSize).react0(reactor, init);
	}

	private Laboratory(int resultBeakerSize) {
		this.result = Beaker.fromSize(resultBeakerSize);
	}

	public Beaker react0(Reactor reactor, Beaker init) {
		ReactorRunner runner = new ReactorRunner(reactor, init);
		Executer.execute(runner);
		return getResult();
	}

	class ReactorRunner implements Runnable {

		private Reactor reactor;
		private Beaker init;

		public ReactorRunner(Reactor reactor, Beaker init) {
			this.reactor = reactor;
			this.init = init;
		}

		@Override
		public void run() {
			try {
				init.waitForElement();
			} catch (InterruptedException e) {
				throw new SimpleException(e);
			}
			reactor.react(init);
			ReactBeaker[] reactBeakers = reactor.pipe();
			if (reactBeakers == null) {
				return;
			}
			for (ReactBeaker reactBeaker : reactBeakers) {
				if (reactBeaker.getReactor() == null) {
					result.addAll(reactBeaker.getBeaker());
				} else {
					Executer.execute(new ReactorRunner(reactBeaker.getReactor(), reactBeaker.getBeaker()));
				}
			}
		}

	}

	public Beaker getResult() {
		try {
			this.result.waitForElement();
		} catch (InterruptedException e) {
		}
		return result;
	}

}
