package com.bc.simple.bean.common.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public class StringUtils {

	/** Prefix for system property placeholders: "${". */
	public static final String PLACEHOLDER_PREFIX_DOLLAR_BRACES = "${";

	/** Suffix for system property placeholders: "}". */
	public static final String PLACEHOLDER_SUFFIX_BRACES = "}";

	/**
	 * Pseudo URL prefix for all matching resources from the class path: "classpath*:" This differs
	 * from ResourceLoader's classpath URL prefix in that it retrieves all matching resources for a
	 * given name (e.g. "/beans.xml"), for example in the root of all deployed JAR files.
	 * 
	 * @see org.springframework.core.io.ResourceLoader#CLASSPATH_URL_PREFIX
	 */
	private static final String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

	private static final String FOLDER_SEPARATOR = "/";

	private static final String WINDOWS_FOLDER_SEPARATOR = "\\\\";

	private static final String TOP_PATH = "..";

	private static final String CURRENT_PATH = ".";

	public static final String COMMA = ",";

	public static final String EMPTY = "";

	/** Suffix for array class names: {@code "[]"}. */
	public static final String ARRAY_SUFFIX = "[]";

	/** The package separator character: {@code '.'}. */
	private static final char PACKAGE_SEPARATOR = '.';

	/** The path separator character: {@code '/'}. */
	private static final char PATH_SEPARATOR = '/';

	/** The CGLIB class separator: {@code "$$"}. */
	public static final String CGLIB_CLASS_SEPARATOR = "$$";

	/** The ".class" file suffix. */
	public static final String CLASS_FILE_SUFFIX = ".class";

	public static boolean hasText(String str) {
		return str != null && str.length() > 0 && containText(str);
	}

	public static boolean containText(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return true;
			}
		}
		return false;
	}


	public static boolean hasLength(String str) {
		return (str != null && !str.isEmpty());
	}


	public static String cleanPath(String path) {
		if (!hasLength(path)) {
			return path;
		}
		String pathToUse = path.replaceAll(WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);
		// Strip prefix from path to analyze, to not treat it as part of the
		// first path element. This is necessary to correctly parse paths like
		// "file:core/../core/io/Resource.class", where the ".." should just
		// strip the first "core" directory while keeping the "file:" prefix.
		int prefixIndex = pathToUse.indexOf(':');
		String prefix = "";
		if (prefixIndex != -1) {
			prefix = pathToUse.substring(0, prefixIndex + 1);
			if (prefix.contains(FOLDER_SEPARATOR)) {
				prefix = "";
			} else {
				pathToUse = pathToUse.substring(prefixIndex + 1);
			}
		}
		if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
			prefix = prefix + FOLDER_SEPARATOR;
			pathToUse = pathToUse.substring(1);
		}
		String[] pathArray = splitByStr(pathToUse, FOLDER_SEPARATOR);
		LinkedList<String> pathElements = new LinkedList<>();
		int tops = 0;

		for (int i = pathArray.length - 1; i >= 0; i--) {
			String element = pathArray[i];
			if (CURRENT_PATH.equals(element)) {
				// Points to current directory - drop it.
			} else if (TOP_PATH.equals(element)) {
				// Registering top path found.
				tops++;
			} else {
				if (tops > 0) {
					// Merging path element with element corresponding to top path.
					tops--;
				} else {
					// Normal path element found.
					pathElements.add(0, element);
				}
			}
		}

		// Remaining top paths need to be retained.
		for (int i = 0; i < tops; i++) {
			pathElements.add(0, TOP_PATH);
		}
		// If nothing else left, at least explicitly point to current path.
		if (pathElements.size() == 1 && "".equals(pathElements.getLast()) && !prefix.endsWith(FOLDER_SEPARATOR)) {
			pathElements.add(0, CURRENT_PATH);
		}
		StringJoiner joiner = new StringJoiner(FOLDER_SEPARATOR);
		for (String cs : pathElements) {
			joiner.add(cs);
		}
		return prefix + joiner.toString();
	}

	public static String[] splitByStr(String str, String delimiter) {
		List<String> result = new ArrayList<String>();
		int index = -1, pos = 0;
		if ((index = str.indexOf(delimiter)) < 0) {
			return new String[] {str};
		}
		while ((index = str.indexOf(delimiter, pos)) >= 0) {

			if (index == 0) {
				pos = index + delimiter.length();
				continue;
			}
			result.add(str.substring(pos, index));
			pos = index + delimiter.length();
		}
		if (pos <= str.length()) {
			result.add(str.substring(pos));
		}

		return result.toArray(new String[result.size()]);
	}


	public static String applyRelativePath(String path, String relativePath) {
		int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR);
		if (separatorIndex != -1) {
			String newPath = path.substring(0, separatorIndex);
			if (!relativePath.startsWith(FOLDER_SEPARATOR)) {
				newPath += FOLDER_SEPARATOR;
			}
			return newPath + relativePath;
		} else {
			return relativePath;
		}
	}

	public static String getSimpleNameByPath(String path) {
		path = cleanPath(path);
		if (path.indexOf(FOLDER_SEPARATOR) >= 0) {
			return path.substring(path.lastIndexOf(FOLDER_SEPARATOR));
		}
		return path;
	}


	public static String[] tokenizeToStringArray(String str, String delimiters, boolean trimTokens,
			boolean ignoreEmptyTokens) {

		if (str == null) {
			return new String[0];
		}

		StringTokenizer st = new StringTokenizer(str, delimiters);
		List<String> tokens = new ArrayList<>();
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (trimTokens) {
				token = token.trim();
			}
			if (!ignoreEmptyTokens || token.length() > 0) {
				tokens.add(token);
			}
		}
		return tokens.toArray(new String[tokens.size()]);
	}

	/**
	 * Return whether the given resource location is a URL: either a special "classpath" or
	 * "classpath*" pseudo URL or a standard URL.
	 * 
	 * @param resourceLocation the location String to check
	 * @return whether the location qualifies as a URL
	 * @see java.net.URL
	 */
	public static boolean isUrl(String resourceLocation) {
		if (resourceLocation == null) {
			return false;
		}
		if (resourceLocation.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
			return true;
		}
		try {
			new URL(resourceLocation);
			return true;
		} catch (MalformedURLException ex) {
			return false;
		}
	}

	public static boolean isEmpty(String str) {
		return str == null || str.length() == 0;
	}

	public static boolean isNotEmpty(String str) {
		return !isEmpty(str);
	}

	public static boolean match(String[] patterns, String str) {
		for (String pattern : patterns) {
			if (match(pattern, str)) {
				return true;
			}
		}
		return false;
	}

	public static boolean match(String pattern, String str) {
		return Pattern.matches(pattern, str);
	}

	public static String getLetters(String str) {
		if (isEmpty(str)) {
			return EMPTY;
		}
		StringBuffer sb = new StringBuffer();
		for (char c : str.toCharArray()) {
			if (Character.isLetter(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}


	@SuppressWarnings("rawtypes")
	public static String replacePlaceholders(String original, Map properties, String placeholderPrefix,
			String placeholderSuffix) {
		StringBuilder result = new StringBuilder(original);
		int startIndex = -1, endIndex = -1;;
		while ((startIndex = result.indexOf(placeholderPrefix, endIndex)) >= 0) {
			endIndex = findMatchEndIndex(result, startIndex + placeholderPrefix.length(), placeholderPrefix,
					placeholderSuffix);
			String placeHolder = result.substring(startIndex + placeholderPrefix.length(), endIndex);
			placeHolder = replacePlaceholders(placeHolder, properties, placeholderPrefix, placeholderSuffix);
			String value = (String) properties.get(placeHolder);
			if (StringUtils.isNotEmpty(value)) {
				result.replace(startIndex, endIndex + placeholderSuffix.length(), value);
				endIndex = startIndex + value.length();
			} else {
				result.replace(startIndex + placeholderPrefix.length(), endIndex, placeHolder);
				endIndex = endIndex + placeholderSuffix.length();
			}
		}
		return result.toString();
	}

	private static int findMatchEndIndex(CharSequence str, int startIndex, String placeholderPrefix,
			String placeholderSuffix) {
		int matchCount = 0;
		for (int i = startIndex; i < str.length(); i++) {
			if (substringMatch(str, i, placeholderPrefix)) {
				matchCount++;
			}
			if (substringMatch(str, i, placeholderSuffix)) {
				if (matchCount == 0) {
					return i;
				}
				matchCount--;
			}
		}
		return -1;
	}


	public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
		if (index + substring.length() > str.length()) {
			return false;
		}
		for (int i = 0; i < substring.length(); i++) {
			if (str.charAt(index + i) != substring.charAt(i)) {
				return false;
			}
		}
		return true;
	}


	public static String capitalize(String str) {
		return changeFirstCharacterCase(str, true);
	}


	public static String uncapitalize(String str) {
		return changeFirstCharacterCase(str, false);
	}

	private static String changeFirstCharacterCase(String str, boolean capitalize) {
		if (!hasLength(str)) {
			return str;
		}

		char baseChar = str.charAt(0);
		char updatedChar;
		if (capitalize) {
			updatedChar = Character.toUpperCase(baseChar);
		} else {
			updatedChar = Character.toLowerCase(baseChar);
		}
		if (baseChar == updatedChar) {
			return str;
		}

		char[] chars = str.toCharArray();
		chars[0] = updatedChar;
		return new String(chars, 0, chars.length);
	}


	public static String convertClassNameToResourcePath(String className) {
		return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}


	public static String replace(String inString, String oldPattern, String newPattern) {
		if (!hasLength(inString) || !hasLength(oldPattern) || newPattern == null) {
			return inString;
		}
		int index = inString.indexOf(oldPattern);
		if (index == -1) {
			// no occurrence -> can return input as-is
			return inString;
		}

		int capacity = inString.length();
		if (newPattern.length() > oldPattern.length()) {
			capacity += 16;
		}
		StringBuilder sb = new StringBuilder(capacity);

		int pos = 0; // our position in the old string
		int patLen = oldPattern.length();
		while (index >= 0) {
			sb.append(inString.substring(pos, index));
			sb.append(newPattern);
			pos = index + patLen;
			index = inString.indexOf(oldPattern, pos);
		}

		// append any characters to the right of a match
		sb.append(inString.substring(pos));
		return sb.toString();
	}

	// uniform utils type switching
	public static String switchString(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof String) {
			return (String) obj;
		}
		return obj.toString();
	}

	public static Integer switchInteger(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof Integer || int.class.isAssignableFrom(obj.getClass())) {
			return (Integer) obj;
		}
		try {
			return Integer.parseInt(obj.toString());
		} catch (Exception e) {
			return null;
		}
	}

	public static Boolean switchBoolean(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof Boolean || boolean.class.isAssignableFrom(obj.getClass())) {
			return (Boolean) obj;
		}
		try {
			return Boolean.parseBoolean(obj.toString());
		} catch (Exception e) {
			return null;
		}
	}


	public static String collectionToDelimitedString(Collection<?> coll, String delim, String prefix, String suffix) {

		if (coll == null || coll.size() == 0) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		Iterator<?> it = coll.iterator();
		while (it.hasNext()) {
			sb.append(prefix).append(it.next()).append(suffix);
			if (it.hasNext()) {
				sb.append(delim);
			}
		}
		return sb.toString();
	}

	public static String toString(Object obj) {
		if (obj == null) {
			return null;
		}
		return obj.toString();
	}
}
