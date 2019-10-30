package com.bc.simple.bean.common.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.Resource;

/**
 * this class is used to content the methods which is used to resolve resource
 */
public class ResourceUtils {

	private static final Log LOG = LogFactory.getLog(ResourceUtils.class);

	public static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

	/**
	 * Pseudo URL prefix for all matching resources from the class path:
	 * "classpath*:" This differs from ResourceLoader's classpath URL prefix in that
	 * it retrieves all matching resources for a given name (e.g. "/beans.xml"), for
	 * example in the root of all deployed JAR files.
	 * 
	 * @see org.springframework.core.io.ResourceLoader#CLASSPATH_URL_PREFIX
	 */
	public static final String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
	/** Pseudo URL prefix for loading from the class path: "classpath:". */
	public static final String CLASSPATH_URL_PREFIX = "classpath:";

	/** URL prefix for loading from the file system: "file:". */
	public static final String FILE_URL_PREFIX = "file:";

	/** URL prefix for loading from a jar file: "jar:". */
	public static final String JAR_URL_PREFIX = "jar:";

	/** URL prefix for loading from a war file on Tomcat: "war:". */
	public static final String WAR_URL_PREFIX = "war:";

	/** URL protocol for a file in the file system: "file". */
	public static final String URL_PROTOCOL_FILE = "file";

	/** URL protocol for an entry from a jar file: "jar". */
	public static final String URL_PROTOCOL_JAR = "jar";

	/** URL protocol for an entry from a war file: "war". */
	public static final String URL_PROTOCOL_WAR = "war";

	/** URL protocol for an entry from a zip file: "zip". */
	public static final String URL_PROTOCOL_ZIP = "zip";

	/** URL protocol for an entry from a WebSphere jar file: "wsjar". */
	public static final String URL_PROTOCOL_WSJAR = "wsjar";

	/** URL protocol for an entry from a JBoss jar file: "vfszip". */
	public static final String URL_PROTOCOL_VFSZIP = "vfszip";

	/** URL protocol for a JBoss file system resource: "vfsfile". */
	public static final String URL_PROTOCOL_VFSFILE = "vfsfile";

	/** URL protocol for a general JBoss VFS resource: "vfs". */
	public static final String URL_PROTOCOL_VFS = "vfs";

	/** File extension for a regular jar file: ".jar". */
	public static final String JAR_FILE_EXTENSION = ".jar";

	/** Separator between JAR URL and file path within the JAR: "!/". */
	public static final String JAR_URL_SEPARATOR = "!/";

	/** Special separator between WAR URL and jar part on Tomcat. */
	public static final String WAR_URL_SEPARATOR = "*/";

	public static List<Resource> getResources(String path) {
		return getResources(path, null);
	}

	public static String getUnPatternRootDir(String path) {
		int endIndex = path.length();
		for (int i = 0; i < path.length(); i++) {
			if (path.charAt(i) == '*') {
				endIndex = path.lastIndexOf("/", i);
				break;
			}
		}
		return path.substring(0, endIndex);
	}

	public static List<Resource> getResources(String path, ClassLoader classLoader) {

		List<Resource> resources = new ArrayList<>();
		try {
			Enumeration<URL> em;
			if (classLoader == null) {
				em = ClassLoader.getSystemResources(path);
			} else {
				em = classLoader.getResources(path);
			}
			while (em.hasMoreElements()) {
				URL url = em.nextElement();
				resources.add(new Resource(url));
			}
		} catch (IOException e) {
			// ignore
		} catch (URISyntaxException e) {
			// ignore
		}
		return resources;
	}

	/**
	 * Find all class location resources with the given path via the ClassLoader.
	 * Called by {@link #findAllClassPathResources(String)}.
	 * 
	 * @param path the absolute path within the classpath (never a leading slash)
	 * @return a mutable Set of matching Resource instances
	 * @since 4.1.1
	 */
	public static Set<Resource> doFindAllClassPathResources(String path) throws IOException {
		Set<Resource> result = new LinkedHashSet<>(16);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
		while (resourceUrls.hasMoreElements()) {
			URL url = resourceUrls.nextElement();
			result.add(convertClassLoaderURL(url));
		}
		if ("".equals(path)) {
			// The above result is likely to be incomplete, i.e. only containing file system
			// references.
			// We need to have pointers to each of the jar files on the classpath as well...
			addAllClassLoaderJarRoots(cl, result);
		}
		return result;
	}

	/**
	 * Convert the given URL as returned from the ClassLoader into a
	 * {@link Resource}.
	 * <p>
	 * The default implementation simply creates a {@link UrlResource} instance.
	 * 
	 * @param url a URL as returned from the ClassLoader
	 * @return the corresponding Resource object
	 * @see java.lang.ClassLoader#getResources
	 * @see org.springframework.core.io.Resource
	 */
	public static Resource convertClassLoaderURL(URL url) {
		try {
			return new Resource(url);
		} catch (URISyntaxException e) {
			// ignore
		}
		return null;
	}

	/**
	 * Search all {@link URLClassLoader} URLs for jar file references and add them
	 * to the given set of resources in the form of pointers to the root of the jar
	 * file content.
	 * 
	 * @param classLoader the ClassLoader to search (including its ancestors)
	 * @param result      the set of resources to add jar roots to
	 * @since 4.1.1
	 */
	public static void addAllClassLoaderJarRoots(ClassLoader classLoader, Set<Resource> result) {
		if (classLoader instanceof URLClassLoader) {
			try {
				for (URL url : ((URLClassLoader) classLoader).getURLs()) {
					Resource jarResource = new Resource(
							ResourceUtils.JAR_URL_PREFIX + url + ResourceUtils.JAR_URL_SEPARATOR);
					if (jarResource.exists()) {
						result.add(jarResource);
					}
				}
			} catch (Exception ex) {
				LOG.info("Cannot introspect jar files since ClassLoader [" + classLoader
						+ "] does not support 'getURLs()': " + ex);
			}
		}

		if (classLoader == ClassLoader.getSystemClassLoader()) {
			// "java.class.path" manifest evaluation...
			addClassPathManifestEntries(result);
		}

		if (classLoader != null) {
			try {
				// Hierarchy traversal...
				addAllClassLoaderJarRoots(classLoader.getParent(), result);
			} catch (Exception ex) {
				LOG.info("Cannot introspect jar files in parent ClassLoader since [" + classLoader
						+ "] does not support 'getParent()': " + ex);
			}
		}
	}

	/**
	 * Determine jar file references from the "java.class.path." manifest property
	 * and add them to the given set of resources in the form of pointers to the
	 * root of the jar file content.
	 * 
	 * @param result the set of resources to add jar roots to
	 * @since 4.3
	 */
	public static void addClassPathManifestEntries(Set<Resource> result) {
		try {
			String javaClassPathProperty = System.getProperty("java.class.path");
			for (String path : StringUtils.splitByStr(javaClassPathProperty, System.getProperty("path.separator"))) {
				String filePath = new File(path).getAbsolutePath();
				int prefixIndex = filePath.indexOf(':');
				if (prefixIndex == 1) {
					// Possibly "c:" drive prefix on Windows, to be upper-cased for proper
					// duplicate detection
					filePath = StringUtils.capitalize(filePath);
				}
				Resource jarResource = new Resource(ResourceUtils.JAR_URL_PREFIX + ResourceUtils.FILE_URL_PREFIX
						+ filePath + ResourceUtils.JAR_URL_SEPARATOR);
				// Potentially overlapping with URLClassLoader.getURLs() result above!
				if (!result.contains(jarResource) && jarResource.exists()) {
					result.add(jarResource);
				}
			}
		} catch (Exception ex) {
			LOG.info("Failed to evaluate 'java.class.path' manifest entries: " + ex);
		}
	}

	/**
	 * Recursively retrieve files that match the given pattern, adding them to the
	 * given result list.
	 * 
	 * @param fullPattern the pattern to match against, with prepended root
	 *                    directory path
	 * @param dir         the current directory
	 * @param result      the Set of matching File instances to add to
	 * @throws IOException if directory contents could not be retrieved
	 */
	public static void doRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) {
		List<Resource> classPathDirs = getResources(StringUtils.EMPTY);
		for (Resource classPathDir : classPathDirs) {
			LOG.info("Searching directory [" + dir.getAbsolutePath() + "] for files matching pattern ["
					+ fullPattern + "]");
			fullPattern = fullPattern.replace(CLASSPATH_ALL_URL_PREFIX, classPathDir.getPath() + "/");
			for (File content : listDirectory(dir)) {
				String currPath = StringUtils.replace(content.getAbsolutePath(), File.separator, "/");
				if (content.isDirectory() && PatternUtils.matchStart(fullPattern, currPath + "/")) {
					if (!content.canRead()) {
						LOG.info("Skipping subdirectory [" + dir.getAbsolutePath()
								+ "] because the application is not allowed to read the directory");
					} else {
						doRetrieveMatchingFiles(fullPattern, content, result);
					}
				}
				if (PatternUtils.match(fullPattern, currPath)) {
					result.add(content);
				}
			}
		}
	}

	/**
	 * Determine a sorted list of files in the given directory.
	 * 
	 * @param dir the directory to introspect
	 * @return the sorted list of files (by default in alphabetical order)
	 * @since 5.1
	 * @see File#listFiles()
	 */
	public static File[] listDirectory(File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			LOG.info("Could not retrieve contents of directory [" + dir.getAbsolutePath() + "]");
			return new File[0];
		}
		Arrays.sort(files, Comparator.comparing(File::getName));
		return files;
	}

	public static void main(String[] args) {
		Set<File> result = new HashSet<>();
		File dir = new File("D:/JDP/workspace_blank_bis/SP/target/classes/com/bc/");
		doRetrieveMatchingFiles("D:/JDP/workspace_blank_bis/SP/target/classes/com/bc/*/**/*.class", dir, result);
		System.out.println(result);
	}

}
