package com.bc.simple.bean.common.support;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.support.SimpleException;


public class ConvertService {

	public static final GenericConverter NULL_CONVERTER = new NullConverter();

	private final Converters converters = new Converters();

	private final Map<ConverterCacheKey, GenericConverter> converterCache = new ConcurrentHashMap<>(64);

	// ConverterRegistry implementation

	public ConvertService() {}

	public void addConverter(Converter<?, ?> converter) {
		Class<?>[] typeInfo = getRequiredTypeInfo(converter.getClass(), Converter.class);
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your "
					+ "Converter [" + converter.getClass().getName() + "]; does the class parameterize those types?");
		}
		addConverter(new ConverterAdapter(converter, typeInfo[0], typeInfo[1]));
	}

	public void addConverter(GenericConverter converter) {
		this.converters.add(converter);
		invalidateCache();
	}

	public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType,
			Converter<? super S, ? extends T> converter) {
		addConverter(new ConverterAdapter(converter, sourceType, targetType));
	}

	public void addConverterFactory(ConverterFactory<?, ?> factory) {
		Class<?>[] typeInfo = getRequiredTypeInfo(factory.getClass(), ConverterFactory.class);
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your "
					+ "Converter [" + factory.getClass().getName() + "]; does the class parameterize those types?");
		}
		addConverter(new ConverterFactoryAdapter(factory, new ConvertiblePair(typeInfo[0], typeInfo[1])));
	}

	public void removeConvertible(Class<?> sourceType, Class<?> targetType) {
		this.converters.remove(sourceType, targetType);
		invalidateCache();
	}

	public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
		if (sourceType == null) {
			return true;
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		return (converter != null);
	}


	public boolean canBypassConvert(Class<?> sourceType, Class<?> targetType) {
		if (sourceType == null) {
			return true;
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		return (converter == null);
	}

	public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
		if (sourceType == null) {
			return handleResult(null, targetType, convertNullSource(null, targetType));
		}
		if (source != null && !sourceType.isInstance(source)) {
			throw new IllegalArgumentException("Source to convert from must be an instance of [" + sourceType
					+ "]; instead it was a [" + source.getClass().getName() + "]");
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		if (converter != null) {
			Object result = converter.convert(source, sourceType, targetType);
			return handleResult(sourceType, targetType, result);
		}
		return handleConverterNotFound(source, sourceType, targetType);
	}



	public Object convert(Object source, Class<?> targetType) {
		return convert(source, source == null ? null : source.getClass(), targetType);
	}

	@Override
	public String toString() {
		return this.converters.toString();
	}

	// Protected template methods



	protected Object convertNullSource(Class<?> sourceType, Class<?> targetType) {
		if (targetType == Optional.class) {
			return Optional.empty();
		}
		return null;
	}



	protected GenericConverter getConverter(Class<?> sourceType, Class<?> targetType) {
		ConverterCacheKey key = new ConverterCacheKey(sourceType, targetType);
		GenericConverter converter = this.converterCache.get(key);
		if (NULL_CONVERTER.equals(converter)) {
			return null;
		}
		if (converter != null) {
			return converter;
		}
		converter = this.converters.find(sourceType, targetType);
		if (converter == null) {
			converter = getDefaultConverter(sourceType, targetType);
		}

		if (converter != null) {
			this.converterCache.put(key, converter);
			return converter;
		}
		if (converter == null) {
			this.converterCache.put(key, NULL_CONVERTER);
		}
		return null;
	}



	protected GenericConverter getDefaultConverter(Class<?> sourceType, Class<?> targetType) {
		return targetType.isAssignableFrom(sourceType) ? new NoOpConverter(targetType.toString()) : null;
	}

	// Internal helpers

	private Class<?>[] getRequiredTypeInfo(Class<?> converterClass, Class<?> genericIfc) {
		Type[] genericTypes = null;
		Type type = converterClass.getGenericSuperclass();
		if (type instanceof ParameterizedType && type.equals(genericIfc)) {
			genericTypes = ((ParameterizedType) type).getActualTypeArguments();
		}
		Type[] types = converterClass.getGenericInterfaces();
		for (Type t : types) {
			if (t instanceof ParameterizedType && ((ParameterizedType) t).getRawType().equals(genericIfc)) {
				genericTypes = ((ParameterizedType) t).getActualTypeArguments();
				break;
			}
		}
		Class<?>[] genericCls = new Class<?>[genericTypes.length];
		for (int i = 0; i < genericCls.length; i++) {
			genericCls[i] = (Class<?>) genericTypes[i];
		}
		return genericCls;
	}

	private void invalidateCache() {
		this.converterCache.clear();
	}

	private Object handleConverterNotFound(Object source, Class<?> sourceType, Class<?> targetType) {

		if (source == null) {
			assertNotPrimitiveTargetType(sourceType, targetType);
			return null;
		}
		if ((sourceType == null || targetType.isAssignableFrom(sourceType)) && targetType.isInstance(source)) {
			return source;
		}
		throw new SimpleException("can't converter from " + sourceType + " to " + targetType);
	}

	private Object handleResult(Class<?> sourceType, Class<?> targetType, Object result) {
		if (result == null) {
			assertNotPrimitiveTargetType(sourceType, targetType);
		}
		return result;
	}

	private void assertNotPrimitiveTargetType(Class<?> sourceType, Class<?> targetType) {
		if (targetType.isPrimitive()) {
			new IllegalArgumentException("A null value cannot be assigned to a primitive type");
		}
	}


	@SuppressWarnings("unchecked")
	private final class ConverterAdapter implements GenericConverter {

		private final Converter<Object, Object> converter;

		private final ConvertiblePair typeInfo;

		public ConverterAdapter(Converter<?, ?> converter, Class<?> sourceType, Class<?> targetType) {
			this.converter = (Converter<Object, Object>) converter;
			this.typeInfo = new ConvertiblePair(sourceType, targetType);
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(Class<?> sourceType, Class<?> targetType) {
			// Check raw type first...
			if (this.typeInfo.getTargetType().isAssignableFrom(targetType)
					&& this.typeInfo.getSourceType().isAssignableFrom(sourceType)) {
				return true;
			}
			return false;
		}

		@Override
		public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
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


	@SuppressWarnings("unchecked")
	private final class ConverterFactoryAdapter implements GenericConverter {

		private final ConverterFactory<Object, Object> converterFactory;

		private final ConvertiblePair typeInfo;

		public ConverterFactoryAdapter(ConverterFactory<?, ?> converterFactory, ConvertiblePair typeInfo) {
			this.converterFactory = (ConverterFactory<Object, Object>) converterFactory;
			this.typeInfo = typeInfo;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(Class<?> sourceType, Class<?> targetType) {
			boolean matches = false;
			if (matches) {
				Converter<?, ?> converter = this.converterFactory.getConverter(targetType);
				Class<?>[] types = getRequiredTypeInfo(converter.getClass(), Converter.class);
				if (types[0].isAssignableFrom(sourceType)) {
					matches = false;
				}
			}
			return matches;
		}

		@Override
		public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
			if (source == null) {
				return convertNullSource(sourceType, targetType);
			}
			return this.converterFactory.getConverter(targetType).convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converterFactory);
		}
	}


	private static final class ConverterCacheKey implements Comparable<ConverterCacheKey> {

		private final Class<?> sourceType;

		private final Class<?> targetType;

		public ConverterCacheKey(Class<?> sourceType, Class<?> targetType) {
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ConverterCacheKey)) {
				return false;
			}
			ConverterCacheKey otherKey = (ConverterCacheKey) other;
			return (this.sourceType.equals(otherKey.sourceType)) && this.targetType.equals(otherKey.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 29 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return ("ConverterCacheKey [sourceType = " + this.sourceType + ", targetType = " + this.targetType + "]");
		}

		@Override
		public int compareTo(ConverterCacheKey other) {
			int result = this.sourceType.toString().compareTo(other.sourceType.toString());
			if (result == 0) {
				result = this.targetType.toString().compareTo(other.targetType.toString());
			}
			return result;
		}
	}


	private static class Converters {

		private final Set<GenericConverter> globalConverters = new LinkedHashSet<>();

		private final Map<ConvertiblePair, ConvertersForPair> converters = new LinkedHashMap<>(36);

		public void add(GenericConverter converter) {
			Set<ConvertiblePair> convertibleTypes = converter.getConvertibleTypes();
			if (convertibleTypes == null) {
				this.globalConverters.add(converter);
			} else {
				for (ConvertiblePair convertiblePair : convertibleTypes) {
					ConvertersForPair convertersForPair = getMatchableConverters(convertiblePair);
					convertersForPair.add(converter);
				}
			}
		}

		private ConvertersForPair getMatchableConverters(ConvertiblePair convertiblePair) {
			ConvertersForPair convertersForPair = this.converters.get(convertiblePair);
			if (convertersForPair == null) {
				convertersForPair = new ConvertersForPair();
				this.converters.put(convertiblePair, convertersForPair);
			}
			return convertersForPair;
		}

		public void remove(Class<?> sourceType, Class<?> targetType) {
			this.converters.remove(new ConvertiblePair(sourceType, targetType));
		}



		public GenericConverter find(Class<?> sourceType, Class<?> targetType) {
			// Search the full type hierarchy
			List<Class<?>> sourceCandidates = getClassHierarchy(sourceType);
			List<Class<?>> targetCandidates = getClassHierarchy(targetType);
			for (Class<?> sourceCandidate : sourceCandidates) {
				for (Class<?> targetCandidate : targetCandidates) {
					ConvertiblePair convertiblePair = new ConvertiblePair(sourceCandidate, targetCandidate);
					GenericConverter converter = getRegisteredConverter(sourceType, targetType, convertiblePair);
					if (converter != null) {
						return converter;
					}
				}
			}
			return null;
		}

		private GenericConverter getRegisteredConverter(Class<?> sourceType, Class<?> targetType,
				ConvertiblePair convertiblePair) {

			// Check specifically registered converters
			ConvertersForPair convertersForPair = this.converters.get(convertiblePair);
			if (convertersForPair != null) {
				GenericConverter converter = convertersForPair.getConverter(sourceType, targetType);
				if (converter != null) {
					return converter;
				}
			}
			// Check ConditionalConverters for a dynamic match
			for (GenericConverter globalConverter : this.globalConverters) {
				if (globalConverter.matches(sourceType, targetType)) {
					return globalConverter;
				}
			}
			return null;
		}


		private List<Class<?>> getClassHierarchy(Class<?> type) {
			List<Class<?>> hierarchy = new ArrayList<>(20);
			Set<Class<?>> visited = new HashSet<>(20);
			addToClassHierarchy(0, BeanUtils.resolvePrimitiveIfNecessary(type), false, hierarchy, visited);
			boolean array = type.isArray();

			int i = 0;
			while (i < hierarchy.size()) {
				Class<?> candidate = hierarchy.get(i);
				candidate = (array ? candidate.getComponentType() : BeanUtils.resolvePrimitiveIfNecessary(candidate));
				Class<?> superclass = candidate.getSuperclass();
				if (superclass != null && superclass != Object.class && superclass != Enum.class) {
					addToClassHierarchy(i + 1, candidate.getSuperclass(), array, hierarchy, visited);
				}
				addInterfacesToClassHierarchy(candidate, array, hierarchy, visited);
				i++;
			}

			if (Enum.class.isAssignableFrom(type)) {
				addToClassHierarchy(hierarchy.size(), Enum.class, array, hierarchy, visited);
				addToClassHierarchy(hierarchy.size(), Enum.class, false, hierarchy, visited);
				addInterfacesToClassHierarchy(Enum.class, array, hierarchy, visited);
			}

			addToClassHierarchy(hierarchy.size(), Object.class, array, hierarchy, visited);
			addToClassHierarchy(hierarchy.size(), Object.class, false, hierarchy, visited);
			return hierarchy;
		}

		private void addInterfacesToClassHierarchy(Class<?> type, boolean asArray, List<Class<?>> hierarchy,
				Set<Class<?>> visited) {

			for (Class<?> implementedInterface : type.getInterfaces()) {
				addToClassHierarchy(hierarchy.size(), implementedInterface, asArray, hierarchy, visited);
			}
		}

		private void addToClassHierarchy(int index, Class<?> type, boolean asArray, List<Class<?>> hierarchy,
				Set<Class<?>> visited) {

			if (asArray) {
				type = Array.newInstance(type, 0).getClass();
			}
			if (visited.add(type)) {
				hierarchy.add(index, type);
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ConversionService converters =\n");
			for (String converterString : getConverterStrings()) {
				builder.append('\t').append(converterString).append('\n');
			}
			return builder.toString();
		}

		private List<String> getConverterStrings() {
			List<String> converterStrings = new ArrayList<>();
			for (ConvertersForPair convertersForPair : this.converters.values()) {
				converterStrings.add(convertersForPair.toString());
			}
			Collections.sort(converterStrings);
			return converterStrings;
		}
	}


	private static class ConvertersForPair {

		private final LinkedList<GenericConverter> converters = new LinkedList<>();

		public void add(GenericConverter converter) {
			this.converters.addFirst(converter);
		}

		public GenericConverter getConverter(Class<?> sourceType, Class<?> targetType) {
			for (GenericConverter converter : this.converters) {
				if (converter.matches(sourceType, targetType)) {
					return converter;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return StringUtils.collectionToDelimitedString(this.converters, ",", "", "");
		}
	}


	private static class NoOpConverter implements GenericConverter {

		private final String name;

		public NoOpConverter(String name) {
			this.name = name;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return null;
		}

		@Override
		public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
			return source;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}


	private static class NullConverter implements GenericConverter {

		public NullConverter() {}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return null;
		}

		@Override
		public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
			return null;
		}

	}


	@FunctionalInterface
	public interface Converter<S, T> {


		T convert(S source);

		default boolean matches(Class<?> sourceType, Class<?> targetType) {
			return true;
		}

	}


	public static final class ConvertiblePair {

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
			if (other == null || other.getClass() != ConvertiblePair.class) {
				return false;
			}
			ConvertiblePair otherPair = (ConvertiblePair) other;
			return (this.sourceType == otherPair.sourceType && this.targetType == otherPair.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 31 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return (this.sourceType.getName() + " -> " + this.targetType.getName());
		}
	}


	public interface GenericConverter {


		Set<ConvertiblePair> getConvertibleTypes();


		Object convert(Object source, Class<?> sourceType, Class<?> targetType);

		default boolean matches(Class<?> sourceType, Class<?> targetType) {
			return true;
		}

	}


	public interface ConverterFactory<S, R> {


		<T extends R> Converter<S, T> getConverter(Class<T> targetType);

	}

}
