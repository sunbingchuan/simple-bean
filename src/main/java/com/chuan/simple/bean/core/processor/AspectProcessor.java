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
package com.chuan.simple.bean.core.processor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.chuan.simple.helper.annotation.AnnotationAttribute;
import com.chuan.simple.helper.annotation.AnnotationAttributeHelper;
import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.PatternHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.expression.ExpressionHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.bean.annotation.Around;
import com.chuan.simple.bean.annotation.Aspect;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.exception.SimpleProcessorException;
import com.chuan.simple.constant.Constant;

public class AspectProcessor implements Processor {
    
    private static final String CONSTRUCTOR_PATTERN = "new";
    
    private static final String ARG_TYPES_ANY_PATTERN = "..";
    
    private static final Log log = LogFactory.getLog(AspectProcessor.class);
    
    private final List<PointCut> pointCuts = new ArrayList<>();

    private final SimpleContext context;

    public AspectProcessor(SimpleContext context) {
        this.context = context;
    }

    @Override
    public void processBuilders(Collection<Builder<?>> builders) {
        addPoints(builders);
        applyPoints(builders);
    }

    private void applyPoints(Collection<Builder<?>> builders) {
        for (Builder<?> beanDefinition : builders) {
            walkPointCuts(beanDefinition);
        }
    }

    private void walkPointCuts(Builder<?> builder) {
        Class<?> beanClass = builder.getBuilderClass();
        for (PointCut pointCut : pointCuts) {
            if (pointCut.matchClass(beanClass.getName())) {
                walkPointCut(pointCut, builder);
            }
        }

    }

    private void walkPointCut(PointCut pointCut, Builder<?> builder) {
        Class<?> beanClass = builder.getBuilderClass();
        InvocationHandler handler = pointCut.getHandler();
        if (pointCut.isClassProxy()) {
            builder.setHandler(handler);
        } else {
            for (Method m : MethodHelper.getMethods(beanClass,false)) {
                if (pointCut.matchMethod(m)) {
                    builder.aspect(m, handler);
                }
            }
            for (Constructor<?> constructor : MethodHelper
                    .getConstructors(beanClass)) {
                if (pointCut.matchConstructor(constructor)) {
                    builder.aspect(constructor, handler);
                }
            }
        }
    }

    private void addPoints(Collection<Builder<?>> builders) {
        for (Builder<?> builder : builders) {
            Map<Class<? extends Annotation>, AnnotationAttribute> attributes =
                    AnnotationAttributeHelper.from(builder.getBuilderClass());
            if (attributes.containsKey(Aspect.class)) {
                resolveAround(builder);
            }
        }
    }

    private void resolveAround(Builder<?> builder) {
        Class<?> beanClass = builder.getBuilderClass();
        for (Method m : MethodHelper.getMethods(beanClass,false)) {
            Map<Class<? extends Annotation>, AnnotationAttribute> attributes =
                    AnnotationAttributeHelper.from(m);
            AnnotationAttribute around =attributes.get(Around.class);
            if (around != null) {
                Object aspect = this.context.build(builder.getBuilderName());
                String pointcut = (String) around.getAttribute(Constant.ATTR_VALUE);
                pointcut = ExpressionHelper.resolvePlaceholders(pointcut,
                        this.context.getAttributes());
                if (StringHelper.isNotEmpty(pointcut)) {
                    if (m.getParameterCount() == 0) {
                        Object o = null;
                        try {
                            o = m.invoke(aspect);
                        } catch (Exception e) {
                            log.debug("Invoke around method '" + m + "' failed",
                                    e);
                        }
                        if (o instanceof String) {
                            addPoint(pointcut, (String) o);
                        } else if (o instanceof InvocationHandler) {
                            addPoint(pointcut, (InvocationHandler) o);
                        }
                    } else {
                        addPoint(pointcut, new BaseInvocationHandler(m, aspect));
                    }
                }
            }
        }
    }

    public void addPoint(String pointcut, String handlerRef) {
        pointCuts.add(new PointCut(pointcut, handlerRef));
    }

    public void addPoint(String pointcut, Class<?> handlerClass) {
        pointCuts.add(new PointCut(pointcut, handlerClass));
    }

    public void addPoint(String pointcut, InvocationHandler handler) {
        pointCuts.add(new PointCut(pointcut, handler));
    }

    private class BaseInvocationHandler implements InvocationHandler {

        private Method m;
        
        private Object aspect;
        
        private BaseInvocationHandler(Method m,Object aspect) {
            this.m=m;
            this.aspect=aspect;
        }
        
        @Override
        public Object invoke(Object proxy, Method method,
                Object[] args) throws Throwable {
            return m.invoke(aspect, proxy, method, args);
        }
        
    }
    
    public class PointCut {

        private static final String SPLIT_REGEX = "[ \\\\(\\\\),]+";

        private String pointcut;
        private String builderName;
        private Class<?> builderClass;
        private String returnTypePattern;
        private String classPattern;
        private String methodPattern;
        private String[] argTypePatterns;
        private int modifiers;
        private boolean classProxy = false;


        private void doInit() {
            String[] tokens = pointcut.split(SPLIT_REGEX);
            if (tokens.length == 1) {
                classPattern = tokens[0];
                classProxy = true;
                return;
            }
            int i = 0;
            for (; i < tokens.length; i++) {
                Integer modifier = ClassHelper.getModifier(tokens[i]);
                if (modifier == null) {
                    break;
                }
                modifiers += modifier;
            }
            returnTypePattern = tokens[i++];
            String token = tokens[i++];
            int methodSeparate = token.lastIndexOf('.');
            classPattern = token.substring(0, methodSeparate);
            if ("*".equals(classPattern)) {
                classPattern = "*..*";
            }
            methodPattern = token.substring(methodSeparate + 1);
            argTypePatterns = new String[tokens.length - i];
            System.arraycopy(tokens, i, argTypePatterns, 0, tokens.length - i);
        }

        private void init(){
        	try {
            	doInit();
			} catch (IndexOutOfBoundsException e) {
				throw new SimpleProcessorException("Invalid pointcut "+pointcut,e);
			}
        }
        
        private InvocationHandler handler;

        public PointCut(String pointcut, String handlerRef) {
            this(pointcut, handlerRef, null, null);
        }

        public PointCut(String pointcut, Class<?> handlerClass) {
            this(pointcut, null, null, handlerClass);
        }

        public PointCut(String pointcut, InvocationHandler handler) {
            this(pointcut, null, handler, null);
        }

        private PointCut(String pointcut, String builderName,
                InvocationHandler handler, Class<?> handlerClass) {
            this.pointcut = pointcut;
            this.builderName = builderName;
            this.handler = handler;
            this.builderClass = handlerClass;
            init();
        }

        public String getPointcut() {
            return pointcut;
        }

        public void setPointcut(String pointcut) {
            this.pointcut = pointcut;
        }

        public InvocationHandler getHandler() {
            if (handler != null) {
                return handler;
            }
            if (builderName != null) {
                handler = (InvocationHandler) context.build(builderName);
            }
            if (handler == null && builderClass != null) {
                handler = (InvocationHandler) context.build(builderClass);
                if (handler == null) {
                    try {
                        handler =
                                (InvocationHandler) builderClass.newInstance();
                    } catch (Exception e) {
                        log.error("Couldn.t create handler of pointcut:"
                                + pointcut, e);
                    }
                }
            }
            if (handler == null) {
                handler = new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method,
                            Object[] args) throws Throwable {
                        return method.invoke(proxy, args);
                    }
                };
            }
            return handler;
        }

        public void setHandler(InvocationHandler handler) {
            this.handler = handler;
        }

        public boolean matchClass(String className) {
            return PatternHelper.matchName(classPattern, className);
        }

        public boolean matchConstructor(Constructor<?> constructor) {
            if (!CONSTRUCTOR_PATTERN.equals(methodPattern)) {
                return false;
            }
            if (!argsTypeMatch(constructor)) {
                return false;
            }
            return true;
        }

        public boolean matchMethod(Method method) {
            if (!PatternHelper.matchName(methodPattern, method.getName())) {
                return false;
            }
            if (modifiers != 0 && (modifiers & ~method.getModifiers()) != 0) {
                return false;
            }
            if (!PatternHelper.matchName(returnTypePattern,
                    method.getReturnType().getName())) {
                return false;
            }
            if (!argsTypeMatch(method)) {
                return false;
            }
            return true;
        }

        private boolean argsTypeMatch(Executable exec) {
            if (argTypePatterns.length == 1
                    && ARG_TYPES_ANY_PATTERN.equals(argTypePatterns[0])) {
                return true;
            }
            if (argTypePatterns.length != exec.getParameterCount()) {
                return false;
            }
            if (argTypePatterns.length == 0) {
                return true;
            }
            Class<?>[] types = exec.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                Class<?> type = types[i];
                if (!PatternHelper.matchName(argTypePatterns[i],
                        type.getName())) {
                    return false;
                }
            }
            return true;
        }

        public boolean isClassProxy() {
            return classProxy;
        }

        public void setClassProxy(boolean classProxy) {
            this.classProxy = classProxy;
        }

        public String getBuilderName() {
            return builderName;
        }

        public void setBuilderName(String builderName) {
            this.builderName = builderName;
        }

        public Class<?> getBuilderClass() {
            return builderClass;
        }

        public void setBuilderClass(Class<?> builderClass) {
            this.builderClass = builderClass;
        }

        @Override
        public String toString() {
            return this.pointcut;
        }
    }

}
