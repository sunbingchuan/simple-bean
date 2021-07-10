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
package com.chuan.simple.bean.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.expression.ExpressionHelper;
import com.chuan.simple.helper.resource.Resource;
import com.chuan.simple.helper.resource.ResourceHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.builder.Manager;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.config.parser.SimpleParser;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.installer.ElementInstaller;
import com.chuan.simple.bean.core.handler.Handler;
import com.chuan.simple.bean.core.processor.Processor;
import com.chuan.simple.bean.exception.SimpleBuildException;

/**
 * <p>
 * Simple-bean is a simple bean manage architecture.
 * <p>
 * The following is primary structure:
 * 
 * <pre>
 * +-------------------------------------------------------+--------------------+
 * |                                                       |                    |
 * |                                                       |    simple-bean     |
 * |                                                       |      primary       |
 * |                            +----------------+         |     structure      |
 * |                            |                |         |                    |
 * |                            |     Loader     |         +--------------------+
 * |                            |                |                              |
 * |   +---------------+        +-------X--------+                              |
 * |   |               |                X                +                      |
 * |   |    Handler    |X X X X X X X X X                |                      |
 * |   |               |                X                |                      |
 * |   +---------------+                X                |   Helper             |
 * |                                    X                |                      |
 * |                                    X                |                      |
 * |                                    X                +----------------+     |
 * |   +---------------+        +----------------+                              |
 * |   |               |        |                |                              |
 * |   | Configuration +------> |     Parser     |                              |
 * |   |               |        |                |                              |
 * |   +---------------+        +-------+--------+       +----------------+     |
 * |                                    |                |                |     |
 * |                                    |                |    Element     |     |
 * |                                    |                |                |     |
 * |                                    |                +-------X--------+     |
 * |                                    |                        X              |
 * |   +---------------+        +-------v--------+               X              |
 * |   |               |        |                |               X              |
 * |   |    Builder     X X X X X    Context      X X X X X X X XX              |
 * |   |               |        |                |               X              |
 * |   +------X--------+        +-------+--------+               X              |
 * |          X                         |                        X              |
 * |   +------X--------+                |                +-------X---------+    |
 * |   |               |                |                |                 |    |
 * |   |   Procedure   |                |                |    Processor    |    |
 * |   |               |                |                |                 |    |
 * |   +---------------+        +-------v--------+       +-----------------+    |
 * |                            |                |                              |
 * |                            |      Bean      |                              |
 * |                            |                |                              |
 * |                            +----------------+                              |
 * |                                                                            |
 * |                                                                            |
 * +----------------------------------------------------------------------------+
 * </pre>
 * <p>
 * Simple-bean can be extended by defining custom {@link Handler} and/or
 * {@link Element} and/or {@link Processor}
 *
 * <p>
 * Context of Simple-bean.
 *
 * @author bingchuan
 *
 */
public class SimpleContext {

    private static final Log log = LogFactory.getLog(SimpleContext.class);

    protected final Map<String, Builder<?>> builderMap =
            new ConcurrentHashMap<>();

    protected final Map<String, String> aliasMap = new ConcurrentHashMap<>();

    protected final Map<String, String> baseAttributes =
            new ConcurrentHashMap<>();

    protected final Map<Class<? extends Element>, ElementInstaller<? extends Builder<?>, ? extends Element>> elementInstallers =
            new ConcurrentHashMap<>();

    protected final List<Processor> processors = new ArrayList<>();

    protected final Map<String, Object> singletonMap =
            new ConcurrentHashMap<>();

    protected final Map<String, Object> singletonMapOnConstruction =
            new ConcurrentHashMap<>();

    protected final List<Object> configs = new ArrayList<>();
    
    protected final Set<String> namesInUse = new HashSet<>();

    protected final SimpleParser parser = new SimpleParser(this);

    protected volatile Boolean onRefresh = false;

    public SimpleContext() {
    }

    public SimpleContext(String... configs) {
        addConfigs(configs);
        refresh();
    }

    protected void addConfigs(String... configs) {
        for (String config : configs) {
            this.configs.add(config);
        }
    }
    
    public void refresh() {
        if (onRefresh) {
            return;
        }
        synchronized (onRefresh) {
            if (!onRefresh) {
                onRefresh = true;

                clear(true);
                
                loadConfigs();
                
                processBuilders();
                
                autoInit();
                
                onRefresh = false;
            }
        }
    }

    protected void processBuilders() {
        Collection<Builder<?>> builders = getBuilders();
        for (Processor processor : processors) {
            processor.processBuilders(builders);
        }
    }

    protected void loadConfigs() {
        for (Object config : configs) {
            if (config instanceof String)
                for (Resource resource : ResourceHelper
                        .resources((String) config)) {
                    parser.parse(resource);
                }
            else if (config instanceof Node || config instanceof Resource)
                parser.parse(config);
        }
    }

    protected void autoInit() {
        for (Builder<?> builder : getBuilders()) {
            if (StringHelper.equals(Builder.SCOPE_SINGLETON, builder.getScope())
                    && builder.isAutoInit()) {
                builder.build();
            }
        }
    }

    public Object tryBuild(String builderName) {
        return build(builderName, false);
    }

    public Object build(String builderName) {
        return build(builderName, true);
    }

    public Object build(String builderName, boolean errorOnFailed) {
        String finalBuilderName = finalBuilderName(builderName);
        Builder<?> builder;
        if ((finalBuilderName == null
                || (builder = builderMap.get(finalBuilderName)) == null)) {
            if (errorOnFailed) {
                throw new SimpleBuildException(
                        "Couldn't find builder " + builderName);
            }else {
                log.debug("Couldn't find builder "+builderName);
                return null;
            }
        }
        return builder.build();
    }

    protected String finalBuilderName(String builderName) {
    	String targetBuilderName = builderName;
    	while (StringHelper.isNotEmpty(targetBuilderName 
    	        = aliasMap.get(builderName))) {
    		builderName = targetBuilderName;
        }
        return builderName;
    }

    public <T> T build(Class<T> clazz) {
        return build(clazz, true);
    }

    public <T> T tryBuild(Class<T> clazz) {
        return build(clazz, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> clazz, boolean errorOnFailed) {
        Builder<?> builder = getBuilder(clazz);
        if (builder != null) {
            return (T) builder.build();
        }
        if (errorOnFailed) {
            log.error("Couldn't find builder for " + clazz);
        } else {
            log.debug("Couldn't find builder for " + clazz);
        }
        return null;
    }

    public Map<String, Builder<?>> getBuilders(Class<?> clazz) {
        Map<String, Builder<?>> result = new HashMap<>();
        for (Entry<String, Builder<?>> entry : builderMap.entrySet()) {
            Builder<?> builder = entry.getValue();
            if (clazz.isAssignableFrom(builder.getBuilderClass())) {
                result.put(entry.getKey(), builder);
            }
        }
        return result;
    }

    public Collection<Builder<?>> getBuilders() {
        return this.builderMap.values();
    }

    public void addBuilder(String beanName, Builder<?> builder) {
        beanName = ExpressionHelper.resolvePlaceholders(beanName, baseAttributes);
        builderMap.put(beanName, builder);
    }

    public void registerBuilder(String beanName, Builder<?> builder) {
        beanName = ExpressionHelper.resolvePlaceholders(beanName, baseAttributes);
        if (!checkAndUseName(beanName)) {
            throw new SimpleBuildException("Alias "+beanName+" is already been used as builder name or alias");
        } else {
            builderMap.put(beanName, builder);
        }
    }

    public Builder<?> getBuilder(String builderName) {
        String finalBuilderName = finalBuilderName(builderName);
        return this.builderMap.get(finalBuilderName);
    }

    public Builder<?> getBuilder(Class<?> clazz) {
        Map<String, Builder<?>> builders = getBuilders(clazz);
        Builder<?> builder = null;
        for (Builder<?> bd : builders.values()) {
            if (builder == null
                    || (bd != null && bd.getOrder() > builder.getOrder())) {
                builder = bd;
            }
        }
        return builder;
    }
    
    public void removeBuilder(String builderName){
        this.builderMap.remove(builderName);
        Iterator<Map.Entry<String,String>> it =this.aliasMap.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<String,String> entry =it.next();
            if (StringHelper.equals(entry.getValue(),builderName)){
                it.remove();
            }
            namesInUse.remove(entry.getKey());
        }
        this.namesInUse.remove(builderName);
    }

    public void removeAlias(String alias){
        this.aliasMap.remove(alias);
        this.namesInUse.remove(alias);
    }

    
    public void registerAlias(String name, String alias) {
        name = ExpressionHelper.resolvePlaceholders(name, baseAttributes);
        alias = ExpressionHelper.resolvePlaceholders(alias, baseAttributes);
        if (!checkAndUseName(alias)){
            throw new SimpleBuildException("Alias "+alias+" is already been used as builder name or alias");
        }
        this.aliasMap.put(alias, name);
    }

    public void registerAliases(String name, Collection<String> aliases) {
        for (String alias : aliases) {
            registerAlias(name, alias);
        }
    }
    
    public boolean checkAndUseName(String name) {
        if (namesInUse.contains(name)){
            return false;
        }
        synchronized (namesInUse){
            if (namesInUse.contains(name)){
                return false;
            }
            namesInUse.add(name);
        }
        return true;
    }

    public void setAttribute(String name, String value) {
        this.baseAttributes.put(name, value);
    }

    public String getAttribute(String name) {
        return this.baseAttributes.get(name);
    }

    public Map<String, String> getAttributes() {
        return this.baseAttributes;
    }

    public void addProcessor(Processor processor) {
        this.processors.add(processor);
    }

    public List<Processor> getProcessors() {
        return processors;
    }


    public void addElementInstaller(Class<? extends Element> clazz,
            ElementInstaller<? extends Builder<?>, ? extends Element> installer) {
        this.elementInstallers.put(clazz, installer);
    }

    public ElementInstaller<? extends Builder<?>, ? extends Element>
            getElementInstaller(Class<? extends Element> clazz) {
        return this.elementInstallers.get(clazz);
    }

    public Map<Class<? extends Element>, ElementInstaller<? extends Builder<?>, ? extends Element>>
            getElementInstallers() {
        return this.elementInstallers;
    }

    public Map<String, Object> getSingletonMapOnConstruction() {
        return singletonMapOnConstruction;
    }

    public Map<String, Object> getSingletonMap() {
        return singletonMap;
    }

    public void addConfig(String... configs) {
        for (String config : configs) {
            this.configs.add(config);
        }
    }

    public void addConfig(Resource... configs) {
        for (Resource config : configs) {
            this.configs.add(config);
        }
    }

    public void addConfig(Node... configs) {
        for (Node config : configs) {
            this.configs.add(config);
        }
    }

    public void removeConfig(String config) {
        this.configs.remove(config);
    }

    public void removeConfig(Node config) {
        this.configs.remove(config);
    }

    public void clearConfig() {
        this.configs.clear();
    }
    
    public void destroy() {
        for (Object sigleton : singletonMap.values()) {
            if (sigleton instanceof Manager) {
                ((Manager) sigleton).destroy();
            }
        }
        clear();
    }
    
    protected void clear() {
        clear(false);
    }
    
    protected void clear(boolean isRefresh) {
        Map<String, Object> refreshableSingletonMap = null;
        if (isRefresh) {
            refreshableSingletonMap = refreshSingleton(); 
        }
        this.aliasMap.clear();
        this.singletonMap.clear();
        this.singletonMapOnConstruction.clear();
        this.builderMap.clear();
        this.namesInUse.clear();
        this.parser.clear();
        if (isRefresh) {
            this.singletonMap.putAll(refreshableSingletonMap);
        }
        
    }
    
    protected Map<String, Object> refreshSingleton() {
        Map<String, Object> refreshableSingletonMap = new HashMap<>();
        for (Entry<String, Object> entry : singletonMap.entrySet()) {
            Object sigleton = entry.getValue();
            if (sigleton instanceof Manager) {
                ((Manager) sigleton).refresh();
                refreshableSingletonMap.put(entry.getKey(), sigleton);
            }
        }
        return refreshableSingletonMap;
    }

    

}
