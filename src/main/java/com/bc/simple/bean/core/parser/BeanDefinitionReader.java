package com.bc.simple.bean.core.parser;

import static com.bc.simple.bean.common.util.Constant.ATTR_ALIAS;
import static com.bc.simple.bean.common.util.Constant.ATTR_NAME;
import static com.bc.simple.bean.common.util.Constant.ATTR_RESOURCE;
import static com.bc.simple.bean.common.util.Constant.DOC_ALIAS;
import static com.bc.simple.bean.common.util.Constant.DOC_BEAN;
import static com.bc.simple.bean.common.util.Constant.DOC_BEANS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.config.ConfigLoader;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.BeanFactory;
import com.bc.simple.bean.core.support.SimpleException;


public class BeanDefinitionReader {

	private Log log = LogFactory.getLog(this.getClass());

	private BeanDefinitionParser parser;
	
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
		int countBefore = getBeanFactory().getBeanDefinitionCount();
		doRegisterBeanDefinitions(doc,resource);
		return this.getBeanFactory().getBeanDefinitionCount() - countBefore;
	}


	protected void doRegisterBeanDefinitions(Node root,Resource resource) {
		this.parser = new BeanDefinitionParser(beanFactory, root,resource);
		parseBeanDefinitions(root, this.parser);
	}


	protected void parseBeanDefinitions(Node root, BeanDefinitionParser parser) {
		if (parser.isDefaultNamespace(root)) {
			List<Node> nl = root.getChilds();
			for (Node node : nl) {
				if (parser.isDefaultNamespace(node)) {
					parseDefaultNode(node, parser);
				} else {
					parser.parseCustomNode(node);
				}
			}
		} else {
			parser.parseCustomNode(root);
		}
	}
	
	private void parseDefaultNode(Node ele, BeanDefinitionParser parser) {
		if (parser.nodeNameEquals(ele, Constant.DOC_IMPORT)) {
			importBeanDefinitionResource(ele);
		} else if (parser.nodeNameEquals(ele, DOC_ALIAS)) {
			processAliasRegistration(ele);
		} else if (parser.nodeNameEquals(ele, DOC_BEAN)) {
			processBeanDefinition(ele, parser);
		} else if (parser.nodeNameEquals(ele, DOC_BEANS)) {
			doRegisterBeanDefinitions(ele,parser.getResource());
		}
	}
	protected void importBeanDefinitionResource(Node ele) {
		String location = ele.attrString(ATTR_RESOURCE);
		if (!StringUtils.hasText(location)) {
			return;
		}

		// Discover whether the location is an absolute or relative URI
		boolean absoluteLocation = false;
		// just not support
		absoluteLocation = StringUtils.isUrl(location);

		// Absolute or relative?
		if (absoluteLocation) {
			try {
				loadBeanDefinitions(location);
			} catch (Exception ex) {
				// ignore
			}
		} else {
			Resource relativeResource = this.parser.getResource().createRelative(location);
			if (relativeResource.exists()) {
				loadBeanDefinitions(relativeResource);
			} else {
				String baseLocation = this.parser.getResource().getUri().toString();
				loadBeanDefinitions(StringUtils.applyRelativePath(baseLocation, location));
			}
		}
	}
	protected void processAliasRegistration(Node ele) {
		String name = ele.attrString(ATTR_NAME);
		String alias = ele.attrString(ATTR_ALIAS);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			valid = false;
		}
		if (valid) {
			try {
				this.beanFactory.registerAlias(name, alias);
			} catch (Exception ex) {
				// ignore
			}
		}
	}


	protected void processBeanDefinition(Node ele, BeanDefinitionParser parser) {
		BeanDefinition bd = parser.parseBeanDefinitionNode(ele);
		if (bd != null) {
			try {
				// Register the final decorated instance.
				this.beanFactory.registerBeanDefinition(bd.getBeanName(), bd);
			} catch (Exception ex) {
				log.info("Failed to register bean definition with name '" + bd.getBeanName() + "'" + ele, ex);
			}
		}
	}

}
