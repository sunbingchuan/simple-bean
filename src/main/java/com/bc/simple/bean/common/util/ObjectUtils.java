package com.bc.simple.bean.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ObjectUtils {

	private static final int INITIAL_HASH = 7;
	private static final int MULTIPLIER = 31;

	private static final String EMPTY_STRING = "";
	private static final String NULL_STRING = "null";
	private static final String ARRAY_START = "{";
	private static final String ARRAY_END = "}";
	private static final String EMPTY_ARRAY = ARRAY_START + ARRAY_END;
	private static final String ARRAY_ELEMENT_SEPARATOR = ", ";

	public static String getIdentityHexString(Object obj) {
		return Integer.toHexString(System.identityHashCode(obj));
	}

	/**
	 * Return the first element in '{@code candidates}' that is contained in
	 * '{@code source}'. If no element in '{@code candidates}' is present in
	 * '{@code source}' returns {@code null}. Iteration order is {@link Collection}
	 * implementation specific.
	 * 
	 * @param source     the source Collection
	 * @param candidates the candidates to search for
	 * @return the first present object, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	public static <E> E findFirstMatch(Collection<?> source, Collection<E> candidates) {
		if (isEmpty(source) || isEmpty(candidates)) {
			return null;
		}
		for (Object candidate : candidates) {
			if (source.contains(candidate)) {
				return (E) candidate;
			}
		}
		return null;
	}

	/**
	 * Return {@code true} if the supplied Collection is {@code null} or empty.
	 * Otherwise, return {@code false}.
	 * 
	 * @param collection the Collection to check
	 * @return whether the given Collection is empty
	 */
	public static boolean isEmpty(Collection<?> collection) {
		return (collection == null || collection.isEmpty());
	}

	/**
	 * Return {@code true} if the supplied Map is {@code null} or empty. Otherwise,
	 * return {@code false}.
	 * 
	 * @param map the Map to check
	 * @return whether the given Map is empty
	 */
	public static boolean isEmpty(Map<?, ?> map) {
		return (map == null || map.isEmpty());
	}

	/**
	 * Determine whether the given array is empty: i.e. {@code null} or of zero
	 * length.
	 * 
	 * @param array the array to check
	 * @see #isEmpty(Object)
	 */
	public static boolean isEmpty(Object[] array) {
		return (array == null || array.length == 0);
	}

	@SuppressWarnings("rawtypes")
	public static int generateId(Object obj) {
		int max = -1;
		if (obj instanceof Map) {
			Map map = (Map) obj;
			for (Object o : map.keySet()) {
				if (o instanceof Integer) {
					int tmp = (int) o;
					if (max <= tmp) {
						max = tmp;
					}
				}
			}
			if (max < 0) {
				for (Object o : map.values()) {
					if (o instanceof Integer) {
						int tmp = (int) o;
						if (max <= tmp) {
							max = tmp;
						}
					}
				}
			}
		} else if (obj instanceof List) {
			List list = (List) obj;
			for (Object o : list) {
				if (o instanceof Integer) {
					int tmp = (int) o;
					if (max <= tmp) {
						max = tmp;
					}
				}
			}
		}
		return max + 1;
	}

	/**
	 * Return as hash code for the given object; typically the value of
	 * {@code Object#hashCode()}}. If the object is an array, this method will
	 * delegate to any of the {@code nullSafeHashCode} methods for arrays in this
	 * class. If the object is {@code null}, this method returns 0.
	 * 
	 * @see Object#hashCode()
	 * @see #nullSafeHashCode(Object[])
	 * @see #nullSafeHashCode(boolean[])
	 * @see #nullSafeHashCode(byte[])
	 * @see #nullSafeHashCode(char[])
	 * @see #nullSafeHashCode(double[])
	 * @see #nullSafeHashCode(float[])
	 * @see #nullSafeHashCode(int[])
	 * @see #nullSafeHashCode(long[])
	 * @see #nullSafeHashCode(short[])
	 */
	public static int nullSafeHashCode(Object obj) {
		if (obj == null) {
			return 0;
		}
		if (obj.getClass().isArray()) {
			if (obj instanceof Object[]) {
				return nullSafeHashCode((Object[]) obj);
			}
			if (obj instanceof boolean[]) {
				return nullSafeHashCode((boolean[]) obj);
			}
			if (obj instanceof byte[]) {
				return nullSafeHashCode((byte[]) obj);
			}
			if (obj instanceof char[]) {
				return nullSafeHashCode((char[]) obj);
			}
			if (obj instanceof double[]) {
				return nullSafeHashCode((double[]) obj);
			}
			if (obj instanceof float[]) {
				return nullSafeHashCode((float[]) obj);
			}
			if (obj instanceof int[]) {
				return nullSafeHashCode((int[]) obj);
			}
			if (obj instanceof long[]) {
				return nullSafeHashCode((long[]) obj);
			}
			if (obj instanceof short[]) {
				return nullSafeHashCode((short[]) obj);
			}
		}
		return obj.hashCode();
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(Object[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (Object element : array) {
			hash = MULTIPLIER * hash + nullSafeHashCode(element);
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(boolean[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (boolean element : array) {
			hash = MULTIPLIER * hash + Boolean.hashCode(element);
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(byte[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (byte element : array) {
			hash = MULTIPLIER * hash + element;
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(char[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (char element : array) {
			hash = MULTIPLIER * hash + element;
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(double[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (double element : array) {
			hash = MULTIPLIER * hash + Double.hashCode(element);
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(float[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (float element : array) {
			hash = MULTIPLIER * hash + Float.hashCode(element);
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(int[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (int element : array) {
			hash = MULTIPLIER * hash + element;
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(long[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (long element : array) {
			hash = MULTIPLIER * hash + Long.hashCode(element);
		}
		return hash;
	}

	/**
	 * Return a hash code based on the contents of the specified array. If
	 * {@code array} is {@code null}, this method returns 0.
	 */
	public static int nullSafeHashCode(short[] array) {
		if (array == null) {
			return 0;
		}
		int hash = INITIAL_HASH;
		for (short element : array) {
			hash = MULTIPLIER * hash + element;
		}
		return hash;
	}

	/**
	 * Return the same value as {@link Boolean#hashCode(boolean)}}.
	 * 
	 * @deprecated as of Spring Framework 5.0, in favor of the native JDK 8 variant
	 */
	@Deprecated
	public static int hashCode(boolean bool) {
		return Boolean.hashCode(bool);
	}

	/**
	 * Return the same value as {@link Double#hashCode(double)}}.
	 * 
	 * @deprecated as of Spring Framework 5.0, in favor of the native JDK 8 variant
	 */
	@Deprecated
	public static int hashCode(double dbl) {
		return Double.hashCode(dbl);
	}

	/**
	 * Return the same value as {@link Float#hashCode(float)}}.
	 * 
	 * @deprecated as of Spring Framework 5.0, in favor of the native JDK 8 variant
	 */
	@Deprecated
	public static int hashCode(float flt) {
		return Float.hashCode(flt);
	}

	/**
	 * Return the same value as {@link Long#hashCode(long)}}.
	 * 
	 * @deprecated as of Spring Framework 5.0, in favor of the native JDK 8 variant
	 */
	@Deprecated
	public static int hashCode(long lng) {
		return Long.hashCode(lng);
	}

	// ---------------------------------------------------------------------
	// Convenience methods for toString output
	// ---------------------------------------------------------------------

	/**
	 * Return a String representation of an object's overall identity.
	 * 
	 * @param obj the object (may be {@code null})
	 * @return the object's identity as String representation, or an empty String if
	 *         the object was {@code null}
	 */
	public static String identityToString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return obj.getClass().getName() + "@" + getIdentityHexString(obj);
	}

	/**
	 * Return a content-based String representation if {@code obj} is not
	 * {@code null}; otherwise returns an empty String.
	 * <p>
	 * Differs from {@link #nullSafeToString(Object)} in that it returns an empty
	 * String rather than "null" for a {@code null} value.
	 * 
	 * @param obj the object to build a display String for
	 * @return a display String representation of {@code obj}
	 * @see #nullSafeToString(Object)
	 */
	public static String getDisplayString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return nullSafeToString(obj);
	}

	/**
	 * Determine the class name for the given object.
	 * <p>
	 * Returns {@code "null"} if {@code obj} is {@code null}.
	 * 
	 * @param obj the object to introspect (may be {@code null})
	 * @return the corresponding class name
	 */
	public static String nullSafeClassName(Object obj) {
		return (obj != null ? obj.getClass().getName() : NULL_STRING);
	}

	/**
	 * Return a String representation of the specified Object.
	 * <p>
	 * Builds a String representation of the contents in case of an array. Returns
	 * {@code "null"} if {@code obj} is {@code null}.
	 * 
	 * @param obj the object to build a String representation for
	 * @return a String representation of {@code obj}
	 */
	public static String nullSafeToString(Object obj) {
		if (obj == null) {
			return NULL_STRING;
		}
		if (obj instanceof String) {
			return (String) obj;
		}
		if (obj instanceof Object[]) {
			return nullSafeToString((Object[]) obj);
		}
		if (obj instanceof boolean[]) {
			return nullSafeToString((boolean[]) obj);
		}
		if (obj instanceof byte[]) {
			return nullSafeToString((byte[]) obj);
		}
		if (obj instanceof char[]) {
			return nullSafeToString((char[]) obj);
		}
		if (obj instanceof double[]) {
			return nullSafeToString((double[]) obj);
		}
		if (obj instanceof float[]) {
			return nullSafeToString((float[]) obj);
		}
		if (obj instanceof int[]) {
			return nullSafeToString((int[]) obj);
		}
		if (obj instanceof long[]) {
			return nullSafeToString((long[]) obj);
		}
		if (obj instanceof short[]) {
			return nullSafeToString((short[]) obj);
		}
		String str = obj.toString();
		return (str != null ? str : EMPTY_STRING);
	}

	// ---------------------------------------------------------------------
	// Convenience methods for content-based equality/hash-code handling
	// ---------------------------------------------------------------------

	/**
	 * Determine if the given objects are equal, returning {@code true} if both are
	 * {@code null} or {@code false} if only one is {@code null}.
	 * <p>
	 * Compares arrays with {@code Arrays.equals}, performing an equality check
	 * based on the array elements rather than the array reference.
	 * 
	 * @param o1 first Object to compare
	 * @param o2 second Object to compare
	 * @return whether the given objects are equal
	 * @see Object#equals(Object)
	 * @see java.util.Arrays#equals
	 */
	public static boolean nullSafeEquals(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		if (o1.equals(o2)) {
			return true;
		}
		if (o1.getClass().isArray() && o2.getClass().isArray()) {
			return arrayEquals(o1, o2);
		}
		return false;
	}

	/**
	 * Compare the given arrays with {@code Arrays.equals}, performing an equality
	 * check based on the array elements rather than the array reference.
	 * 
	 * @param o1 first array to compare
	 * @param o2 second array to compare
	 * @return whether the given objects are equal
	 * @see #nullSafeEquals(Object, Object)
	 * @see java.util.Arrays#equals
	 */
	private static boolean arrayEquals(Object o1, Object o2) {
		if (o1 instanceof Object[] && o2 instanceof Object[]) {
			return Arrays.equals((Object[]) o1, (Object[]) o2);
		}
		if (o1 instanceof boolean[] && o2 instanceof boolean[]) {
			return Arrays.equals((boolean[]) o1, (boolean[]) o2);
		}
		if (o1 instanceof byte[] && o2 instanceof byte[]) {
			return Arrays.equals((byte[]) o1, (byte[]) o2);
		}
		if (o1 instanceof char[] && o2 instanceof char[]) {
			return Arrays.equals((char[]) o1, (char[]) o2);
		}
		if (o1 instanceof double[] && o2 instanceof double[]) {
			return Arrays.equals((double[]) o1, (double[]) o2);
		}
		if (o1 instanceof float[] && o2 instanceof float[]) {
			return Arrays.equals((float[]) o1, (float[]) o2);
		}
		if (o1 instanceof int[] && o2 instanceof int[]) {
			return Arrays.equals((int[]) o1, (int[]) o2);
		}
		if (o1 instanceof long[] && o2 instanceof long[]) {
			return Arrays.equals((long[]) o1, (long[]) o2);
		}
		if (o1 instanceof short[] && o2 instanceof short[]) {
			return Arrays.equals((short[]) o1, (short[]) o2);
		}
		return false;
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(Object[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append(String.valueOf(array[i]));
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(boolean[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}

			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(byte[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(char[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append("'").append(array[i]).append("'");
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(double[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}

			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(float[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}

			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(int[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(long[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

	/**
	 * Return a String representation of the contents of the specified array.
	 * <p>
	 * The String representation consists of a list of the array's elements,
	 * enclosed in curly braces ({@code "{}"}). Adjacent elements are separated by
	 * the characters {@code ", "} (a comma followed by a space). Returns
	 * {@code "null"} if {@code array} is {@code null}.
	 * 
	 * @param array the array to build a String representation for
	 * @return a String representation of {@code array}
	 */
	public static String nullSafeToString(short[] array) {
		if (array == null) {
			return NULL_STRING;
		}
		int length = array.length;
		if (length == 0) {
			return EMPTY_ARRAY;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				sb.append(ARRAY_START);
			} else {
				sb.append(ARRAY_ELEMENT_SEPARATOR);
			}
			sb.append(array[i]);
		}
		sb.append(ARRAY_END);
		return sb.toString();
	}

}
