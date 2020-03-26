package com.bc.simple.bean.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.Resource;
import com.sun.istack.internal.Nullable;

/**
 * this class is used to content the methods which is used to resolve resource
 */
public class ResourceUtils {

	private static final Log LOG = LogFactory.getLog(ResourceUtils.class);

	public static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

	public static final String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
	public static final String CLASSPATH_URL_PREFIX = "classpath:";

	public static final String FILE_URL_PREFIX = "file:";

	public static final String JAR_URL_PREFIX = "jar:";

	public static final String WAR_URL_PREFIX = "war:";

	public static final String URL_PROTOCOL_FILE = "file";

	public static final String URL_PROTOCOL_JAR = "jar";

	public static final String URL_PROTOCOL_WAR = "war";

	public static final String URL_PROTOCOL_ZIP = "zip";

	public static final String URL_PROTOCOL_WSJAR = "wsjar";

	public static final String URL_PROTOCOL_VFSZIP = "vfszip";

	public static final String URL_PROTOCOL_VFSFILE = "vfsfile";

	public static final String URL_PROTOCOL_VFS = "vfs";

	public static final String JAR_FILE_EXTENSION = ".jar";

	public static final String JAR_URL_SEPARATOR = "!/";

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


	public static Set<Resource> doFindAllClassPathResources(String path) throws IOException {
		Set<Resource> result = new LinkedHashSet<>(16);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
		while (resourceUrls.hasMoreElements()) {
			URL url = resourceUrls.nextElement();
			result.add(convertResource(url));
		}
		if ("".equals(path)) {
			// The above result is likely to be incomplete, i.e. only containing file system
			// references.
			// We need to have pointers to each of the jar files on the classpath as well...
			addAllClassLoaderJarRoots(cl, result);
		}
		return result;
	}


	public static Resource convertResource(URL url) {
		try {
			return new Resource(url);
		} catch (URISyntaxException e) {
			// ignore
		}
		return null;
	}


	public static void addAllClassLoaderJarRoots(ClassLoader classLoader, Set<Resource> result) {
		if (classLoader instanceof URLClassLoader) {
			try {
				for (URL url : ((URLClassLoader) classLoader).getURLs()) {
					Resource jarResource =
							new Resource(ResourceUtils.JAR_URL_PREFIX + url + ResourceUtils.JAR_URL_SEPARATOR);
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
						+ "] does not support 'getParent()': ", ex);
			}
		}
	}


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
			LOG.info("Failed to evaluate 'java.class.path' manifest entries: ", ex);
		}
	}


	public static void doRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) {
		LOG.info(
				"Searching directory [" + dir.getAbsolutePath() + "] for files matching pattern [" + fullPattern + "]");
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

	public static void analysisClasspathAndRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) {
		List<Resource> classPathDirs = getResources(StringUtils.EMPTY);
		for (Resource classPathDir : classPathDirs) {
			dir.getPath().contains(classPathDir.getPath());
			fullPattern = fullPattern.replace(CLASSPATH_ALL_URL_PREFIX, classPathDir.getPath() + "/");
		}
		doRetrieveMatchingFiles(fullPattern, dir, result);
	}


	public static File[] listDirectory(File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			LOG.info("Could not retrieve contents of directory [" + dir.getAbsolutePath() + "]");
			return new File[0];
		}
		Arrays.sort(files, Comparator.comparing(File::getName));
		return files;
	}


	public static Properties loadAllProperties(String resourceName, @Nullable ClassLoader classLoader) {
		Properties props = new Properties();
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = BeanUtils.getDefaultClassLoader();
		}
		try {
			Enumeration<URL> urls = (classLoaderToUse != null ? classLoaderToUse.getResources(resourceName)
					: ClassLoader.getSystemResources(resourceName));

			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				URLConnection con = url.openConnection();
				InputStream is = con.getInputStream();
				try {
					props.load(is);
				} finally {
					is.close();
				}
			}
		} catch (IOException e) {
			// ignore
		}
		return props;
	}


	public static void main(String[] args) {
		Set<File> result = new HashSet<>();
		File dir = new File("D:/JDP/workspace_blank_bis/SP/target/classes/com/bc/");
		doRetrieveMatchingFiles("D:/JDP/workspace_blank_bis/SP/target/classes/com/bc/*/**/*.class", dir, result);
		System.out.println(result);
	}

}
