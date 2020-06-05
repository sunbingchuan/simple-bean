package com.bc.test.parallel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class ParallelTest {

	private static final ExecutorService executor = Executors.newFixedThreadPool(1000);
	
	private static final int count = 100;
	
	private static CountDownLatch latch = new CountDownLatch(count);
	
	public static void execute(Runnable task) {
		for (int i = count; i >0; i--) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(20);
						latch.countDown();
						latch.await();
						task.run();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
			});
		}
	}
	
	public static void main(String[] args) {
		execute(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("go alway"+System.currentTimeMillis());
				
			}
		});
	}
	
}
