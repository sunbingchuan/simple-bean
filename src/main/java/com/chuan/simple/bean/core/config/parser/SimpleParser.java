/*
 * Copyright 2018-2021 Bingchuan Sun.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chuan.simple.bean.core.config.parser;


import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.chuan.simple.helper.clazz.BuilderNameHelper;
import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.resource.PathHelper;
import com.chuan.simple.helper.resource.Resource;
import com.chuan.simple.helper.resource.ResourceHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.builder.ConstructorBuilder;
import com.chuan.simple.bean.core.build.builder.MethodBuilder;
import com.chuan.simple.bean.core.config.loader.SimpleLoader;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.config.node.NodeWrapper;
import com.chuan.simple.bean.core.element.entity.BuildParameterElement;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.FieldElement;
import com.chuan.simple.bean.core.element.entity.MethodParameterElement;
import com.chuan.simple.bean.core.element.installer.BuildParameterInstaller;
import com.chuan.simple.bean.core.element.installer.FieldInstaller;
import com.chuan.simple.bean.core.element.installer.MethodParameterInstaller;
import com.chuan.simple.bean.core.handler.HandlerManager;
import com.chuan.simple.bean.exception.SimpleParseException;
import com.chuan.simple.constant.Constant;

/**
 * The common configuration parser.
 */
public class SimpleParser implements Parser<Object> {

    private final Set<String> usedNames = new HashSet<>();

    private final SimpleContext context;

    private final SimpleLoader loader;

    private final HandlerManager handlerManager;
    
    private final Set<Resource> parsed = Collections.synchronizedSet(new HashSet<>());

    public SimpleParser(SimpleContext context) {
        this.context = context;
        this.loader = new SimpleLoader();
        handlerManager = new HandlerManager(this.context);
        addDefaultElementInstaller();
    }

    private void addDefaultElementInstaller() {
        this.context.addElementInstaller(BuildParameterElement.class,
                new BuildParameterInstaller());
        this.context.addElementInstaller(FieldElement.class,
                new FieldInstaller());
        this.context.addElementInstaller(MethodParameterElement.class,
                new MethodParameterInstaller());
    }

    @Override
    public void parse(Object resource) {
        if (resource instanceof Resource) {
            if (parsed.add((Resource) resource)) {
                parseNode(loader.load((Resource) resource));
            }
        } else if (resource instanceof Node) {
            parseNode((Node) resource, null);
        }
    }

    protected void parseNode(NodeWrapper cfg) {
        parseNode(cfg.getNode(), cfg.getResource());
    }

    protected void parseNode(Node node, Resource resource) {
        this.context.getAttributes().putAll(node.attrStrings());
        for (Node child : node.getChilds()) {
            switch (child.getName()) {
            case Constant.DOC_BUILDER:
                parseBuilderNode(child);
                break;
            case Constant.DOC_ALIAS:
                processAliasNode(child);
                break;
            case Constant.DOC_IMPORT:
                importBeanDefinitionResource(child, resource);
                break;
            default:
                parseCustomNode(child);
                break;
            }
        }
    }

    protected Builder<?> parseBuilderNode(Node cfg) {
        String className = cfg.attrString(Constant.ATTR_CLASS).trim();
        Builder<?> builder = createBeanBuilder(cfg, className);
        generateBuilderName(cfg, builder, className);
        parseBuilderAttributes(cfg, builder);
        parseBuilderChilds(cfg, builder);
        this.context.addBuilder(builder.getBuilderName(), builder);
        return builder;
    }

    protected void parseBuilderChilds(Node cfg, Builder<?> builder) {
        List<Node> nl = cfg.getChilds();
        for (Node node : nl) {
            switch (node.getName()) {
            case Constant.DOC_BUILD_PARAMETER:
                parseBuildParameterNode(node, builder);
                break;
            case Constant.DOC_FIELD:
                parseFieldNode(node, builder);
                break;
            case Constant.DOC_EXECUTABLE_PARAMETER:
                parseExecutableParameterNode(node, builder);
                break;
            default:
                break;
            }
        }
    }

    private void generateBuilderName(Node cfg, Builder<?> builder,
            String className) {
        String name = cfg.attrString(Constant.ATTR_NAME);
        String aliasStr = cfg.attrString(Constant.ATTR_ALIAS);
        List<String> aliases = new ArrayList<>();
        if (StringHelper.hasLength(aliasStr)) {
            String[] aliasArr = StringHelper.splitByDelimiters(aliasStr,
                    Constant.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
            aliases.addAll(Arrays.asList(aliasArr));
        }
        String builderName = name;
        if (!StringHelper.hasText(builderName) && !aliases.isEmpty()) {
            builderName = aliases.remove(0);
        }
        if (!StringHelper.hasText(builderName)) {
            builderName = BuilderNameHelper.generateBuilderName(className);
            builderName=BuilderNameHelper.satisfiedName(builderName, context::checkAndUseName);
        }
        builder.addAliases(aliases);
        this.context.registerAliases(builderName, aliases);
        builder.setBuilderName(builderName);
    }


    private void parseBuilderAttributes(Node cfg, Builder<?> builder) {
        String scope = cfg.attrString(Constant.ATTR_SCOPE);
        if (!StringHelper.isEmpty(scope)) {
            builder.setScope(scope);
        }
        String autoInit = cfg.attrString(Constant.ATTR_AUTO_INIT);
        if (StringHelper.isEmpty(autoInit)) {
            autoInit = this.context.getAttribute(Constant.ATTR_AUTO_INIT);
        }
        builder.setAutoInit(Constant.TRUE_VALUE.equals(autoInit));
        if (cfg.hasAttr(Constant.ATTR_DEPENDS_ON)) {
            String dependsOn = cfg.attrString(Constant.ATTR_DEPENDS_ON);
            builder.setDependsOn(StringHelper.splitByDelimiter(dependsOn,
                    StringHelper.COMMA));
        }
        String autowiredField = cfg.attrString(Constant.ATTR_AUTOWIRED_FIELD);
        if (StringHelper.isEmpty(autowiredField)) {
            builder.setAutowiredField(matchBuilderName(
                    Constant.ATTR_DEFAULT_AUTOWIRED_FIELDS, builder.getBuilderName()));
        } else {
            builder.setAutowiredField(Constant.TRUE_VALUE.equals(autowiredField));
        }
        String autowiredExecutable = cfg.attrString(Constant.ATTR_AUTOWIRED_EXECUTABLE);
        if (StringHelper.isEmpty(autowiredExecutable)) {
            builder.setAutowiredExecutable(
                    matchBuilderName(Constant.ATTR_DEFAULT_AUTOWIRED_EXECUTABLES,
                            builder.getBuilderName()));
        } else {
            builder.setAutowiredExecutable(
                    Constant.TRUE_VALUE.equals(autowiredExecutable));
        }
        Integer order = StringHelper.switchInteger(cfg.attr(Constant.ATTR_ORDER));
        if (order != null) {
            builder.setOrder(order);
        }
        builder.setDescription(cfg.attrString(Constant.DOC_DESCRIPTION));
    }

    private boolean matchBuilderName(String matchAttrName, String builderName) {
        String patternStr = this.context.getAttribute(matchAttrName);
        if (patternStr != null) {
            String[] patterns = StringHelper.splitByDelimiter(patternStr,
                    StringHelper.COMMA);
            return StringHelper.match(patterns, builderName);
        }
        return false;
    }

    protected Builder<?> createBeanBuilder(Node cfg, String className) {
        Builder<?> builder;
        if (cfg.hasAttr(Constant.ATTR_METHOD_NAME)) {
            MethodBuilder<?> methodBuilder = new MethodBuilder<>(this.context);
            if (cfg.hasAttr(Constant.ATTR_OWNER_NAME)) {
                methodBuilder.setOwnerName(cfg.attrString(Constant.ATTR_OWNER_NAME));
            }
            if (cfg.hasAttr(Constant.ATTR_OWNER_CLASS_NAME)) {
                methodBuilder.setOwnerClassName(
                        cfg.attrString(Constant.ATTR_OWNER_CLASS_NAME));
            }
            methodBuilder.setMethodName(cfg.attrString(Constant.ATTR_METHOD_NAME));
            builder = methodBuilder;
        } else {
            builder = new ConstructorBuilder<>(this.context);
        }
        String parameterTypeStr = cfg.attrString(Constant.ATTR_BUILD_PARAMETER_TYPES);
        if (StringHelper.isNotEmpty(parameterTypeStr)) {
            Class<?>[] parameterTypes = parseParameterTypes(parameterTypeStr);
            builder.setBuildParameterTypes(parameterTypes);
        }
        builder.setBuilderClass(ClassHelper.forName(className));
        builder.setClassName(className);
        return builder;
    }

    protected Class<?>[] parseParameterTypes(String parameterTypeStr) {
        String[] typeStrs = StringHelper.splitByDelimiter(parameterTypeStr,
                StringHelper.COMMA);
        return ClassHelper.forName(typeStrs);
    }

    protected void parseBuildParameterNode(Node cfg, Builder<?> bd) {
        BuildParameterElement element =
                new BuildParameterElement(bd.getClassName());
        Integer index = StringHelper.switchInteger(cfg.attrString(Constant.ATTR_INDEX));
        if (index != null) {
            element.setParameterIndex(index);
        }
        parseBaseConfig(cfg, element);
        bd.addElement(element);
    }

    protected void parseExecutableParameterNode(Node cfg, Builder<?> bd) {
        MethodParameterElement element =
                new MethodParameterElement(bd.getClassName(), null, null);
        Integer index = StringHelper.switchInteger(cfg.attrString(Constant.ATTR_INDEX));
        if (index != null) {
            element.setParameterIndex(index);
        }
        String executableName = cfg.attrString(Constant.ATTR_EXECUTABLE_NAME);
        element.setDeclaringExecutableName(executableName);
        parseBaseConfig(cfg, element);
        bd.addElement(element);
    }

    protected void parseBaseConfig(Node cfg, Element element) {
        String type = cfg.attrString(Constant.ATTR_TYPE);
        if (StringHelper.isNotEmpty(type)) {
            element.setTypeName(type);
        }
        String name = cfg.attrString(Constant.ATTR_NAME);
        if (StringHelper.isNotEmpty(name)) {
            element.setName(name);
            element.setBuilderName(name);
        }
        String builderName = cfg.attrString(Constant.ATTR_REF);
        if (StringHelper.isNotEmpty(builderName)) {
            element.setBuilderName(builderName);
        }
        String val = cfg.attrString(Constant.ATTR_VAL);
        if (StringHelper.isNotEmpty(val)) {
            element.setValue(val);
        }
        parseMutiType(cfg, element);
    }

    protected void parseMutiType(Node cfg, Element element) {
        for (Node node : cfg.getChilds()) {
            switch (node.getName()) {
            case Constant.DOC_ARRAY:
                parseArrayNode(node, element);
                break;
            case Constant.DOC_LIST:
                parseListNode(node, element);
                break;
            case Constant.DOC_SET:
                parseSetNode(node, element);
                break;
            case Constant.DOC_MAP:
                parseMapNode(node, element);
                break;
            case Constant.DOC_PROP:
                parsePropNode(node, element);
                break;
            default:
                break;
            }
        }
    }

    protected void parseArrayNode(Node cfg, Element element) {
        List<Element> list = parseListNode(cfg, element);
        element.setValue(list.toArray(new Element[0]));
    }

    protected void parseSetNode(Node cfg, Element element) {
        Set<Element> set = new HashSet<>();
        for (Node node : cfg.getChilds()) {
            addEle(node,set);
        }
        element.setValue(set);
    }

    protected List<Element> parseListNode(Node cfg, Element element) {
        List<Element> list = new ArrayList<>();
        for (Node node : cfg.getChilds()) {
            addEle(node,list);
        }
        element.setValue(list);
        return list;
    }

    protected void addEle(Node node, Collection<Element> collection){
        if (Constant.DOC_ELE.equals(node.getName())) {
            Element e = new Element();
            parseBaseConfig(node, e);
            collection.add(e);
        }
    }

    protected void parseMapNode(Node cfg, Element element) {
        Map<Object, Object> map = new HashMap<>();
        for (Node node : cfg.getChilds()) {
            if (Constant.DOC_PAIR.equals(node.getName())) {
                parsePairNode(node, map);
            }

        }
        element.setValue(map);
    }

    protected void parsePropNode(Node cfg, Element element) {
        Properties prop = new Properties();
        for (Node node : cfg.getChilds()) {
            if (Constant.DOC_PAIR.equals(node.getName())) {
                parsePairNode(cfg, prop);
            }

        }
        element.setValue(prop);
    }

    protected void parsePairNode(Node cfg, Map<Object, Object> pairs) {
        Element key = null;
        Element value = null;
        for (Node node : cfg.getChilds()) {
            switch (node.getName()) {
            case Constant.DOC_PAIR_KEY:
                key = new Element();
                parseBaseConfig(node, key);
                break;
            case Constant.DOC_PAIR_VALUE:
                value = new Element();
                parseBaseConfig(node, value);
                break;
            default:
                break;
            }
        }
        if (key != null) {
            pairs.put(key, value);
        }
    }

    protected void parseFieldNode(Node cfg, Builder<?> bd) {
        FieldElement fieldElement = new FieldElement(bd.getClassName(), null);
        parseBaseConfig(cfg, fieldElement);
        bd.addElement(fieldElement);
    }

    protected void processAliasNode(Node ele) {
        String name = ele.attrString(Constant.ATTR_NAME);
        String alias = ele.attrString(Constant.ATTR_ALIAS);
        if (StringHelper.hasText(name) && StringHelper.hasText(alias)) {
            List<String> aliases =
                    Arrays.asList(StringHelper.splitByDelimiters(alias,
                            Constant.MULTI_VALUE_ATTRIBUTE_DELIMITERS));
            this.context.registerAliases(name, aliases);
        }
    }

    protected void importBeanDefinitionResource(Node node, Resource resource) {
        String path = node.attrString(Constant.ATTR_RESOURCE);
        if (!StringHelper.hasText(path)) {
            return;
        }
        Set<Resource> resources = new HashSet<>();
        if (path.startsWith(PathHelper.CURRENT_PATH)) {
            if (resource == null) {
                throw new SimpleParseException(
                        "Couldn't resolve relative path without source resource");
            }
            path = PathHelper.relativePath(resource.getPath(), path);
        }
        if (!addUrlResource(path, resources)
                && !addAbsoluteResource(path, resources)) {
            resources.addAll(ResourceHelper.resources(path));
        }
        for (Resource r : resources) {
            parse(r);
        }
    }

    protected boolean addUrlResource(String path, Set<Resource> resources) {
        if (!PathHelper.isURL(path)) {
            return false;
        }
        try {
            URL url = new URL(path);
            resources.add(new Resource(url));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean addAbsoluteResource(String path,
            Set<Resource> resources) {
        if (PathHelper.isAbsolute(path)) {
            resources.add(new Resource(path));
            return true;
        }
        return false;
    }

    protected void parseCustomNode(Node cfg) {
        this.handlerManager.parse(cfg);
    }

    public void clear() {
        this.parsed.clear();
        this.usedNames.clear();
    }
}
