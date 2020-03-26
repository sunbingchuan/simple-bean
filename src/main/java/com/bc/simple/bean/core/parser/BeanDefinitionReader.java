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
import com.bc.simple.bean.core.support.SimpleException;


public class BeanDefinitionReader {

	private Log log = LogFactory.getLog(this.getClass());

	private final BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	private final ThreadLocal<Set<Resource>> resourcesCurrentlyBeingLoaded = new ThreadLocal<>();


	public BeanDefinitionReader(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}


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


	public int loadBeanDefinitions(Resource resource) {

		Set<Resource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
		if (currentResources == null) {
			currentResources = new HashSet<>(4);
			this.resourcesCurrentlyBeingLoaded.set(currentResources);
		}
		if (!currentResources.add(resource)) {
			throw new SimpleException(
					"Detected cycle loading of " + resource + " , please check your import definitions!");
		}
		try {
			InputStream inputStream = resource.getInputStream();
			try {
				return doLoadBeanDefinitions(resource);
			} finally {
				inputStream.close();
			}
		} catch (IOException ex) {
			throw new SimpleException("IOException parsing XML document from " + resource, ex);
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
			throw new SimpleException("Parse beandefinitions failed!", e);
		}

		return count;
	}

	public int loadBeanDefinitions(String location) {
		try {
			Resource resource = new Resource(this.beanClassLoader.getResource(location));
			return loadBeanDefinitions(resource);
		} catch (URISyntaxException e) {
			log.info(location + " load failed!");
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


	public int registerBeanDefinitions(Node doc, Resource resource) {
		BeanDefinitionDocumentReader documentReader =
				new BeanDefinitionDocumentReader(this, this.getBeanFactory(), resource);
		int countBefore = getBeanFactory().getBeanDefinitionCount();
		documentReader.registerBeanDefinitions(doc);
		return this.getBeanFactory().getBeanDefinitionCount() - countBefore;
	}

}
