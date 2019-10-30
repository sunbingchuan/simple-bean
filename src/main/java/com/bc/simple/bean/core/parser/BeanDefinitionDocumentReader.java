package com.bc.simple.bean.core.parser;

import static com.bc.simple.bean.common.util.Constant.ATTR_ALIAS;
import static com.bc.simple.bean.common.util.Constant.ATTR_NAME;
import static com.bc.simple.bean.common.util.Constant.ATTR_PROFILE;
import static com.bc.simple.bean.common.util.Constant.ATTR_RESOURCE;
import static com.bc.simple.bean.common.util.Constant.DOC_ALIAS;
import static com.bc.simple.bean.common.util.Constant.DOC_BEAN;
import static com.bc.simple.bean.common.util.Constant.DOC_BEANS;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;

// single
public class BeanDefinitionDocumentReader {

	private Log log = LogFactory.getLog(this.getClass());

	private BeanDefinitionParser parser;

	private BeanDefinitionReader reader;

	private final BeanFactory beanFactory;

	private Resource resource;

	public BeanDefinitionDocumentReader(BeanDefinitionReader reader, BeanFactory beanFactory, Resource resource) {
		this.reader = reader;
		this.beanFactory = beanFactory;
		this.resource = resource;
	}

	/**
	 * This implementation parses bean definitions according to the "spring-beans"
	 * XSD (or DTD, historically).
	 * <p>
	 * Opens a DOM Document; then initializes the default settings specified at the
	 * {@code <beans/>} level; then parses the contained bean definitions.
	 */
	public void registerBeanDefinitions(Node node) {
		doRegisterBeanDefinitions(node);
	}

	/**
	 * Register each bean definition within the given root {@code <beans/>} Node.
	 */

	protected void doRegisterBeanDefinitions(Node root) {
		// Any nested <beans> Nodes will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) parser, which may be null. Create
		// the new (child) parser with a reference to the parent for fallback
		// purposes,
		// then ultimately reset this.parser back to its original (parent) reference.
		// this behavior emulates a stack of parsers without actually necessitating
		// one.
		BeanDefinitionParser parent = this.parser;
		this.parser = createparser(root, parent);
		String profileSpec = root.attrString(ATTR_PROFILE);
		if (StringUtils.hasText(profileSpec)) {
			String[] specifiedProfiles = StringUtils.splitByStr(profileSpec, Constant.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			// We cannot use Profiles.of(...) since profile expressions are not supported
			// in XML config. See SPR-12458 for details.
		}
		preProcessConfig(root);
		parseBeanDefinitions(root, this.parser);
		postProcessConfig(root);
		this.parser = parent;
	}

	protected BeanDefinitionParser createparser(Node root, BeanDefinitionParser parentparser) {
		BeanDefinitionParser parser = new BeanDefinitionParser(beanFactory, root);
		return parser;
	}

	/**
	 * Parse the Nodes at the root level in the document: "import", "alias", "bean".
	 * 
	 * @param root the DOM root Node of the document
	 */
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
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" Node and load the bean definitions from the given resource
	 * into the bean factory.
	 */
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
				this.reader.loadBeanDefinitions(location);
			} catch (Exception ex) {
				// ignore
			}
		} else {
			Resource relativeResource = this.resource.createRelative(location);
			if (relativeResource.exists()) {
				this.reader.loadBeanDefinitions(relativeResource);
			} else {
				String baseLocation = resource.getUri().toString();
				this.reader.loadBeanDefinitions(StringUtils.applyRelativePath(baseLocation, location));
			}
		}
	}

	/**
	 * Process the given alias Node, registering the alias with the registry.
	 */
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
				this.reader.getBeanFactory().registerAlias(name, alias);
			} catch (Exception ex) {
				// ignore
			}
		}
	}

	/**
	 * Process the given bean Node, parsing the bean definition and registering it
	 * with the registry.
	 */
	protected void processBeanDefinition(Node ele, BeanDefinitionParser parser) {
		BeanDefinition bd = parser.parseBeanDefinitionNode(ele);
		if (bd != null) {
			bd = parser.decorateBeanDefinitionIfRequired(ele, bd);
			try {
				// Register the final decorated instance.
				this.reader.getBeanFactory().registerBeanDefinition(bd.getBeanName(), bd);
			} catch (Exception ex) {
				log.info("Failed to register bean definition with name '" + bd.getBeanName() + "'" + ele, ex);
			}
		}
	}

	/**
	 * Allow the XML to be extensible by processing any custom Node types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>
	 * The default implementation is empty. Subclasses can override this method to
	 * convert custom Nodes into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * 
	 * @see #getReaderContext()
	 */
	protected void preProcessConfig(Node root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom Node types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>
	 * The default implementation is empty. Subclasses can override this method to
	 * convert custom Nodes into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * 
	 * @see #getReaderContext()
	 */
	protected void postProcessConfig(Node root) {
	}

	public BeanDefinitionParser getParser() {
		return parser;
	}

	public void setParser(BeanDefinitionParser parser) {
		this.parser = parser;
	}

	public BeanDefinitionReader getReader() {
		return reader;
	}

	public void setReader(BeanDefinitionReader reader) {
		this.reader = reader;
	}

}
