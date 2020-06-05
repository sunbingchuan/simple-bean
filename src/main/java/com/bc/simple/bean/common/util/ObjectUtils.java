package com.bc.simple.bean.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ObjectUtils {

	private static final String EMPTY_STRING = "";
	private static final String NULL_STRING = "null";

	public static String getIdentityHexString(Object obj) {
		return Integer.toHexString(System.identityHashCode(obj));
	}


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


	public static boolean isEmpty(Collection<?> collection) {
		return (collection == null || collection.isEmpty());
	}


	public static boolean isEmpty(Map<?, ?> map) {
		return (map == null || map.isEmpty());
	}


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


	public static int nullSafeHashCode(Object obj) {
		if (obj == null) {
			return 0;
		}
		if (obj.getClass().isArray()) {
			if (obj instanceof Object[]) {
				return Arrays.hashCode((Object[]) obj);
			}
			if (obj instanceof boolean[]) {
				return Arrays.hashCode((boolean[]) obj);
			}
			if (obj instanceof byte[]) {
				return Arrays.hashCode((byte[]) obj);
			}
			if (obj instanceof char[]) {
				return Arrays.hashCode((char[]) obj);
			}
			if (obj instanceof double[]) {
				return Arrays.hashCode((double[]) obj);
			}
			if (obj instanceof float[]) {
				return Arrays.hashCode((float[]) obj);
			}
			if (obj instanceof int[]) {
				return Arrays.hashCode((int[]) obj);
			}
			if (obj instanceof long[]) {
				return Arrays.hashCode((long[]) obj);
			}
			if (obj instanceof short[]) {
				return Arrays.hashCode((short[]) obj);
			}
		}
		return obj.hashCode();
	}


	public static String identityToString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return obj.getClass().getName() + "@" + getIdentityHexString(obj);
	}


	public static String getDisplayString(Object obj) {
		if (obj == null) {
			return EMPTY_STRING;
		}
		return nullSafeToString(obj);
	}


	public static String nullSafeClassName(Object obj) {
		return (obj != null ? obj.getClass().getName() : NULL_STRING);
	}


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
	
}
