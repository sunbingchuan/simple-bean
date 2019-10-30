package com.bc.simple.bean.common.reactor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Executer {
	private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 100, 30, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>());

	public static void execute(Runnable task) {
		executor.execute(task);
	}

	public static void shutdown() {
		executor.shutdown();
	}

	public static Future<?> submit(Runnable task) {
		return executor.submit(task);
	}

	public static <T> Future<T> submit(Callable<T> task) {
		return executor.submit(task);
	}

}
