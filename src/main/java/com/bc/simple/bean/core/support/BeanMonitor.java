package com.bc.simple.bean.core.support;

public interface BeanMonitor {

	default void afterPropertiesSet() {

	};

	default void init() {

	};

	default void onException() {

	};

	default void close() {

	};

	default void flush() {

	}

}
