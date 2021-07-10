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
package com.chuan.simple.bean.core.build.builder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.bean.core.Privates;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.procedure.BuildProcedure;
import com.chuan.simple.bean.core.build.procedure.CompoundProcedure;
import com.chuan.simple.bean.core.build.procedure.CreateProcedure;
import com.chuan.simple.bean.core.build.procedure.ElementProcedure;
import com.chuan.simple.bean.core.build.procedure.PopulateProcedure;
import com.chuan.simple.bean.core.build.procedure.ProcedureDependant;
import com.chuan.simple.bean.core.build.procedure.ProxyProcedure;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.FieldElement;
import com.chuan.simple.bean.core.info.ClassInfo;

/**
 *
 * The class contains most of the bean definitions.
 *
 * @param <T>
 *            the type of bean which built by this builder
 */
public abstract class Builder<T> implements ProcedureDependant {

    /**
     * Scope for singleton mode.
     */
    public static final String SCOPE_SINGLETON = "singleton";
    /**
     * Scope for prototype mode.
     */
    public static final String SCOPE_PROTOTYPE = "prototype";

    protected final List<String> aliases = new ArrayList<>();
    
    /**
     * Allow get the singleton on construction or not.
     */
    protected boolean allowOnConstruction = true;

    /**
     * The class for the target bean which is built by this builder.
     */
    protected volatile Class<?> builderClass;

    protected volatile String className;

    /**
     * The name of this builder.
     */
    protected volatile String builderName;

    /**
     * The description of this builder(just notes).
     */
    protected String description;

    /**
     * Automatic build {@link Builder#SCOPE_SINGLETON} while refreshing the
     * context ({@link SimpleContext#refresh()}) or not.
     */
    protected boolean autoInit = false;

    /**
     * Automatic configure parameters for the executables while absent
     * parameters.
     */
    protected boolean autowiredExecutable = false;

    /**
     * Automatic configure fields which not assigned.
     */
    protected boolean autowiredField = false;


    protected String scope = SCOPE_SINGLETON;

    protected int order;

    /**
     * Store the bean which instanceof {@link Manager} when
     * {@link Builder#scope} is {@link #SCOPE_PROTOTYPE} and
     * {@link Builder#isManagePrototype()} is true.
     */
    protected final Set<Manager> managers =
            Collections.synchronizedSet(new HashSet<>());

    protected boolean managePrototype = false;

    protected final List<Element> elements = new ArrayList<>();

    protected final List<Element> parsedElements = new ArrayList<>();

    protected Map<Executable, InvocationHandler> aspects =
            new ConcurrentHashMap<>();

    protected InvocationHandler handler;
    /**
     * The beans should be built before the building of this builder.
     */
    protected String[] dependsOn;

    protected final SimpleContext context;

    protected ClassInfo classInfo;

    protected Executable buildExecutable;

    protected final List<Element> buildParameters = new ArrayList<>();

    protected Class<?>[] buildParameterTypes;

    protected final Map<Executable, List<Element>> executableParameters =
            new ConcurrentHashMap<>();

    protected final Map<String, FieldElement> fields =
            new ConcurrentHashMap<>();

    protected Map<Class<?>, BuildProcedure> procedures = new HashMap<>();;

    protected CreateProcedure createProcedure;

    protected Builder(SimpleContext context) {
        this.context = context;
        initializeProcedure();
    }

    @Override
    public void initializeProcedure() {
        this.createProcedure = new CreateProcedure(this);
        new PopulateProcedure(this);
        new CompoundProcedure(this);
        new ProxyProcedure(this);
        new ElementProcedure(this);
    }

    @SuppressWarnings("unchecked")
    public T build() {
        return (T) createProcedure.create();
    }

    public void addProcedure(BuildProcedure... procedures) {
        for (BuildProcedure procedure : procedures) {
            this.procedures.put(procedure.getClass(), procedure);
        }
    }

    @SuppressWarnings({ "unchecked" })
    public <P extends BuildProcedure> P getProcedure(Class<P> clazz) {
        return (P) this.procedures.get(clazz);
    }

    /**
     * Extends original {@link BuildProcedure} {@code parentClass} by new
     * {@code procedure}
     */
    public void extendsProcedure(Class<? extends BuildProcedure> parentClass,
            BuildProcedure procedure) {
        this.procedures.put(parentClass, procedure);

    }
    
    public void refresh() {
        this.buildParameters.clear();
        this.executableParameters.clear();
        this.managers.clear();
        this.createProcedure.clear();
                this.context.getSingletonMap().remove(this.builderName);
    }
    
    public void clearElements(Class<? extends Element> elementType) {
        Iterator<Element> it = elements.iterator();
        while (it.hasNext()) {
            Element e = it.next();
            if (elementType.isInstance(e)) {
                it.remove();
            }
        }
    }
    
    public Class<?> getBuilderClass() {
        return builderClass;
    }

    public void setBuilderClass(Class<?> builderClass) {
        this.builderClass = builderClass;
    }

    public String getBuilderName() {
        return builderName;
    }

    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void addAliases(Collection<String> aliases) {
        this.aliases.addAll(aliases);
    }

    public void addAlias(String alias) {
        this.aliases.add(alias);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String[] getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String[] dependsOn) {
        this.dependsOn = dependsOn;
    }

    public boolean isAutowiredExecutable() {
        return autowiredExecutable;
    }

    public void setAutowiredExecutable(boolean autowiredExecutable) {
        this.autowiredExecutable = autowiredExecutable;
    }

    public boolean isAutowiredField() {
        return autowiredField;
    }

    public void setAutowiredField(boolean autowiredField) {
        this.autowiredField = autowiredField;
    }

    public Set<Manager> getManagers() {
        return managers;
    }

    public List<Element> getElements() {
        return elements;
    }

    public void addElement(Element... element) {
        for (Element e : element) {
            this.elements.add(e);
        }
    }

    public SimpleContext getContext() {
        return context;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Map<Executable, InvocationHandler> getAspects() {
        return aspects;
    }

    public void aspect(Executable executable, InvocationHandler handler) {
        this.aspects.put(executable, handler);
    }

    public void aspectMethod(String methodName, InvocationHandler handler,
            Class<?> paramTypes) {
        Method method = MethodHelper.findMethod(this.builderClass, methodName,
                paramTypes);
        this.aspects.put(method, handler);
    }

    public void aspectConstructor(InvocationHandler handler,
            Class<?> paramTypes) {
        Constructor<?> ctor =
                MethodHelper.findConstructor(this.builderClass, paramTypes);
        this.aspects.put(ctor, handler);
    }

    public InvocationHandler getHandler() {
        return handler;
    }

    public void setHandler(InvocationHandler handler) {
        this.handler = handler;
    }

    public ClassInfo getClassInfo() {
        return classInfo;
    }

    public void setClassInfo(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public Executable getBuildExecutable() {
        return buildExecutable;
    }

    public void setBuildExecutable(Executable executable) {
        this.buildExecutable = executable;
    }

    public void addBuildParameter(Element param) {
        this.buildParameters.add(param);
    }

    public List<Element> getBuildParameters() {
        return this.buildParameters;
    }

    public Map<Executable, List<Element>> getExecutableParameters() {
        return executableParameters;
    }

    public void setExecutableParameters(Executable executable,
            List<Element> params) {
        this.executableParameters.put(executable, params);
    }

    public void setField(String fieldName, FieldElement value) {
        this.fields.put(fieldName, value);
    }

    public Map<String, FieldElement> getFields() {
        return this.fields;
    }

    public boolean isAllowOnConstruction() {
        return allowOnConstruction;
    }

    public void setAllowOnConstruction(boolean allowOnConstruction) {
        this.allowOnConstruction = allowOnConstruction;
    }

    public boolean isAutoInit() {
        return autoInit;
    }

    public void setAutoInit(boolean autoInit) {
        this.autoInit = autoInit;
    }

    public Class<?>[] getBuildParameterTypes() {
        return buildParameterTypes;
    }

    public void setBuildParameterTypes(Class<?>[] buildParameterTypes) {
        this.buildParameterTypes = buildParameterTypes;
    }


    public boolean isManagePrototype() {
        return managePrototype;
    }

    public void setManagePrototype(boolean managePrototype) {
        this.managePrototype = managePrototype;
    }

    public boolean isSingleton() {
        return SCOPE_SINGLETON.equals(scope);
    }


    public BuilderPrivates privates = new BuilderPrivates();

    public class BuilderPrivates implements Privates{
        public List<Element> getParsedElements() {
            return parsedElements;
        }
    }

}
