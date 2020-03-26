package com.bc.simple.bean.core.parser;

import static com.bc.simple.bean.common.util.Constant.ANY_VALUE;
import static com.bc.simple.bean.common.util.Constant.ATTRIBUTE;
import static com.bc.simple.bean.common.util.Constant.ATTR_ABSTRACT;
import static com.bc.simple.bean.common.util.Constant.ATTR_AUTOWIRE_CANDIDATE;
import static com.bc.simple.bean.common.util.Constant.ATTR_BEAN;
import static com.bc.simple.bean.common.util.Constant.ATTR_CLASS;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_AUTOWIRE;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_AUTOWIRE_CANDIDATES;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_DESTROY_METHOD;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_INIT_METHOD;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_LAZY_INIT;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEFAULT_MERGE;
import static com.bc.simple.bean.common.util.Constant.ATTR_DEPENDS_ON;
import static com.bc.simple.bean.common.util.Constant.ATTR_DESTROY_METHOD;
import static com.bc.simple.bean.common.util.Constant.ATTR_FACTORY_BEAN;
import static com.bc.simple.bean.common.util.Constant.ATTR_FACTORY_BEAN_CLASS_NAME;
import static com.bc.simple.bean.common.util.Constant.ATTR_FACTORY_METHOD;
import static com.bc.simple.bean.common.util.Constant.ATTR_ID;
import static com.bc.simple.bean.common.util.Constant.ATTR_INDEX;
import static com.bc.simple.bean.common.util.Constant.ATTR_INIT_METHOD;
import static com.bc.simple.bean.common.util.Constant.ATTR_KEY;
import static com.bc.simple.bean.common.util.Constant.ATTR_KEY_REF;
import static com.bc.simple.bean.common.util.Constant.ATTR_KEY_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_LAZY_INIT;
import static com.bc.simple.bean.common.util.Constant.ATTR_MATCH;
import static com.bc.simple.bean.common.util.Constant.ATTR_MERGE;
import static com.bc.simple.bean.common.util.Constant.ATTR_NAME;
import static com.bc.simple.bean.common.util.Constant.ATTR_NODE_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_ORDER;
import static com.bc.simple.bean.common.util.Constant.ATTR_OVERRIDE_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_PARENT;
import static com.bc.simple.bean.common.util.Constant.ATTR_PRIMARY;
import static com.bc.simple.bean.common.util.Constant.ATTR_REF;
import static com.bc.simple.bean.common.util.Constant.ATTR_REF_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_REPLACER;
import static com.bc.simple.bean.common.util.Constant.ATTR_SCOPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_TYPE;
import static com.bc.simple.bean.common.util.Constant.ATTR_VALUE;
import static com.bc.simple.bean.common.util.Constant.ATTR_VALUE_REF;
import static com.bc.simple.bean.common.util.Constant.ATTR_VALUE_TYPE;
import static com.bc.simple.bean.common.util.Constant.AUTOWIRE_BY_NAME_VALUE;
import static com.bc.simple.bean.common.util.Constant.AUTOWIRE_BY_TYPE_VALUE;
import static com.bc.simple.bean.common.util.Constant.DEFAULT_VALUE;
import static com.bc.simple.bean.common.util.Constant.DOC_ARG_TYPE;
import static com.bc.simple.bean.common.util.Constant.DOC_ARRAY;
import static com.bc.simple.bean.common.util.Constant.DOC_BEAN;
import static com.bc.simple.bean.common.util.Constant.DOC_CONSTRUCTOR_ARG;
import static com.bc.simple.bean.common.util.Constant.DOC_DESCRIPTION;
import static com.bc.simple.bean.common.util.Constant.DOC_ENTRY;
import static com.bc.simple.bean.common.util.Constant.DOC_IDREF;
import static com.bc.simple.bean.common.util.Constant.DOC_KEY;
import static com.bc.simple.bean.common.util.Constant.DOC_LIST;
import static com.bc.simple.bean.common.util.Constant.DOC_LOOKUP_METHOD;
import static com.bc.simple.bean.common.util.Constant.DOC_MAP;
import static com.bc.simple.bean.common.util.Constant.DOC_META;
import static com.bc.simple.bean.common.util.Constant.DOC_PROP;
import static com.bc.simple.bean.common.util.Constant.DOC_PROPERTY;
import static com.bc.simple.bean.common.util.Constant.DOC_PROPS;
import static com.bc.simple.bean.common.util.Constant.DOC_REF;
import static com.bc.simple.bean.common.util.Constant.DOC_REPLACED_METHOD;
import static com.bc.simple.bean.common.util.Constant.DOC_SET;
import static com.bc.simple.bean.common.util.Constant.DOC_VALUE;
import static com.bc.simple.bean.common.util.Constant.FALSE_VALUE;
import static com.bc.simple.bean.common.util.Constant.MULTI_VALUE_ATTRIBUTE_DELIMITERS;
import static com.bc.simple.bean.common.util.Constant.OVERRIDE_TYPE_LOOKUP_VALUE;
import static com.bc.simple.bean.common.util.Constant.OVERRIDE_TYPE_REPLACE_VALUE;
import static com.bc.simple.bean.common.util.Constant.PATTERN_ANY;
import static com.bc.simple.bean.common.util.Constant.TRUE_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_ARRAY_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_BEAN_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_LIST_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_MAP_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_META_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_PROPS_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_REF_VALUE;
import static com.bc.simple.bean.common.util.Constant.TYPE_SET_VALUE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.DomUtils;
import com.bc.simple.bean.common.util.ObjectUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.handler.HandlerProxy;
import com.bc.simple.bean.core.support.SimpleException;


@SuppressWarnings({"unchecked", "rawtypes"})
public class BeanDefinitionParser {

	private Log log = LogFactory.getLog(BeanDefinitionParser.class);

	private final BeanFactory beanFactory;

	private final Node root;


	private final Set<String> usedNames = new HashSet<>();

	private final HandlerProxy handler;


	public BeanDefinitionParser(BeanFactory beanFactory, Node root) {
		this.beanFactory = beanFactory;
		this.handler = new HandlerProxy(beanFactory);
		this.root = root;
	}



	public BeanDefinition parseBeanDefinitionNode(Node ele) {
		return parseBeanDefinitionNode(ele, null);
	}



	protected BeanDefinition parseBeanDefinitionNode(Node ele, BeanDefinition containingBean) {
		String id = ele.attrString(ATTR_ID);
		String nameAttr = ele.attrString(ATTR_NAME);

		List<String> aliases = new ArrayList<>();
		if (StringUtils.hasLength(nameAttr)) {
			String[] nameArr =
					StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS, true, true);
			aliases.addAll(Arrays.asList(nameArr));
		}

		String beanName = id;
		if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
			beanName = aliases.remove(0);
		}
		checkNameUniqueness(beanName, aliases, ele);
		BeanDefinition beanDefinition = parseBeanDefinitionNode(ele, beanName, containingBean);
		if (beanDefinition != null) {
			if (!StringUtils.hasText(beanName)) {
				try {
					if (containingBean != null) {
						beanName = BeanUtils.generateBeanName(beanDefinition, beanFactory, true);
					} else if (beanDefinition.isAnnotated()) {
						beanName = BeanUtils.generateAnnotatedBeanName(beanDefinition, beanFactory);
					} else {
						String beanClassName = beanDefinition.getBeanClassName();
						beanName = BeanUtils.generateBeanName(beanDefinition, beanFactory, false);
						if (!beanFactory.isBeanNameInUse(beanClassName)) {
							aliases.add(beanClassName);
						}
					}

				} catch (Exception ex) {
					// ignore
					return null;
				}
			}
			String[] aliasesArray = aliases.toArray(new String[aliases.size()]);
			beanDefinition.setAliases(aliasesArray);
			beanFactory.registerAliases(beanName, aliasesArray);
			beanDefinition.setBeanName(beanName);
			return beanDefinition;
		}
		return null;
	}


	protected void checkNameUniqueness(String beanName, List<String> aliases, Node beanNode) {
		String foundName = null;

		if (StringUtils.hasText(beanName) && this.usedNames.contains(beanName)) {
			foundName = beanName;
		}
		if (foundName == null) {
			foundName = ObjectUtils.findFirstMatch(this.usedNames, aliases);
		}
		if (foundName != null) {
			throw new SimpleException("Bean name '" + foundName + "' is already used in Node " + beanNode + "!");
		}
		this.usedNames.add(beanName);
		this.usedNames.addAll(aliases);
	}



	protected BeanDefinition parseBeanDefinitionNode(Node ele, String beanName, BeanDefinition containingBean) {
		String className = null;
		if (ele.hasAttr(ATTR_CLASS)) {
			className = ele.attrString(ATTR_CLASS).trim();
		}
		try {
			BeanDefinition bd = createBeanDefinition(className);
			parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
			bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DOC_DESCRIPTION));
			parseMetaNodes(ele, bd);
			parseLookupOverrideSubNodes(ele, bd.getOverrideMethodDefinitions());
			parseReplacedMethodSubNodes(ele, bd.getOverrideMethodDefinitions());
			parseConstructorArgNodes(ele, bd);
			parsePropertyNodes(ele, bd);
			bd.setRoot(this.root);
			return bd;
		} catch (ClassNotFoundException ex) {
			throw new SimpleException("Bean class [" + className + "] not found!", ex);
		} catch (NoClassDefFoundError err) {
			throw new SimpleException("Class which bean class [" + className + "] depends on not found!", err);
		} catch (Throwable ex) {
			throw new SimpleException("Error during bean definition parsing", ex);
		}
	}

	/**
	 * Apply the attributes of the given bean Node to the given bean * definition.
	 * 
	 * @param ele bean declaration Node
	 * @param beanName bean name
	 * @param containingBean containing bean definition
	 * @return a bean definition initialized according to the bean Node attributes
	 */
	protected BeanDefinition parseBeanDefinitionAttributes(Node ele, String beanName, BeanDefinition containingBean,
			BeanDefinition bd) {

		if (ele.hasAttr(ATTR_SCOPE)) {
			bd.setScope(ele.attrString(ATTR_SCOPE));
		} else if (containingBean != null) {
			// Take default from containing bean in case of an inner bean definition.
			bd.setScope(containingBean.getScope());
		}

		if (ele.hasAttr(ATTR_ABSTRACT)) {
			bd.setAbstract(TRUE_VALUE.equals(ele.attrString(ATTR_ABSTRACT)));
		}
		String tmp;
		String lazyInit = ele.attrString(ATTR_LAZY_INIT);
		if (StringUtils.isEmpty(lazyInit)) {
			if (StringUtils.isNotEmpty(tmp = this.root.attrString(ATTR_DEFAULT_LAZY_INIT))) {
				lazyInit = tmp;
			} else {
				lazyInit = FALSE_VALUE;
			}
		}
		bd.setLazyInit(TRUE_VALUE.equals(lazyInit));

		if (ele.hasAttr(ATTR_DEPENDS_ON)) {
			String dependsOn = ele.attrString(ATTR_DEPENDS_ON);
			bd.setDependsOn(StringUtils.splitByStr(dependsOn, MULTI_VALUE_ATTRIBUTE_DELIMITERS));
		}

		String autowireCandidate = ele.attrString(ATTR_AUTOWIRE_CANDIDATE);
		if (StringUtils.isEmpty(autowireCandidate)) {
			String candidatePattern = this.root.attrString(ATTR_DEFAULT_AUTOWIRE_CANDIDATES);
			if (candidatePattern != null) {
				String[] patterns = StringUtils.splitByStr(candidatePattern, StringUtils.COMMA);
				bd.setAutowireCandidate(StringUtils.match(patterns, beanName));
			} else {
				bd.setAutowireCandidate(true);
			}
		} else {
			bd.setAutowireCandidate(TRUE_VALUE.equals(autowireCandidate));
		}

		if (ele.hasAttr(ATTR_PRIMARY)) {
			Integer order = StringUtils.switchInteger(ele.attr(ATTR_PRIMARY));
			bd.setBeanOrder(order);
		}

		if (ele.hasAttr(ATTR_ORDER)) {
			bd.setPrimary(TRUE_VALUE.equals(ele.attrString(ATTR_PRIMARY)));
		}


		if (ele.hasAttr(ATTR_INIT_METHOD)) {
			String initMethodName = ele.attrString(ATTR_INIT_METHOD);
			bd.setInitMethodName(initMethodName);
		} else if ((tmp = root.attrString(ATTR_DEFAULT_INIT_METHOD)) != null) {
			bd.setInitMethodName(tmp);
			bd.setEnforceInitMethod(false);
		}

		if (ele.hasAttr(ATTR_DESTROY_METHOD)) {
			String destroyMethodName = ele.attrString(ATTR_DESTROY_METHOD);
			bd.setDestroyMethodName(destroyMethodName);
		} else if ((tmp = root.attrString(ATTR_DEFAULT_DESTROY_METHOD)) != null) {
			bd.setDestroyMethodName(tmp);
			bd.setEnforceDestroyMethod(false);
		}

		if (ele.hasAttr(ATTR_FACTORY_METHOD)) {
			bd.setFactoryMethodName(ele.attrString(ATTR_FACTORY_METHOD));
		}

		if (ele.hasAttr(ATTR_FACTORY_BEAN)) {
			bd.setFactoryBeanName(ele.attrString(ATTR_FACTORY_BEAN));
		}

		if (ele.hasAttr(ATTR_FACTORY_BEAN_CLASS_NAME)) {
			bd.setFactoryBeanClassName(ele.attrString(ATTR_FACTORY_BEAN_CLASS_NAME));
		}

		return bd;
	}


	protected BeanDefinition createBeanDefinition(String className) throws ClassNotFoundException {
		return BeanUtils.createBeanDefinition(className, beanFactory.getBeanClassLoader());
	}

	protected void parseMetaNodes(Node ele, BeanDefinition bd) {
		if (bd == null) {
			throw new SimpleException("BeanDefinition can not be null!");
		}
		List<Node> nl = ele.getChilds();
		for (Node node : nl) {
			if (nodeNameEquals(node, DOC_META)) {
				Node metaNode = node;
				String key = metaNode.attrString(ATTR_KEY);
				String value = metaNode.attrString(ATTR_VALUE);
				bd.setAttribute(key, value);
			}
		}
	}

	protected void parseMetaNodes(Node ele, Object target) {
		if (target == null) {
			throw new SimpleException("target obj can not be null!");
		}
		if (target instanceof Map) {
			Map map = (Map) target;
			List<Node> nl = ele.getChilds();
			for (Node node : nl) {
				if (nodeNameEquals(node, DOC_META)) {
					Node metaNode = node;
					String key = metaNode.attrString(ATTR_KEY);
					String value = metaNode.attrString(ATTR_VALUE);
					Map attribute = new HashMap<>(3);
					attribute.put(ATTR_KEY, key);
					attribute.put(ATTR_VALUE, value);
					attribute.put(ATTR_TYPE, TYPE_META_VALUE);
					map.put(ATTRIBUTE, attribute);
				}
			}
		}

	}

	protected int getAutowireMode(String attValue) {
		String att = attValue;
		if (StringUtils.isEmpty(att)) {
			att = this.root.attrString(ATTR_DEFAULT_AUTOWIRE);
		}
		int autowire = BeanDefinition.AUTOWIRE_NO;
		if (AUTOWIRE_BY_NAME_VALUE.equals(att)) {
			autowire = BeanDefinition.AUTOWIRE_BY_NAME;
		} else if (AUTOWIRE_BY_TYPE_VALUE.equals(att)) {
			autowire = BeanDefinition.AUTOWIRE_BY_TYPE;
		}
		return autowire;
	}


	protected void parseConstructorArgNodes(Node beanEle, BeanDefinition bd) {
		List<Node> nl = beanEle.getChilds();
		for (Node node : nl) {
			if (nodeNameEquals(node, DOC_CONSTRUCTOR_ARG)) {
				parseConstructorArgNode(node, bd);
			}
		}
	}


	protected void parsePropertyNodes(Node beanEle, BeanDefinition bd) {
		List<Node> nl = beanEle.getChilds();
		for (Node node : nl) {
			if (nodeNameEquals(node, DOC_PROPERTY)) {
				parsePropertyNode(node, bd);
			}
		}
	}


	protected void parseLookupOverrideSubNodes(Node beanEle, Set<Map<String, Object>> overrides) {
		List<Node> nl = beanEle.getChilds();
		for (Node node : nl) {
			if (nodeNameEquals(node, DOC_LOOKUP_METHOD)) {
				Node ele = node;
				String methodName = ele.attrString(ATTR_NAME);
				String beanRef = ele.attrString(ATTR_BEAN);
				Map override = new HashMap<String, Object>(3);
				override.put(ATTR_NAME, methodName);
				override.put(ATTR_BEAN, beanRef);
				override.put(ATTR_OVERRIDE_TYPE, OVERRIDE_TYPE_LOOKUP_VALUE);
				overrides.add(override);
			}
		}
	}


	protected void parseReplacedMethodSubNodes(Node beanEle, Set<Map<String, Object>> overrides) {
		List<Node> nl = beanEle.getChilds();
		for (Node node : nl) {
			if (nodeNameEquals(node, DOC_REPLACED_METHOD)) {
				Node replacedMethodEle = node;
				String name = replacedMethodEle.attrString(ATTR_NAME);
				List<Node> nodes = replacedMethodEle.getChilds();
				List<Class<?>> types = new ArrayList<>();
				boolean anyArgType = false;
				for (int j = 0; j < nodes.size(); j++) {
					Node n = nodes.get(j);
					if (n.getName().equals(DOC_ARG_TYPE)) {
						String match = n.attrString(ATTR_MATCH);
						if (StringUtils.isEmpty(match)) {
							continue;
						} else if (PATTERN_ANY.equals(match.trim())) {
							anyArgType = true;
							break;
						} else {
							Class type = BeanUtils.forName(match, null);
							types.add(type);
						}
					}
				}
				String callback = replacedMethodEle.attrString(ATTR_REPLACER);
				Map override = new HashMap<String, Object>(1);
				override.put(ATTR_NAME, name);
				override.put(ATTR_REPLACER, callback);
				override.put(ATTR_OVERRIDE_TYPE, OVERRIDE_TYPE_REPLACE_VALUE);
				if (anyArgType) {
					override.put(DOC_ARG_TYPE, ANY_VALUE);
				} else {
					override.put(DOC_ARG_TYPE, types);
				}
				overrides.add(override);
			}
		}
	}


	protected void parseConstructorArgNode(Node ele, BeanDefinition bd) {
		String indexAttr = ele.attrString(ATTR_INDEX);
		String typeAttr = ele.attrString(ATTR_TYPE);
		String nameAttr = ele.attrString(ATTR_NAME);
		if (StringUtils.hasLength(indexAttr)) {
			try {
				int index = Integer.parseInt(indexAttr);
				if (index < 0) {
					throw new SimpleException("'index' cannot be lower than 0 of node " + ele + "!");
				} else {
					Map map = parsePropertyValue(ele, bd, null);
					if (StringUtils.hasLength(typeAttr)) {
						map.put(ATTR_TYPE, typeAttr);
					}
					if (StringUtils.hasLength(nameAttr)) {
						map.put(ATTR_NAME, nameAttr);
					}
					if (bd.getConstructorArgumentValues().containsKey(index)) {
						throw new SimpleException("Ambiguous constructor-arg entries for index " + index + " " + ele);
					} else {
						bd.getConstructorArgumentValues().put(index, map);
					}
				}
			} catch (NumberFormatException ex) {
				log.info("Attribute 'index' of tag 'constructor-arg' must be an integer" + ele);
			}
		} else {
			Map map = parsePropertyValue(ele, bd, null);
			if (StringUtils.hasLength(typeAttr)) {
				map.put(ATTR_TYPE, typeAttr);
			}
			if (StringUtils.hasLength(nameAttr)) {
				map.put(ATTR_NAME, nameAttr);
			}
			int index = ObjectUtils.generateId(bd.getConstructorArgumentValues());
			bd.getConstructorArgumentValues().put(index, map);
		}
	}


	protected void parsePropertyNode(Node ele, BeanDefinition bd) {
		String propertyName = ele.attrString(ATTR_NAME);
		if (!StringUtils.hasLength(propertyName)) {
			log.info("Tag 'property' must have a 'name' attribute " + ele);
			return;
		}
		Map map = parsePropertyValue(ele, bd, propertyName);
		String type = ele.attrString(ATTR_TYPE);
		if (StringUtils.hasLength(type)) {
			map.put(ATTR_TYPE, type);
		}
		map.put(ATTR_NAME, propertyName);
		parseMetaNodes(ele, map);
		bd.getPropertyValues().add(map);
	}



	protected Map parsePropertyValue(Node ele, BeanDefinition bd, String propertyName) {
		String NodeName = (propertyName != null ? "<property> Node for property '" + propertyName + "'"
				: "<constructor-arg> Node");

		// Should only have one child Node: ref, value, list, etc.
		List<Node> nl = ele.getChilds();
		Node subNode = null;
		for (Node node : nl) {
			if (!nodeNameEquals(node, DOC_DESCRIPTION) && !nodeNameEquals(node, DOC_META)) {
				// Child Node is what we're looking for.
				if (subNode != null) {
					log.info(NodeName + " must not contain more than one sub-Node " + ele);
				} else {
					subNode = node;
				}
			}
		}
		boolean hasRefAttribute = ele.hasAttr(ATTR_REF);
		boolean hasValueAttribute = ele.hasAttr(ATTR_VALUE);
		if ((hasRefAttribute && hasValueAttribute) || ((hasRefAttribute || hasValueAttribute) && subNode != null)) {
			log.info(NodeName + " is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-Node "
					+ ele);
		}

		if (hasRefAttribute) {
			String refName = ele.attrString(ATTR_REF);
			String refTypeName = ele.attrString(ATTR_REF_TYPE);
			if (!StringUtils.hasText(refName)) {
				log.info(NodeName + " contains empty 'ref' attribute " + ele);
			}
			Map ref = new HashMap<String, Object>(3);
			ref.put(ATTR_TYPE, TYPE_REF_VALUE);
			ref.put(ATTR_REF, refName);
			ref.put(ATTR_REF_TYPE, refTypeName);
			return ref;
		} else if (hasValueAttribute) {
			String value = ele.attrString(ATTR_VALUE);
			Map prop = new HashMap<String, Object>(2);
			prop.put(ATTR_VALUE, value);
			return prop;
		} else if (subNode != null) {
			return parsePropertySubNode(subNode, bd);
		} else {
			// Neither child Node nor "ref" or "value" attribute found.
			log.error(NodeName + " must specify a ref or value ");
			return null;
		}
	}

	protected Map parsePropertySubNode(Node ele, BeanDefinition bd) {
		return parsePropertySubNode(ele, bd, null);
	}



	protected Map parsePropertySubNode(Node ele, BeanDefinition bd, String defaultValueType) {
		if (nodeNameEquals(ele, DOC_BEAN)) {
			BeanDefinition nestedBd = parseBeanDefinitionNode(ele, bd);
			Map map = new HashMap<String, Object>(2);
			map.put(ATTR_TYPE, TYPE_BEAN_VALUE);
			map.put(ATTR_VALUE, nestedBd);
			return map;
		} else if (nodeNameEquals(ele, DOC_REF)) {
			// A generic reference to any name of any bean.
			String refName = ele.attrString(ATTR_BEAN);
			if (!StringUtils.hasLength(refName)) {
				// A reference to the id of another bean in a parent context.
				refName = ele.attrString(ATTR_PARENT);
				if (!StringUtils.hasLength(refName)) {
					log.info("'bean' or 'parent' is required for <ref> Node " + ele);
					return null;
				}
			}
			Map ref = new HashMap<>(3);
			ref.put(ATTR_REF, refName);
			ref.put(ATTR_TYPE, TYPE_REF_VALUE);
			return ref;
		} else if (nodeNameEquals(ele, DOC_IDREF)) {
			return parseIdRefNode(ele);
		} else if (nodeNameEquals(ele, DOC_VALUE)) {
			return parseValueNode(ele, defaultValueType);
		} else if (nodeNameEquals(ele, DOC_ARRAY)) {
			return parseArrayNode(ele, bd);
		} else if (nodeNameEquals(ele, DOC_LIST)) {
			return parseListNode(ele, bd);
		} else if (nodeNameEquals(ele, DOC_SET)) {
			return parseSetNode(ele, bd);
		} else if (nodeNameEquals(ele, DOC_MAP)) {
			return parseMapNode(ele, bd);
		} else if (nodeNameEquals(ele, DOC_PROPS)) {
			return parsePropsNode(ele, bd);
		} else {
			return parseNestedCustomNode(ele, bd);
		}
	}



	protected Map parseIdRefNode(Node ele) {
		// A generic reference to any name of any bean.
		String refName = ele.attrString(ATTR_BEAN);
		if (!StringUtils.hasLength(refName)) {
			log.info("'bean' is required for <idref> Node " + ele);
			return null;
		}
		if (!StringUtils.hasText(refName)) {
			log.info("<idref> Node contains empty target attribute " + ele);
			return null;
		}
		Map ref = new HashMap<>(2);
		ref.put(ATTR_NAME, refName);
		ref.put(ATTR_TYPE, TYPE_REF_VALUE);
		return ref;
	}


	protected Map parseValueNode(Node ele, String defaultTypeName) {
		// It's a literal value.
		String value = DomUtils.getTextValue(ele);
		if (!StringUtils.hasText(value)) {
			value = ele.attrString(ATTR_VALUE);
		}
		String specifiedTypeName = ele.attrString(ATTR_TYPE);
		String typeName = specifiedTypeName;
		if (!StringUtils.hasText(typeName)) {
			typeName = defaultTypeName;
		}
		Map map = new HashMap<>(2);
		map.put(ATTR_TYPE, specifiedTypeName);
		map.put(ATTR_VALUE, value);
		return map;
	}


	protected Map parseArrayNode(Node arrayEle, BeanDefinition bd) {
		String nodeType = arrayEle.attrString(ATTR_NODE_TYPE);
		List<Node> nl = arrayEle.getChilds();
		List target = new ArrayList<>(nl.size());
		Map map = new HashMap<>(4);
		map.put(ATTR_NODE_TYPE, nodeType);
		map.put(ATTR_MERGE, parseMergeAttribute(arrayEle));
		map.put(ATTR_TYPE, TYPE_ARRAY_VALUE);
		map.put(ATTR_VALUE, target);
		parseCollectionNodes(nl, target, bd, nodeType);
		return map;
	}


	protected Map parseListNode(Node collectionEle, BeanDefinition bd) {
		Map map = parseArrayNode(collectionEle, bd);
		map.put(ATTR_TYPE, TYPE_LIST_VALUE);
		return map;
	}


	protected Map parseSetNode(Node arrayEle, BeanDefinition bd) {
		Map map = parseArrayNode(arrayEle, bd);
		map.put(ATTR_TYPE, TYPE_SET_VALUE);
		return map;
	}

	protected void parseCollectionNodes(List<Node> nodes, Collection<Object> target, BeanDefinition bd,
			String defaultNodeType) {

		for (Node node : nodes) {
			if (!nodeNameEquals(node, DOC_DESCRIPTION)) {
				target.add(parsePropertySubNode(node, bd, defaultNodeType));
			}
		}
	}


	protected Map<String, Object> parseMapNode(Node mapEle, BeanDefinition bd) {
		Map<String, Object> result = parsePairs(mapEle, bd, DOC_ENTRY);
		result.put(ATTR_TYPE, TYPE_MAP_VALUE);
		return result;
	}

	protected Map<String, Object> parsePairs(Node ele, BeanDefinition bd, String unitName) {

		String defaultKeyType = ele.attrString(ATTR_KEY_TYPE);
		String defaultValueType = ele.attrString(ATTR_VALUE_TYPE);
		List<Node> entryEles = DomUtils.getChildElementsByTagName(ele, unitName);
		List<Map<String, Object>> defines = new ArrayList<>(entryEles.size());
		Map<String, Object> result = new HashMap<>(4);
		result.put(ATTR_KEY_TYPE, defaultKeyType);
		result.put(ATTR_VALUE_TYPE, defaultValueType);
		result.put(ATTR_MERGE, parseMergeAttribute(ele));
		result.put(ATTR_VALUE, defines);
		for (Node entryEle : entryEles) {
			// Should only have one value child Node: ref, value, list, etc.
			// Optionally, there might be a key child Node.
			Map<String, Object> map = new HashMap<>();
			List<Node> entrySubNodes = entryEle.getChilds();
			Node keyEle = null;
			Node valueEle = null;
			for (Node node : entrySubNodes) {
				Node candidateEle = node;
				if (nodeNameEquals(candidateEle, DOC_KEY)) {
					if (keyEle != null) {
						log.info(unitName + " Node is only allowed to contain one <key> sub-Node " + entryEle);
					} else {
						keyEle = candidateEle;
					}
				} else {
					// Child Node is what we're looking for.
					if (nodeNameEquals(candidateEle, DOC_DESCRIPTION)) {
						// the Node is a <description> -> ignore it
					} else if (valueEle != null) {
						log.info(unitName + " Node must not contain more than one value sub-Node " + entryEle);
					} else {
						valueEle = candidateEle;
					}
				}
			}

			// Extract key from attribute or sub-Node.
			Object key = null;
			boolean hasKeyAttribute = entryEle.hasAttr(ATTR_KEY);
			boolean hasKeyRefAttribute = entryEle.hasAttr(ATTR_KEY_REF);
			if ((hasKeyAttribute && hasKeyRefAttribute) || (hasKeyAttribute || hasKeyRefAttribute) && keyEle != null) {
				log.info(unitName + " Node is only allowed to contain either "
						+ "a 'key' attribute OR a 'key-ref' attribute OR a <key> sub-Node " + entryEle);
			}
			if (hasKeyAttribute) {
				key = buildTypedStringValueForMap(entryEle.attrString(ATTR_KEY), entryEle.attrString(ATTR_KEY_TYPE),
						defaultKeyType, entryEle);
			} else if (hasKeyRefAttribute) {
				String refName = entryEle.attrString(ATTR_KEY_REF);
				if (!StringUtils.hasText(refName)) {
					log.info(unitName + " Node contains empty 'key-ref' attribute " + entryEle);
				}
				Map ref = new HashMap<>(2);
				map.put(ATTR_VALUE, refName);
				map.put(ATTR_TYPE, TYPE_REF_VALUE);
				key = ref;
			} else if (keyEle != null) {
				key = parseKeyNode(keyEle, bd, defaultKeyType);
			} else {
				log.info(unitName + " Node must specify a key " + entryEle);
			}

			// Extract value from attribute or sub-Node.
			Object value = null;
			boolean hasValueAttribute = entryEle.hasAttr(ATTR_VALUE);
			boolean hasValueRefAttribute = entryEle.hasAttr(ATTR_VALUE_REF);
			boolean hasValueTypeAttribute = entryEle.hasAttr(ATTR_VALUE_TYPE);
			if ((hasValueAttribute && hasValueRefAttribute)
					|| (hasValueAttribute || hasValueRefAttribute) && valueEle != null) {
				log.info(unitName + " Node is only allowed to contain either "
						+ "'value' attribute OR 'value-ref' attribute OR <value> sub-Node " + entryEle);
			}
			if ((hasValueTypeAttribute && hasValueRefAttribute) || (hasValueTypeAttribute && !hasValueAttribute)
					|| (hasValueTypeAttribute && valueEle != null)) {
				log.info("conflict definition " + entryEle);
			}
			if (hasValueAttribute) {
				value = buildTypedStringValueForMap(entryEle.attrString(ATTR_VALUE),
						entryEle.attrString(ATTR_VALUE_TYPE), defaultValueType, entryEle);
			} else if (hasValueRefAttribute) {
				String refName = entryEle.attrString(ATTR_VALUE_REF);
				if (!StringUtils.hasText(refName)) {
					log.info(unitName + " Node contains empty 'value-ref' attribute " + entryEle);
				}
				Map ref = new HashMap<>();
				map.put(ATTR_NAME, refName);
				map.put(ATTR_TYPE, TYPE_REF_VALUE);
				value = ref;
			} else if (valueEle != null) {
				value = parsePropertySubNode(valueEle, bd, defaultValueType);
			} else {
				log.info(unitName + " Node must specify a value " + entryEle);
			}

			// Add final key and value to the Map.
			map.put(ATTR_KEY, key);
			map.put(ATTR_VALUE, value);
			defines.add(map);
		}

		return result;

	}


	protected final Object buildTypedStringValueForMap(String value, String typeName, String defaultTypeName,
			Node entryEle) {
		Map typedValue = new HashMap<>(2);
		if (StringUtils.isEmpty(typeName)) {
			typeName = defaultTypeName;
		}
		typedValue.put(ATTR_TYPE, typeName);
		typedValue.put(ATTR_VALUE, value);
		return typedValue;
	}



	protected Object parseKeyNode(Node keyEle, BeanDefinition bd, String defaultKeyTypeName) {
		List<Node> nl = keyEle.getChilds();
		Node subNode = null;
		for (Node node : nl) {
			// Child Node is what we're looking for.
			if (subNode != null) {
				log.info("<key> Node must not contain more than one value sub-Node " + keyEle);
			} else {
				subNode = node;
			}
		}
		if (subNode == null) {
			return null;
		}
		return parsePropertySubNode(subNode, bd, defaultKeyTypeName);
	}


	protected Map parsePropsNode(Node propsEle, BeanDefinition bd) {
		Map<String, Object> result = parsePairs(propsEle, bd, DOC_PROP);
		result.put(ATTR_TYPE, TYPE_PROPS_VALUE);
		return result;
	}


	protected boolean parseMergeAttribute(Node collectionNode) {
		String value = collectionNode.attrString(ATTR_MERGE);
		if (DEFAULT_VALUE.equals(value)) {
			String df = this.root.attrString(ATTR_DEFAULT_MERGE);
			if (StringUtils.isNotEmpty(df)) {
				value = df;
			} else {
				value = FALSE_VALUE;
			}
		}
		return TRUE_VALUE.equals(value);
	}

	protected Map parseCustomNode(Node ele) {
		return parseCustomNode(ele, null);
	}

	protected Map parseCustomNode(Node ele, BeanDefinition containingBd) {
		Object result = this.handler.parse(ele, containingBd, root);
		Map map = new HashMap<>(2);
		map.put(ATTR_TYPE, ele.attr(ATTR_TYPE));
		map.put(ATTR_VALUE, result);
		return map;
	}

	private Map parseNestedCustomNode(Node ele, BeanDefinition containingBd) {
		return parseCustomNode(ele, containingBd);
	}


	protected boolean nodeNameEquals(Node node, String desiredName) {
		return desiredName.equals(node.getName());
	}

	protected boolean isDefaultNamespace(Node node) {
		return node.getName().equals(Constant.DOC_BEAN) || node.getName().equals(Constant.DOC_BEANS)
				|| node.getName().equals(Constant.DOC_ROOT) || node.getName().equals(Constant.DOC_IMPORT)
				|| node.getName().equals(Constant.DOC_ALIAS);

	}

}
