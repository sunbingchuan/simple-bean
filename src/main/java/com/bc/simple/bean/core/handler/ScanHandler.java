package com.bc.simple.bean.core.handler;

import static com.bc.simple.bean.common.util.ResourceUtils.DEFAULT_RESOURCE_PATTERN;
import static com.bc.simple.bean.common.util.ResourceUtils.getResources;
import static com.bc.simple.bean.common.util.ResourceUtils.getUnPatternRootDir;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.annotation.Component;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.ResourceUtils;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.asm.ClassReader;
import com.bc.simple.bean.core.support.AnnotationMetaData;

@SuppressWarnings("unused")
public class ScanHandler implements Handler {

	private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";

	private static final String RESOURCE_PATTERN_ATTRIBUTE = "resource-pattern";

	private static final String USE_DEFAULT_FILTERS_ATTRIBUTE = "use-default-filters";

	private static final String ANNOTATION_CONFIG_ATTRIBUTE = "annotation-config";

	private static final String NAME_GENERATOR_ATTRIBUTE = "name-generator";

	private static final String SCOPE_RESOLVER_ATTRIBUTE = "scope-resolver";

	private static final String SCOPED_PROXY_ATTRIBUTE = "scoped-proxy";

	private static final String EXCLUDE_FILTER_ELEMENT = "exclude-filter";

	private static final String INCLUDE_FILTER_ELEMENT = "include-filter";

	private static final String FILTER_TYPE_ATTRIBUTE = "type";

	private static final String FILTER_EXPRESSION_ATTRIBUTE = "expression";

	private BeanFactory beanFactory;

	private Node root;

	public ScanHandler() {
	}


	@Override
	public BeanDefinition handle(Node element, BeanDefinition bd, Node root) {
		this.setRoot(root);
		List<BeanDefinition> beanDefinitions = this.doScan(element);
		this.postProcessBeanDefinition(beanDefinitions, element);
		beanDefinitions.forEach(beanDefinition -> {
			AnnotationUtils.processCommonDefinitionAnnotations(beanDefinition, beanDefinition.getMetadata());
			beanFactory.registerBeanDefinition(beanDefinition.getBeanName(), beanDefinition);
		});

		return bd;
	}

	protected List<BeanDefinition> doScan(Node element) {
		String basePackage = element.attrString(BASE_PACKAGE_ATTRIBUTE);
		basePackage = StringUtils.convertClassNameToResourcePath(basePackage);
		String packageSearchPath = basePackage + '/' + DEFAULT_RESOURCE_PATTERN;
		String unPatternRoot = getUnPatternRootDir(basePackage);
		List<Resource> dirs = getResources(unPatternRoot);
		List<BeanDefinition> beanDefinitions = new ArrayList<>();
		Set<File> resources = new HashSet<File>();
		for (Resource dir : dirs) {
			File rootDir = dir.getFile();
			String rootDirPath = rootDir.getPath();
			rootDirPath = rootDirPath.replace("\\", "/");
			String claspath = rootDirPath.substring(0, rootDirPath.lastIndexOf(unPatternRoot));
			ResourceUtils.analysisClasspathAndRetrieveMatchingFiles(claspath + packageSearchPath, rootDir, resources);
			for (File file : resources) {
				try (FileInputStream inputStream = new FileInputStream(file);) {
					ClassReader classReader = new ClassReader(inputStream);
					AnnotationMetaData annotationMetaData = new AnnotationMetaData();
					annotationMetaData.setResource(new Resource(file));
					classReader.accept(annotationMetaData, ClassReader.SKIP_DEBUG);
					if (annotationMetaData.isAnnotation()) {
						continue;
					}
					if (isComponent(annotationMetaData)) {
						BeanDefinition bdf = new BeanDefinition();
						bdf.setMetadata(annotationMetaData);
						bdf.setResource(annotationMetaData.getResource());
						bdf.setBeanClassName(annotationMetaData.getClassName());
						bdf.setBeanClass(BeanUtils.forName(annotationMetaData.getClassName(), null));
						String beanName = BeanUtils.generateAnnotatedBeanName(bdf, beanFactory);
						bdf.setBeanName(beanName);
						beanDefinitions.add(bdf);
					}
				} catch (Exception e) {
					e.printStackTrace();
					// ignore
				}
			}
		}
		return beanDefinitions;
	}

	private static final Map<String, Boolean> componentTypeCache = new HashMap<String, Boolean>();

	private boolean isComponent(AnnotationMetaData annotationMetaData) {
		for (String type : annotationMetaData.getAnnotationTypes()) {
			if (componentTypeCache.containsKey(type) && componentTypeCache.get(type)) {
				return true;
			} else {
				boolean isComponent = false;
				if (type.equals(Component.class.getCanonicalName())) {
					isComponent = true;
				} else {
					isComponent = AnnotationUtils.findAnnotation(BeanUtils.forName(type), Component.class) != null;
				}
				componentTypeCache.put(type, isComponent);
				if (isComponent) {
					return true;
				}
			}
		}
		return false;
	}

	public void postProcessBeanDefinition(List<BeanDefinition> beanDefinitions, Node element) {
		for (BeanDefinition beanDefinition : beanDefinitions) {
			String candidatePattern = this.root.attrString(Constant.ATTR_DEFAULT_AUTOWIRE_CANDIDATES);
			if (StringUtils.isNotEmpty(candidatePattern)) {
				String[] patterns = StringUtils.splitByStr(candidatePattern, StringUtils.COMMA);
				beanDefinition.setAutowireCandidate(StringUtils.match(patterns, beanDefinition.getBeanName()));
			}

		}
		AnnotationUtils.registerAnnotationConfigProcessors(this.root, beanFactory);
	}



	public Node getRoot() {
		return root;
	}

	public void setRoot(Node root) {
		this.root = root;
	}

	@Override
	public void setBeanFactory(BeanFactory factory) {
		this.beanFactory = factory;
	}


}
