package com.bc.simple.bean.core.support;

import java.util.Map;
import java.util.Properties;

public class Environment {

	private Properties properties;

	private final Map<String, String> env;

	public Environment() {
		env = System.getenv();
		properties = System.getProperties();
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public Map<String, String> getEnv() {
		return env;
	}

}
