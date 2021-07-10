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
package com.chuan.simple.bean.core.build.procedure;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.expression.ExpressionHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.helper.method.ParameterHelper;
import com.chuan.simple.bean.annotation.Autowired;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.build.builder.ConstructorBuilder;
import com.chuan.simple.bean.core.build.builder.Manager;
import com.chuan.simple.bean.core.build.builder.MethodBuilder;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.installer.ElementInstaller;
import com.chuan.simple.bean.core.processor.Processor;
import com.chuan.simple.bean.exception.SimpleCreateException;

public class CreateProcedure extends BuildProcedure
        implements ProcedureDependant {

    private static final Log log = LogFactory.getLog(CreateProcedure.class);

    private CompoundProcedure compoundProcedure;

    private PopulateProcedure populateProcedure;

    private ProxyProcedure proxyProcedure;

    private ElementProcedure elementProcedure;

    public CreateProcedure(Builder<?> builder) {
        super(builder);
    }

    public Object create() {
        initializeProcedure();
        Object bean = null;
        if ((bean = getSingleton()) == null)
            try {

                bean = doCreate();

                processAfterInstantiation(bean);

                bean = populateProcedure.populate(bean);

                processAfterInitialization(bean);

            } catch (Exception e) {
                
                clearExceptionState();
                
                throw new SimpleCreateException("Create bean error", e);
            }
        return bean;
    }

    protected Object getSingleton() {
        Object bean = null;
        if (builder.isSingleton()) {
            SimpleContext context = builder.getContext();
            String builderName = builder.getBuilderName();
            Map<String, Object> singletonMap = context.getSingletonMap();
            Map<String, Object> singletonMapOnConstruction =
                    context.getSingletonMapOnConstruction();
            bean = singletonMap.get(builderName);
            if (bean == null) {
                bean = getSingletonOnConstruction(singletonMapOnConstruction);
            }
        }
        return bean;
    }
    
    protected Object doCreate() {
        putSingletonOnConstruction(ObjectHelper.EMPTY);
        resolvePlaceholder();
        buildDependsOn();
        elementProcedure.clearElementParsedValue();
        elementProcedure.installElement(ElementInstaller.PERIOD_BEFORE_CREATE);
        Object result = proxyProcedure.optionalProxy();
        if (result == null)
            if (builder instanceof ConstructorBuilder<?>) {
                List<Object> paramValues = setBuildConstructor();
                ConstructorBuilder<?> constructorBuilder =
                        (ConstructorBuilder<?>) builder;
                Constructor<?> constructor = (Constructor<?>) constructorBuilder
                        .getBuildExecutable();
                result = MethodHelper.invoke(constructor,
                        paramValues.toArray());
            } else if (builder instanceof MethodBuilder<?>) {
                MethodBuilder<?> methodBuilder = (MethodBuilder<?>) builder;
                setOwner(methodBuilder);
                List<Object> paramValues = setBuildMethod();
                Method method = (Method) methodBuilder.getBuildExecutable();
            result = MethodHelper.invoke(methodBuilder.getOwner(), method,paramValues.toArray());
        }
        if (result==null){
            throw new SimpleCreateException("Couldn't create bean "+builder.getBuilderName());
        }
        elementProcedure.installElement(ElementInstaller.PERIOD_AFTER_CREATE);
        putSingletonOnConstruction(result);
        return result;
    }

    protected void putSingletonOnConstruction(Object value) {
        if (builder.isSingleton() && value != null) {
            builder.getContext().getSingletonMapOnConstruction()
                    .put(builder.getBuilderName(), value);
        }
    }

    protected void putSingleton(Object value) {
        if (builder.isSingleton()&&value!=null) {
            builder.getContext().getSingletonMap().put(builder.getBuilderName(), value);
        }
        if (!builder.isSingleton()
                &&(value instanceof Manager)
                &&builder.isManagePrototype()){
            builder.getManagers().add((Manager)value);
        }
        removeSingletonOnConstruction();
    }

    protected void removeSingletonOnConstruction() {
        builder.getContext().getSingletonMapOnConstruction().remove(builder.getBuilderName());
    }
    
    protected void clearExceptionState() {
        removeSingletonOnConstruction();
    }
    
    protected void processAfterInstantiation(Object bean) {
        for (Processor processor : builder.getContext().getProcessors()) {
            processor.processAfterInstantiation(bean, builder);
        }
    }

    protected void processAfterInitialization(Object bean) {
        for (Processor processor : builder.getContext().getProcessors()) {
            processor.processAfterInitialization(bean, builder);
        }
        if (bean instanceof Manager) {
            ((Manager) bean).Initialize();
        }

        putSingleton(bean);
    }

    @Override
    public void initializeProcedure() {
        if (this.compoundProcedure == null) {
            synchronized (this) {
                if (this.compoundProcedure == null) {
                    this.compoundProcedure =
                            builder.getProcedure(CompoundProcedure.class);
                }
            }
        }
        if (this.populateProcedure == null) {
            synchronized (this) {
                if (this.populateProcedure == null) {
                    this.populateProcedure =
                            builder.getProcedure(PopulateProcedure.class);
                }
            }
        }
        if (this.proxyProcedure == null) {
            synchronized (this) {
                if (this.proxyProcedure == null) {
                    this.proxyProcedure =
                            builder.getProcedure(ProxyProcedure.class);
                }
            }
        }
        if (this.elementProcedure == null) {
            synchronized (this) {
                if (this.elementProcedure == null) {
                    this.elementProcedure =
                            builder.getProcedure(ElementProcedure.class);
                }
            }
        }
    }

    protected Object getSingletonOnConstruction(
            Map<String, Object> singletonMapOnConstruction) {
        Object bean = singletonMapOnConstruction.get(builder.getBuilderName());
        if (bean != null) {
            if (ObjectHelper.EMPTY.equals(bean)
                    || !builder.isAllowOnConstruction()) {
                throw new SimpleCreateException("Bean "
                        + builder.getBuilderName() + " is on construction");
            }
        }
        return bean;
    }

    private final Object setOwnerLock = new Object();

    protected void setOwner(MethodBuilder<?> methodBuilder) {
        if (methodBuilder.getOwner() == null) {
            synchronized (setOwnerLock) {
                if (methodBuilder.getOwner() == null) {
                    String ownerName = methodBuilder.getOwnerName();
                    Object owner = null;
                    if (StringHelper.isNotEmpty(ownerName)) {
                        owner = methodBuilder.getContext().build(ownerName);
                    } else {
                        owner = methodBuilder.getContext()
                                .build(methodBuilder.getOwnerClass());
                    }
                    methodBuilder.setOwner(owner);
                    methodBuilder.setOwnerClass(owner.getClass());
                }
            }
        }
    }

    protected List<Object> setBuildMethod() {
        MethodBuilder<?> methodBuilder = (MethodBuilder<?>) builder;
        Executable executable = builder.getBuildExecutable();
        Class<?> ownerClass = methodBuilder.getOwnerClass();
        String methodName = methodBuilder.getMethodName();
        Class<?>[] buildParameterTypes = builder.getBuildParameterTypes();
        if (executable == null && buildParameterTypes != null) {
            executable = MethodHelper.findMethod(ownerClass, methodName,
                    buildParameterTypes);
            if (executable == null) {
                log.error("Couldn't find method of " + ownerClass
                        + " with parameter types "
                        + Arrays.asList(buildParameterTypes));
            }
        }
        List<Object> paramValues = null;
        boolean isCompound = false;
        List<Element> params = builder.getBuildParameters();
        paramValues = elementProcedure.getElementParsedValues(params);
        if (executable == null) {
            List<Method> buildMethods = getMethods(ownerClass, methodName);
            for (Method buildMethod : buildMethods) {
                if (tryCompoundParameters(buildMethod, paramValues)) {
                    executable = buildMethod;
                    isCompound = true;
                    break;
                }
            }
        }
        if (executable == null) {
            throw new SimpleCreateException("Set instance executable failed");
        }
        builder.setBuildExecutable(executable);
        if (executable.getParameterCount() > 0 && !isCompound) {
            compoundProcedure.compoundParameters(builder.getBuildExecutable(),
                    paramValues);
        }
        return paramValues;
    }

    protected List<Object> setBuildConstructor() {
        Executable executable = builder.getBuildExecutable();
        if (executable == null && builder.getBuildParameterTypes() != null) {
            executable = MethodHelper.findConstructor(builder.getBuilderClass(),
                    builder.getBuildParameterTypes());
            if (executable == null) {
                log.error("Couldn't find constructor of "
                        + builder.getBuilderClass() + " with parameter types "
                        + ObjectHelper.toString(builder.getBuildParameterTypes()));
            }
        }
        List<Object> paramValues = null;
        boolean isCompound = false;
        List<Element> params = builder.getBuildParameters();
        paramValues = elementProcedure.getElementParsedValues(params);
        if (executable == null) {
            Constructor<?>[] constructors = getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (tryCompoundParameters(constructor, paramValues)) {
                    executable = constructor;
                    isCompound = true;
                    break;
                }
            }
        }
        if (executable == null) {
            throw new SimpleCreateException("Set instance executable failed");
        }
        builder.setBuildExecutable(executable);
        if (executable.getParameterCount() > 0 && !isCompound) {
            compoundProcedure.compoundParameters(builder.getBuildExecutable(),
                    paramValues);
        }
        return paramValues;
    }
    
    protected boolean tryCompoundParameters(Executable executable,
            List<Object> paramValues) {
        try {
            List<Object> tmpParamValues = new ArrayList<>();
            if (ObjectHelper.isNotEmpty(paramValues)) {
                tmpParamValues.addAll(paramValues);
            }
            if (!executableMatch(executable, paramValues)) {
                return false;
            }
            compoundProcedure.compoundParameters(executable, tmpParamValues);
            paramValues.clear();
            paramValues.addAll(tmpParamValues);
            return true;
        } catch (Exception e) {
            log.debug("Couldn't fit executable " + executable
                    + " while looking for building executable,"
                    + "the error message is " + e.getMessage());
        }
        return false;
    }

    protected boolean executableMatch(Executable executable,
            List<Object> paramValues) {
        if (ObjectHelper.isEmpty(paramValues)) {
            return true;
        }
        if (executable.getParameterCount() < paramValues.size()) {
            return false;
        }
        Class<?>[] correspondingParamTypes = new Class<?>[paramValues.size()];
        System.arraycopy(executable.getParameterTypes(), 0,
                correspondingParamTypes, 0, paramValues.size());
        if (!ParameterHelper.paramsFit(paramValues.toArray(),
                correspondingParamTypes)) {
            return false;
        }
        return true;
    }

    protected List<Method> getMethods(Class<?> ownerClass, String methodName) {
        List<Method> list = new ArrayList<>();
        for (Method method : MethodHelper.getMethods(ownerClass)) {
            if (methodName.equals(method.getName())) {
                list.add(method);
            }
        }
        list.sort(defaultExecutableComparator);
        return list;
    }

    protected Constructor<?>[] getConstructors() {
        Class<?> beanClass = builder.getBuilderClass();
        Constructor<?>[] result = beanClass.getDeclaredConstructors();
        Arrays.sort(result, defaultExecutableComparator);
        return result;
    }

    protected Boolean dependsOnBuilt = false;

    protected void buildDependsOn() {
        if (!dependsOnBuilt) {
            synchronized (dependsOnBuilt) {
                if (!dependsOnBuilt) {
                    doBuildDependsOn();
                    dependsOnBuilt = true;
                }
            }
        }
    }

    protected void doBuildDependsOn() {
        String[] dependsOn = builder.getDependsOn();
        if (!ObjectHelper.isEmpty(dependsOn)) {
            SimpleContext context = builder.getContext();
            for (String depend : dependsOn) {
                Builder<?> builder = context.getBuilder(depend);
                if (Builder.SCOPE_SINGLETON.equals(builder.getScope())) {
                    context.build(depend);
                }
            }
        }
    }

    public void clear() {
        this.elementProcedure.clear();
        this.placeHolderResolved = false;
        this.dependsOnBuilt = false;
    }

    protected Boolean placeHolderResolved = false;

    protected void resolvePlaceholder() {
        if (!placeHolderResolved) {
            synchronized (placeHolderResolved) {
                if (!placeHolderResolved) {
                    doResolvePlaceholder();
                    placeHolderResolved = true;
                }
            }
        }

    }

    protected void doResolvePlaceholder() {
        SimpleContext context = builder.getContext();
        Map<String, String> properties = context.getAttributes();
        if (!ObjectHelper.isEmpty(builder.getDependsOn())) {
            builder.setDependsOn(ExpressionHelper
                    .resolvePlaceholders(builder.getDependsOn(), properties));
        }
        if (StringHelper.isNotEmpty(builder.getDescription())) {
            builder.setDescription(ExpressionHelper
                    .resolvePlaceholders(builder.getDescription(), properties));
        }
        builder.setScope(ExpressionHelper
                .resolvePlaceholders(builder.getScope(), properties));
        for (Element element : builder.getElements()) {
            if (StringHelper.isNotEmpty(element.getBuilderName())) {
                element.setBuilderName(ExpressionHelper.resolvePlaceholders(
                        element.getBuilderName(), properties));
            }
            if (StringHelper.isNotEmpty(element.getName())) {
                element.setName(ExpressionHelper
                        .resolvePlaceholders(element.getName(), properties));
            }
            if (StringHelper.isNotEmpty(element.getTypeName())) {
                element.setTypeName(ExpressionHelper.resolvePlaceholders(
                        element.getTypeName(), properties));
            }
            Object value = element.getValue();
            if (value instanceof String) {
                value = ExpressionHelper.resolvePlaceholders((String) value,
                        properties);
                element.setValue(value);
            }
        }
    }

    protected static final ExecutableComparator defaultExecutableComparator =
            new ExecutableComparator();

    protected static class ExecutableComparator
            implements Comparator<Executable> {
        @Override
        public int compare(Executable a, Executable b) {
            int i = 0;
            Autowired autowiredA = a.getAnnotation(Autowired.class);
            if (autowiredA != null) {
                i -= 100;
            }
            Autowired autowiredB = b.getAnnotation(Autowired.class);
            if (autowiredB != null) {
                i += 100;
            }
            if (Modifier.isPublic(a.getModifiers())) {
                i -= 10;
            }
            if (Modifier.isPublic(b.getModifiers())) {
                i += 10;
            }
            // Prefer to use executable with less parameters.
            if (a.getParameterCount() > b.getParameterCount()) {
                i += 1;
            }
            if (b.getParameterCount() > a.getParameterCount()) {
                i -= 1;
            }
            return i;

        }

        public ExecutableComparator() {
        }

    }

}
