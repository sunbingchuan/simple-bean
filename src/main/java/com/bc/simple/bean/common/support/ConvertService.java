package com.bc.simple.bean.common.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.core.support.SimpleException;

@SuppressWarnings("rawtypes")
public class ConvertService {

	
	private final Set<ConverterWraper> converters = new LinkedHashSet<>();
	
	private final Map<ConvertiblePair, ConverterWraper> convertCache = new ConcurrentHashMap<>();

	public ConvertService() {
	}

	@SuppressWarnings("unchecked")
	public <S, T> void addConverter(Converter<S, T> converter) {
		List<Class<?>> typeInfo = getRequiredTypeInfo(converter.getClass(), Converter.class);
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your "
					+ "Converter [" + converter.getClass().getName() + "]; does the class parameterize those types?");
		}
		this.converters
				.add(new ConverterWraper<S, T>(converter, (Class<S>) typeInfo.get(0), (Class<T>) typeInfo.get(1)));
	}
	

	public void removeConvertible(Class<?> sourceType, Class<?> targetType) {
		ConverterWraper converterWraper = getConverter(sourceType, targetType);
		if (this.converters.remove(converterWraper)) {
			clearCache();
		}
	}

	public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
		if (sourceType == null) {
			return !targetType.isPrimitive();
		}
		ConverterWraper converter = getConverter(sourceType, targetType);
		return (converter != null);
	}


	
	public <S, T> T convert(S source, Class<S> sourceType, Class<T> targetType) {
		if (sourceType == null) {
			return handleResult(targetType, convertNullSource(targetType));
		}
		ConverterWraper converterWraper = getConverter(sourceType, targetType);
		if (converterWraper != null) {
			@SuppressWarnings("unchecked")
			T result = (T) converterWraper.convert(source);
			return handleResult(targetType, result);
		}
		return handleConverterNotFound(source, sourceType, targetType);
	}

	@SuppressWarnings("unchecked")
	public <S,T> Object convert(S source, Class<T> targetType) {
		return convert(source, source == null ? null : (Class<S>)source.getClass(), targetType);
	}

	@Override
	public String toString() {
		return this.converters.toString();
	}

	private Object convertNullSource(Class<?> targetType) {
		if (targetType == Optional.class) {
			return Optional.empty();
		}
		return null;
	}

	@SuppressWarnings({ "unchecked" })
	protected <S, T> ConverterWraper<S, T> getConverter(Class<S> sourceType, Class<T> targetType) {
		ConvertiblePair key = new ConvertiblePair(sourceType, targetType);
		ConverterWraper converterWraper = this.convertCache.get(key);
		if (converterWraper == null) {
			for (ConverterWraper cw : converters) {
				if (cw.matches(sourceType, targetType)) {
					converterWraper = cw;

				}
			}
		}

		if (converterWraper == null) {
			if (targetType.isAssignableFrom(sourceType)) {
				converterWraper = new ConverterWraper<>(new NoOpConverter<S, T>(), sourceType, targetType);
			} else {
				converterWraper = new ConverterWraper<>(new NullConverter<S, T>(), sourceType, targetType);
			}
		}
		this.convertCache.put(key, converterWraper);
		return converterWraper;
	}

	private List<Class<?>> getRequiredTypeInfo(Class<?> converterClass, Class<?> genericIfc) {
		return BeanUtils.getGenerics(converterClass, genericIfc);
	}

	private void clearCache() {
		if (convertCache.size() > 0) {
			this.convertCache.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private <S, T> T handleConverterNotFound(S source, Class<S> sourceType, Class<T> targetType) {
		if (source == null) {
			assertNotPrimitiveTargetType(targetType);
			return null;
		}
		if ((sourceType == null || targetType.isAssignableFrom(sourceType)) && targetType.isInstance(source)) {
			return (T) source;
		}
		throw new SimpleException("can't converter from " + sourceType + " to " + targetType);
	}

	@SuppressWarnings("unchecked")
	private <T> T handleResult(Class<T> targetType, Object result) {
		if (result == null) {
			assertNotPrimitiveTargetType(targetType);
		}
		return (T) result;
	}

	private void assertNotPrimitiveTargetType(Class<?> targetType) {
		if (targetType.isPrimitive()) {
			new IllegalArgumentException("A null value cannot be assigned to a primitive type");
		}
	}

	private final class ConverterWraper<S, T> {

		private final Converter<S, T> converter;

		private final ConvertiblePair typeInfo;

		public ConverterWraper(Converter<S, T> converter, Class<S> sourceType, Class<T> targetType) {
			this.converter = converter;
			this.typeInfo = new ConvertiblePair(sourceType, targetType);
		}

		public boolean matches(Class<?> sourceType, Class<?> targetType) {
			if (targetType.isAssignableFrom(this.typeInfo.getTargetType())
					&& this.typeInfo.getSourceType().isAssignableFrom(sourceType)) {
				return true;
			}
			return false;
		}

		public T convert(S source) {
			if (source == null) {
				return null;
			}
			return this.converter.convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converter);
		}

	}

	private static class NoOpConverter<S, T> implements Converter<S, T> {

		@SuppressWarnings("unchecked")
		@Override
		public T convert(S source) {
			return (T) source;
		}
	}

	private static class NullConverter<S, T> implements Converter<S, T> {
		@Override
		public T convert(S source) {
			return null;
		}
	}

	@FunctionalInterface
	public interface Converter<S, T> {

		T convert(S source);

	}

	private static final class ConvertiblePair {

		private final Class<?> sourceType;

		private final Class<?> targetType;

		public ConvertiblePair(Class<?> sourceType, Class<?> targetType) {
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		public Class<?> getSourceType() {
			return this.sourceType;
		}

		public Class<?> getTargetType() {
			return this.targetType;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (other instanceof ConvertiblePair) {
				return false;
			}
			ConvertiblePair otherPair = (ConvertiblePair) other;
			return (this.sourceType == otherPair.sourceType && this.targetType == otherPair.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() ^ this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return (this.sourceType.getName() + " -> " + this.targetType.getName());
		}
	}

}
