package com.bc.simple.bean.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternUtils {

	/** Default path separator: "/". */
	public static final String DEFAULT_PATH_SEPARATOR = "/";

	public static final int CACHE_TURNOFF_THRESHOLD = 65536;

	public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{[^/]+?\\}");

	public static final char[] WILDCARD_CHARS = { '*', '?', '{' };

	public static final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");

	public static final String DEFAULT_VARIABLE_PATTERN = "(.*)";

	public static int skipSeparator(String path, int pos, String separator) {
		int skipped = 0;
		while (path.startsWith(separator, pos + skipped)) {
			skipped += separator.length();
		}
		return skipped;
	}

	public static int skipSegment(String path, int pos, String prefix) {
		int skipped = 0;
		for (int i = 0; i < prefix.length(); i++) {
			char c = prefix.charAt(i);
			if (isWildcardChar(c)) {
				return skipped;
			}
			int currPos = pos + skipped;
			if (currPos >= path.length()) {
				return 0;
			}
			if (c == path.charAt(currPos)) {
				skipped++;
			}
		}
		return skipped;
	}

	public static boolean isWildcardChar(char c) {
		for (char candidate : WILDCARD_CHARS) {
			if (c == candidate) {
				return true;
			}
		}
		return false;
	}

	public static boolean isPotentialMatch(String path, String[] pattDirs, String pathSeparator) {
		int pos = 0;
		for (String pattDir : pattDirs) {
			int skipped = skipSeparator(path, pos, pathSeparator);
			pos += skipped;
			skipped = skipSegment(path, pos, pattDir);
			if (skipped < pattDir.length()) {
				return (skipped > 0 || (pattDir.length() > 0 && isWildcardChar(pattDir.charAt(0))));
			}
			pos += skipped;
		}
		return true;
	}

	public static boolean match(String pattern, String path) {
		return doMatch(pattern, path, true, null, DEFAULT_PATH_SEPARATOR);
	}

	public static boolean matchStart(String pattern, String path) {
		return doMatch(pattern, path, false, null, DEFAULT_PATH_SEPARATOR);
	}

	/**
	 * Actually match the given {@code path} against the given {@code pattern}.
	 * 
	 * @param pattern   the pattern to match against
	 * @param path      the path String to test
	 * @param fullMatch whether a full pattern match is required (else a pattern
	 *                  match as far as the given base path goes is sufficient)
	 * @return {@code true} if the supplied {@code path} matched, {@code false} if
	 *         it didn't
	 */
	public static boolean doMatch(String pattern, String path, boolean fullMatch,
			Map<String, String> uriTemplateVariables, String pathSeparator) {

		if (path.startsWith(pathSeparator) != pattern.startsWith(pathSeparator)) {
			return false;
		}

		String[] pattDirs = StringUtils.tokenizeToStringArray(pattern, pathSeparator, false, true);
		if (fullMatch && !isPotentialMatch(path, pattDirs, pathSeparator)) {
			return false;
		}

		String[] pathDirs = StringUtils.tokenizeToStringArray(path, pathSeparator, false, true);

		int pattIdxStart = 0;
		int pattIdxEnd = pattDirs.length - 1;
		int pathIdxStart = 0;
		int pathIdxEnd = pathDirs.length - 1;

		// Match all elements up to the first **
		while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			String pattDir = pattDirs[pattIdxStart];
			if ("**".equals(pattDir)) {
				break;
			}
			if (!matchStrings(pattDir, pathDirs[pathIdxStart], uriTemplateVariables)) {
				return false;
			}
			pattIdxStart++;
			pathIdxStart++;
		}

		if (pathIdxStart > pathIdxEnd) {
			// Path is exhausted, only match if rest of pattern is * or **'s
			if (pattIdxStart > pattIdxEnd) {
				return (pattern.endsWith(pathSeparator) == path.endsWith(pathSeparator));
			}
			if (!fullMatch) {
				return true;
			}
			if (pattIdxStart == pattIdxEnd && pattDirs[pattIdxStart].equals("*") && path.endsWith(pathSeparator)) {
				return true;
			}
			for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
				if (!pattDirs[i].equals("**")) {
					return false;
				}
			}
			return true;
		} else if (pattIdxStart > pattIdxEnd) {
			// String not exhausted, but pattern is. Failure.
			return false;
		} else if (!fullMatch && "**".equals(pattDirs[pattIdxStart])) {
			// Path start definitely matches due to "**" part in pattern.
			return true;
		}

		// up to last '**'
		while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			String pattDir = pattDirs[pattIdxEnd];
			if (pattDir.equals("**")) {
				break;
			}
			if (!matchStrings(pattDir, pathDirs[pathIdxEnd], uriTemplateVariables)) {
				return false;
			}
			pattIdxEnd--;
			pathIdxEnd--;
		}
		if (pathIdxStart > pathIdxEnd) {
			// String is exhausted
			for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
				if (!pattDirs[i].equals("**")) {
					return false;
				}
			}
			return true;
		}
		// match the strs between '**' s
		while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			int patIdxTmp = -1;
			for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
				if (pattDirs[i].equals("**")) {
					patIdxTmp = i;
					break;
				}
			}
			if (patIdxTmp == pattIdxStart + 1) {
				// '**/**' situation, so skip one
				pattIdxStart++;
				continue;
			}
			// Find the pattern between padIdxStart & padIdxTmp in str between
			// strIdxStart & strIdxEnd
			int patLength = (patIdxTmp - pattIdxStart - 1);
			int strLength = (pathIdxEnd - pathIdxStart + 1);
			int foundIdx = -1;

			strLoop: for (int i = 0; i <= strLength - patLength; i++) {
				for (int j = 0; j < patLength; j++) {
					String subPat = pattDirs[pattIdxStart + j + 1];
					String subStr = pathDirs[pathIdxStart + i + j];
					if (!matchStrings(subPat, subStr, uriTemplateVariables)) {
						continue strLoop;
					}
				}
				foundIdx = pathIdxStart + i;
				break;
			}

			if (foundIdx == -1) {
				return false;
			}

			pattIdxStart = patIdxTmp;
			pathIdxStart = foundIdx + patLength;
		}

		for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
			if (!pattDirs[i].equals("**")) {
				return false;
			}
		}

		return true;
	}

	public static boolean matchStrings(String subPat, String subStr, Map<String, String> uriTemplateVariables) {
		Pattern pattern = makeGlobalPattern(subPat);
		return matchStrings(pattern, subStr, uriTemplateVariables);
	}

	/**
	 * Main entry point.
	 * 
	 * @return {@code true} if the string matches against the pattern, or
	 *         {@code false} otherwise.
	 */
	public static boolean matchStrings(Pattern pattern, String str, Map<String, String> uriTemplateVariables) {
		List<String> variableNames = new ArrayList<>();
		Matcher matcher = pattern.matcher(str);
		if (matcher.matches()) {

			if (uriTemplateVariables != null) {
				// SPR-8455
				if (variableNames.size() != matcher.groupCount()) {
					throw new IllegalArgumentException("The number of capturing groups in the pattern segment "
							+ pattern + " does not match the number of URI template variables it defines, "
							+ "which can occur if capturing groups are used in a URI template regex. "
							+ "Use non-capturing groups instead.");
				}
				for (int i = 1; i <= matcher.groupCount(); i++) {
					String name = variableNames.get(i - 1);
					String value = matcher.group(i);
					uriTemplateVariables.put(name, value);
				}
			}
			return true;
		} else {
			return false;
		}
	}

	public static Pattern makeGlobalPattern(String pattern) {
		List<String> variableNames = new ArrayList<>();
		StringBuilder patternBuilder = new StringBuilder();
		Matcher matcher = GLOB_PATTERN.matcher(pattern);
		int end = 0;
		while (matcher.find()) {
			patternBuilder.append(quote(pattern, end, matcher.start()));
			String match = matcher.group();
			if ("?".equals(match)) {
				patternBuilder.append('.');
			} else if ("*".equals(match)) {
				patternBuilder.append(".*");
			} else if (match.startsWith("{") && match.endsWith("}")) {
				int colonIdx = match.indexOf(':');
				if (colonIdx == -1) {
					patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
					variableNames.add(matcher.group(1));
				} else {
					String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
					patternBuilder.append('(');
					patternBuilder.append(variablePattern);
					patternBuilder.append(')');
					String variableName = match.substring(1, colonIdx);
					variableNames.add(variableName);
				}
			}
			end = matcher.end();
		}
		patternBuilder.append(quote(pattern, end, pattern.length()));
		return Pattern.compile(patternBuilder.toString());
	}

	public static String quote(String s, int start, int end) {
		if (start == end) {
			return "";
		}
		return Pattern.quote(s.substring(start, end));
	}

	public static void main(String[] args) {
		boolean result = PatternUtils.match("1/**/3/4/**/6/7/8/**/10", "1/2/3/4/5/6/7/8/9/10");
		System.out.println(result);
	}

}
