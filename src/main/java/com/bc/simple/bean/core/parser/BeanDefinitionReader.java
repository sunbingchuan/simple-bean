package com.bc.simple.bean.core.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.config.ConfigLoader;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.core.support.CurrencyException;

/**
 * Abstract base class for bean definition readers which implement the
 * {@link BeanDefinitionReader} interface.
 *
 * <p>
 * Provides common properties like the bean factory to work on and the class
 * loader to use for loading bean classes.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 11.12.2003
 * @see BeanDefinitionReaderUtils
 */
public class BeanDefinitionReader {

	private Log log = LogFactory.getLog(this.getClass());

	private final BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	private final ThreadLocal<Set<Resource>> resourcesCurrentlyBeingLoaded = new ThreadLocal<>();

	/**
	 * Create a new AbstractBeanDefinitionReader for the given bean factory.
	 * <p>
	 * If the passed-in bean factory does not only implement the
	 * BeanDefinitionRegistry interface but also the ResourceLoader interface, it
	 * will be used as default ResourceLoader as well. This will usually be the case
	 * for {@link org.springframework.context.ApplicationContext} implementations.
	 * <p>
	 * If given a plain BeanDefinitionRegistry, the default ResourceLoader will be a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}.
	 * <p>
	 * If the passed-in bean factory also implements {@link EnvironmentCapable} its
	 * environment will be used by this reader. Otherwise, the reader will
	 * initialize and use a {@link StandardEnvironment}. All ApplicationContext
	 * implementations are EnvironmentCapable, while normal BeanFactory
	 * implementations are not.
	 * 
	 * @param registry the BeanFactory to load bean definitions into, in the form of
	 *                 a BeanDefinitionRegistry
	 * @see #setResourceLoader
	 * @see #setEnvironment
	 */
	public BeanDefinitionReader(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Set the ClassLoader to use for bean classes.
	 * <p>
	 * Default is {@code null}, which suggests to not load bean classes eagerly but
	 * rather to just register bean definitions with class names, with the
	 * corresponding Classes to be resolved later (or never).
	 * 
	 * @see Thread#getContextClassLoader()
	 */
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	public int loadBeanDefinitions(Resource... resources) {
		int count = 0;
		for (Resource resource : resources) {
			count += loadBeanDefinitions(resource);
		}
		return count;
	}

	/**
	 * Load bean definitions from the specified XML file.
	 * 
	 * @param resource the resource descriptor for the XML file
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 */
	public int loadBeanDefinitions(Resource resource) {

		Set<Resource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
		if (currentResources == null) {
			currentResources = new HashSet<>(4);
			this.resourcesCurrentlyBeingLoaded.set(currentResources);
		}
		if (!currentResources.add(resource)) {
			throw new CurrencyException("Detected cyclic loading of " + resource + " - check your import definitions!");
		}
		try {
			InputStream inputStream = resource.getInputStream();
			try {
				return doLoadBeanDefinitions(resource);
			} finally {
				inputStream.close();
			}
		} catch (IOException ex) {
			throw new CurrencyException("IOException parsing XML document from " + resource, ex);
		} finally {
			currentResources.remove(resource);
			if (currentResources.isEmpty()) {
				this.resourcesCurrentlyBeingLoaded.remove();
			}
		}
	}

	protected int doLoadBeanDefinitions(Resource resource) {
		int count = 0;
		try {
			Node doc = ConfigLoader.load(resource.getFile());
			registerBeanDefinitions(doc, resource);
		} catch (Exception e) {
			throw new CurrencyException("parse beandefinitions failed!", e);
		}

		return count;
	}

	public int loadBeanDefinitions(String location) {
		try {
			Resource resource = new Resource(this.beanClassLoader.getResource(location));
			return loadBeanDefinitions(resource);
		} catch (URISyntaxException e) {
			log.info(location + "load failed!");
		}
		return 0;
	}

	public int loadBeanDefinitions(String... locations) {
		int count = 0;
		for (String location : locations) {
			count += loadBeanDefinitions(location);
		}
		return count;
	}

	/**
	 * Register the bean definitions contained in the given DOM document. Called by
	 * {@code loadBeanDefinitions}.
	 * <p>
	 * Creates a new instance of the parser class and invokes
	 * {@code registerBeanDefinitions} on it.
	 * 
	 * @param doc      the DOM document
	 * @param resource the resource descriptor (for context information)
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of parsing errors
	 * @see #loadBeanDefinitions
	 * @see #setDocumentReaderClass
	 * @see BeanDefinitionDocumentReader#registerBeanDefinitions
	 */
	public int registerBeanDefinitions(Node doc, Resource resource) {
		BeanDefinitionDocumentReader documentReader = new BeanDefinitionDocumentReader(this, this.getBeanFactory(),
				resource);
		int countBefore = getBeanFactory().getBeanDefinitionCount();
		documentReader.registerBeanDefinitions(doc);
		return this.getBeanFactory().getBeanDefinitionCount() - countBefore;
	}

}
