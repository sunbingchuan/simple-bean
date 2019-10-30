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

	public void putProperty(String key,String value) {
		 this.properties.put(key, value);
	}
	
	public void putProperty(Map<? extends Object, ? extends Object> props) {
		 this.properties.putAll(props);
	}
	
	/**
	 * Return whether the given property key is available for resolution, i.e. if the value for the
	 * given key is not {@code null}.
	 */
	public boolean containsProperty(String key) {
		return properties.containsKey(key);
	}

	/**
	 * Return the property value associated with the given key, or {@code null} if the key cannot be
	 * resolved.
	 * 
	 * @param key the property name to resolve
	 * @see #getProperty(String, String)
	 * @see #getProperty(String, Class)
	 * @see #getRequiredProperty(String)
	 */
	/**
	 * Return the property value associated with the given key, or {@code defaultValue} if the key
	 * cannot be resolved.
	 * 
	 * @param key the property name to resolve
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String)
	 * @see #getProperty(String, Class)
	 */
	String getProperty(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	/**
	 * Return the property value associated with the given key, or {@code null} if the key cannot be
	 * resolved.
	 * 
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * @see #getRequiredProperty(String, Class)
	 */
	@SuppressWarnings("unchecked")
	public <T> T getProperty(String key, Class<T> targetType) {
		return (T) properties.get(key);
	}

	/**
	 * Return the property value associated with the given key, or {@code defaultValue} if the key
	 * cannot be resolved.
	 * 
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String, Class)
	 */
	@SuppressWarnings("unchecked")
	<T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		return (T) properties.getOrDefault(key, defaultValue);
	}

	/**
	 * Return the property value associated with the given key (never {@code null}).
	 * 
	 * @throws IllegalStateException if the key cannot be resolved
	 * @see #getRequiredProperty(String, Class)
	 */
	public String getRequiredProperty(String key) {
		return properties.getProperty(key);
	}

	/**
	 * Return the property value associated with the given key, converted to the given targetType
	 * (never {@code null}).
	 * 
	 * @throws IllegalStateException if the given key cannot be resolved
	 */
	@SuppressWarnings("unchecked")
	<T> T getRequiredProperty(String key, Class<T> targetType) {
		return (T) properties.get(key);
	}

	/**
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding property
	 * values as resolved by {@link #getProperty}. Unresolvable placeholders with no default value
	 * are ignored and passed through unchanged.
	 * 
	 * @param text the String to resolve
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * @see #resolveRequiredPlaceholders
	 * @see org.springframework.util.SystemPropertyUtils#resolvePlaceholders(String)
	 */
	public String resolvePlaceholders(String text) {
		return StringUtils.replacePlaceholders(text, properties, StringUtils.PLACEHOLDER_PREFIX_DOLLAR_BRACES,
				StringUtils.PLACEHOLDER_SUFFIX_BRACES);
	}

	/**
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding property
	 * values as resolved by {@link #getProperty}. Unresolvable placeholders with no default value
	 * will cause an IllegalArgumentException to be thrown.
	 * 
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null} or if any placeholders are
	 *         unresolvable
	 * @see org.springframework.util.SystemPropertyUtils#resolvePlaceholders(String, boolean)
	 */
	public String resolveRequiredPlaceholders(String text) {
		return StringUtils.replacePlaceholders(text, properties, StringUtils.PLACEHOLDER_PREFIX_DOLLAR_BRACES,
				StringUtils.PLACEHOLDER_SUFFIX_BRACES);
	}



}
