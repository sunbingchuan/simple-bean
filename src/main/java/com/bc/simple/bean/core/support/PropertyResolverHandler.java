package com.bc.simple.bean.core.support;

import java.util.Map;
import java.util.Properties;

import com.bc.simple.bean.common.util.StringUtils;

public class PropertyResolverHandler {



	private Properties properties;

	private Map<String, String> env = System.getenv();

	public Map<String, String> getEnv() {
		return env;
	}

	public PropertyResolverHandler(Properties properties) {
		this.properties = properties;
	}

	public PropertyResolverHandler() {
		this.properties = new Properties();
	}

	public void putProperty(String key, String value) {
		this.properties.put(key, value);
	}

	public void putProperty(Map<? extends Object, ? extends Object> props) {
		this.properties.putAll(props);
	}


	public boolean containsProperty(String key) {
		return properties.containsKey(key);
	}



	String getProperty(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}


	@SuppressWarnings("unchecked")
	public <T> T getProperty(String key, Class<T> targetType) {
		return (T) properties.get(key);
	}


	@SuppressWarnings("unchecked")
	<T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		return (T) properties.getOrDefault(key, defaultValue);
	}


	public String getRequiredProperty(String key) {
		return properties.getProperty(key);
	}


	@SuppressWarnings("unchecked")
	<T> T getRequiredProperty(String key, Class<T> targetType) {
		return (T) properties.get(key);
	}


	public String resolvePlaceholders(String text) {
		return StringUtils.replacePlaceholders(text, properties, StringUtils.PLACEHOLDER_PREFIX_DOLLAR_BRACES,
				StringUtils.PLACEHOLDER_SUFFIX_BRACES);
	}


	public String resolveRequiredPlaceholders(String text) {
		return StringUtils.replacePlaceholders(text, properties, StringUtils.PLACEHOLDER_PREFIX_DOLLAR_BRACES,
				StringUtils.PLACEHOLDER_SUFFIX_BRACES);
	}



}
