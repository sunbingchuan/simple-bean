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
import com.bc.simple.bean.core.support.CurrencyException;

/**
 * Base {@link ConversionService} implementation suitable for use in most
 * environments. Indirectly implements {@link ConverterRegistry} as registration
 * API through the {@link ConfigurableConversionService} interface.
 *
 */
public class ConvertService {

	public static final GenericConverter NULL_CONVERTER = new NullConverter();

	private final Converters converters = new Converters();

	private final Map<ConverterCacheKey, GenericConverter> converterCache = new ConcurrentHashMap<>(64);

	// ConverterRegistry implementation

	public ConvertService() {
	}

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

	/**
	 * Return whether conversion between the source type and the target type can be
	 * bypassed.
	 * <p>
	 * More precisely, this method will return true if objects of sourceType can be
	 * converted to the target type by returning the source object unchanged.
	 * 
	 * @param sourceType context about the source type to convert from (may be
	 *                   {@code null} if source is {@code null})
	 * @param targetType context about the target type to convert to (required)
	 * @return {@code true} if conversion can be bypassed; {@code false} otherwise
	 * @throws IllegalArgumentException if targetType is {@code null}
	 * @since 3.2
	 */
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

	/**
	 * Convenience operation for converting a source object to the specified
	 * targetType, where the target type is a descriptor that provides additional
	 * conversion context. Simply delegates to {@link #convert(Object, Class<?>,
	 * Class<?>)} and encapsulates the construction of the source type descriptor
	 * using {@link Class<?>#forObject(Object)}.
	 * 
	 * @param source     the source object
	 * @param targetType the target type
	 * @return the converted value
	 * @throws ConversionException      if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null}, or sourceType
	 *                                  is {@code null} but source is not
	 *                                  {@code null}
	 */

	public Object convert(Object source, Class<?> targetType) {
		return convert(source, source == null ? null : source.getClass(), targetType);
	}

	@Override
	public String toString() {
		return this.converters.toString();
	}

	// Protected template methods

	/**
	 * Template method to convert a {@code null} source.
	 * <p>
	 * The default implementation returns {@code null} or the Java 8
	 * {@link java.util.Optional#empty()} instance if the target type is
	 * {@code java.util.Optional}. Subclasses may override this to return custom
	 * {@code null} objects for specific target types.
	 * 
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the converted null object
	 */

	protected Object convertNullSource(Class<?> sourceType, Class<?> targetType) {
		if (targetType == Optional.class) {
			return Optional.empty();
		}
		return null;
	}

	/**
	 * Hook method to lookup the converter for a given sourceType/targetType pair.
	 * First queries this ConversionService's converter cache. On a cache miss, then
	 * performs an exhaustive search for a matching converter. If no converter
	 * matches, returns the default converter.
	 * 
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the generic converter that will perform the conversion, or
	 *         {@code null} if no suitable converter was found
	 * @see #getDefaultConverter(Class<?>, Class<?>)
	 */

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

	/**
	 * Return the default converter if no converter is found for the given
	 * sourceType/targetType pair.
	 * <p>
	 * Returns a NO_OP Converter if the source type is assignable to the target
	 * type. Returns {@code null} otherwise, indicating no suitable converter could
	 * be found.
	 * 
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the default generic converter that will perform the conversion
	 */

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
		throw new CurrencyException("can't converter from" + sourceType + " to " + targetType);
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

	/**
	 * Adapts a {@link Converter} to a {@link GenericConverter}.
	 */
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

	/**
	 * Adapts a {@link ConverterFactory} to a {@link GenericConverter}.
	 */
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

	/**
	 * Key for use with the converter cache.
	 */
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

	/**
	 * Manages all converters registered with the service.
	 */
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

		/**
		 * Find a {@link GenericConverter} given a source and target type.
		 * <p>
		 * This method will attempt to match all possible converters by working through
		 * the class and interface hierarchy of the types.
		 * 
		 * @param sourceType the source type
		 * @param targetType the target type
		 * @return a matching {@link GenericConverter}, or {@code null} if none found
		 */

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

		/**
		 * Returns an ordered class hierarchy for the given type.
		 * 
		 * @param type the type
		 * @return an ordered list of all classes that the given type extends or
		 *         implements
		 */
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

	/**
	 * Manages converters registered with a specific {@link ConvertiblePair}.
	 */
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

	/**
	 * Internal converter that performs no operation.
	 */
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

	/**
	 * Internal converter that performs no operation.
	 */
	private static class NullConverter implements GenericConverter {

		public NullConverter() {
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return null;
		}

		@Override
		public Object convert(Object source, Class<?> sourceType, Class<?> targetType) {
			return null;
		}

	}

	/**
	 * A converter converts a source object of type {@code S} to a target of type
	 * {@code T}.
	 *
	 * <p>
	 * Implementations of this interface are thread-safe and can be shared.
	 *
	 * <p>
	 * Implementations may additionally implement {@link ConditionalConverter}.
	 *
	 * @author Keith Donald
	 * @since 3.0
	 * @param <S> the source type
	 * @param <T> the target type
	 */
	@FunctionalInterface
	public interface Converter<S, T> {

		/**
		 * Convert the source object of type {@code S} to target type {@code T}.
		 * 
		 * @param source the source object to convert, which must be an instance of
		 *               {@code S} (never {@code null})
		 * @return the converted object, which must be an instance of {@code T}
		 *         (potentially {@code null})
		 * @throws IllegalArgumentException if the source cannot be converted to the
		 *                                  desired target type
		 */
		T convert(S source);

		default boolean matches(Class<?> sourceType, Class<?> targetType) {
			return true;
		}

	}

	/**
	 * Holder for a source-to-target class pair.
	 */
	public static final class ConvertiblePair {

		private final Class<?> sourceType;

		private final Class<?> targetType;

		/**
		 * Create a new source-to-target pair.
		 * 
		 * @param sourceType the source type
		 * @param targetType the target type
		 */
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

	/**
	 * Generic converter interface for converting between two or more types.
	 *
	 * <p>
	 * This is the most flexible of the Converter SPI interfaces, but also the most
	 * complex. It is flexible in that a GenericConverter may support converting
	 * between multiple source/target type pairs (see
	 * {@link #getConvertibleTypes()}. In addition, GenericConverter implementations
	 * have access to source/target {@link Class<?> field context} during the type
	 * conversion process. This allows for resolving source and target field
	 * metadata such as annotations and generics information, which can be used to
	 * influence the conversion logic.
	 *
	 * <p>
	 * This interface should generally not be used when the simpler
	 * {@link Converter} or {@link ConverterFactory} interface is sufficient.
	 *
	 * <p>
	 * Implementations may additionally implement {@link ConditionalConverter}.
	 *
	 * @author Keith Donald
	 * @author Juergen Hoeller
	 * @since 3.0
	 * @see Class<?>
	 * @see Converter
	 * @see ConverterFactory
	 * @see ConditionalConverter
	 */
	public interface GenericConverter {

		/**
		 * Return the source and target types that this converter can convert between.
		 * <p>
		 * Each entry is a convertible source-to-target type pair.
		 * <p>
		 * For {@link ConditionalConverter conditional converters} this method may
		 * return {@code null} to indicate all source-to-target pairs should be
		 * considered.
		 */
		Set<ConvertiblePair> getConvertibleTypes();

		/**
		 * Convert the source object to the targetType described by the
		 * {@code Class<?>}.
		 * 
		 * @param source     the source object to convert (may be {@code null})
		 * @param sourceType the type descriptor of the field we are converting from
		 * @param targetType the type descriptor of the field we are converting to
		 * @return the converted object
		 */
		Object convert(Object source, Class<?> sourceType, Class<?> targetType);

		default boolean matches(Class<?> sourceType, Class<?> targetType) {
			return true;
		}

	}

	/**
	 * A factory for "ranged" converters that can convert objects from S to subtypes
	 * of R.
	 *
	 * <p>
	 * Implementations may additionally implement {@link ConditionalConverter}.
	 *
	 * @author Keith Donald
	 * @since 3.0
	 * @param <S> the source type converters created by this factory can convert
	 *        from
	 * @param <R> the target range (or base) type converters created by this factory
	 *        can convert to; for example {@link Number} for a set of number
	 *        subtypes.
	 * @see ConditionalConverter
	 */
	public interface ConverterFactory<S, R> {

		/**
		 * Get the converter to convert from S to target type T, where T is also an
		 * instance of R.
		 * 
		 * @param            <T> the target type
		 * @param targetType the target type to convert to
		 * @return a converter from S to T
		 */
		<T extends R> Converter<S, T> getConverter(Class<T> targetType);

	}

}
