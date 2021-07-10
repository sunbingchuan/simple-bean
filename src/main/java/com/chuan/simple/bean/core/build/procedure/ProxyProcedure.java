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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.helper.method.MethodHelper;
import com.chuan.simple.helper.method.ParameterHelper;
import com.chuan.simple.helper.proxy.BaseProxy;
import com.chuan.simple.helper.proxy.ProxyHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.Element;

public class ProxyProcedure extends BuildProcedure
        implements ProcedureDependant {

    protected CompoundProcedure compoundProcedure;

    protected ElementProcedure elementProcedure;

    private boolean checkWrap = true;

    public ProxyProcedure(Builder<?> builder) {
        super(builder);
    }

    /**
     * Proxy class as necessary.
     * 
     * @see ProxyHelper#proxy(Class, InvocationHandler)
     * @see ProxyHelper#instance(InvocationHandler, Class, Method[], Class[])
     */
    public Object optionalProxy() {
        initializeProcedure();
        Object result = null;
        if (ObjectHelper.isNotEmpty(builder.getBuildParameters())) {
            return result;
        }
        Class<?> builderClass = builder.getBuilderClass();
        if (builder.getHandler() != null) {
            result = ProxyHelper.instance(builder.getHandler(), builderClass,
                    MethodHelper.getMethods(builderClass)
                            .toArray(new Method[0]));
        } else if (proxyConstructor()) {
            result = ProxyHelper.proxy(builderClass, defaultInvocationHandler);
        } else if (!ObjectHelper.isEmpty(builder.getAspects())) {
            result = ProxyHelper.instance(defaultInvocationHandler,
                    builderClass,
                    builder.getAspects().keySet().toArray(new Method[0]),
                    new Class[] {});
        }
        if (result != null) {
            checkWrap = false;
        }
        return result;
    }

    /**
     * Wrap bean as necessary.
     * 
     * @see ProxyHelper#wrap(Class, Object, InvocationHandler)
     */
    public Object optionalWrapBean(Object result) {
        if (!checkWrap) {
            return result;
        }
        Class<?> builderClass = builder.getBuilderClass();
        InvocationHandler handler = null;
        if (builder.getHandler() != null) {
            handler = builder.getHandler();
        } else if (!ObjectHelper.isEmpty(builder.getAspects())) {
            handler = defaultInvocationHandler;
        }
        if (handler != null) {
            result = ProxyHelper.wrap(builderClass, result, handler);
        }
        return result;
    }

    protected Object[] fitParameters(Method method, Object[] args,
            Map<Executable, Executable> correspondExecutableCache) {
        Executable originalExecutable = correspondExecutableCache.get(method);
        Parameter[] parameters = ParameterHelper.getParameters(method);
        if (method.getParameterCount() == 0) {
            return args;
        }
        if (args == null) {
            args = new Object[method.getParameterCount()];
        }
        List<Object> parameterValues = null;
        if (originalExecutable != null) {
            List<Element> params =
                    builder.getExecutableParameters().get(originalExecutable);
            parameterValues = elementProcedure.getElementParsedValues(params);
        }
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (args[i] == null) {
                if (parameterValues != null && parameterValues.size() > i) {
                    Object parameterValue = parameterValues.get(i);
                    if (parameterValue != null) {
                        args[i] = parameterValue;
                        continue;
                    }
                }
                if (!builder.isAutowiredExecutable()) {
                    continue;
                }
                args[i] = this.compoundProcedure.compound(parameter);
            }
        }
        return args;
    }

    protected boolean proxyConstructor() {
        Boolean result = false;
        Set<Executable> methods = builder.getAspects().keySet();
        for (Executable method : methods) {
            if (method instanceof Constructor) {
                result = true;
                break;
            }
        }
        return result;
    }

    protected InvocationHandler findHandler(Executable executable,
            Map<Executable, InvocationHandler> overrideMethods,
            Map<Executable, Executable> correspondExecutableCache) {
        Executable originalExecutable =
                correspondExecutableCache.get(executable);
        if (originalExecutable == null) {
            if ((originalExecutable =
                    getOriginalExecutable(executable)) != null) {
                correspondExecutableCache.put(executable, originalExecutable);
            }
        }
        InvocationHandler target = null;
        if (originalExecutable != null)
            target = overrideMethods.get(originalExecutable);
        if (target == null) {
            target = new BaseInvocationHandler();
        }
        return target;
    }

    protected Executable getOriginalExecutable(Executable proxyMethod) {
        Executable originalExecutable = null;
        Class<?> builderClass = builder.getBuilderClass();
        try {
            Class<?>[] parameterTypes = proxyMethod.getParameterTypes();
            if (StringHelper.equals(proxyMethod.getName(),
                    BaseProxy.PROXY_CONSTRUCTOR_NAME)) {
                originalExecutable = MethodHelper.findConstructor(builderClass,
                        parameterTypes);
            } else {
                String originalClassMethodName = proxyMethod.getName()
                        .replace(BaseProxy.PROXY_SUFFIX, "");
                originalExecutable = MethodHelper.findMethod(builderClass,
                        originalClassMethodName, parameterTypes);
            }
        } catch (Exception e) {
            // ignore
        }
        return originalExecutable;
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
        if (this.elementProcedure == null) {
            synchronized (this) {
                if (this.elementProcedure == null) {
                    this.elementProcedure =
                            builder.getProcedure(ElementProcedure.class);
                }
            }
        }
    }

    public void clear() {
        checkWrap = true;
    }

    private DefaultInvocationHandler defaultInvocationHandler =
            new DefaultInvocationHandler();

    private class DefaultInvocationHandler implements InvocationHandler {
        Map<Executable, Executable> correspondExecutableCache =
                new ConcurrentHashMap<>();

        private DefaultInvocationHandler() {
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            InvocationHandler invocationHandler = findHandler(method,
                    builder.getAspects(), correspondExecutableCache);
            args = fitParameters(method, args, correspondExecutableCache);
            return invocationHandler.invoke(proxy, method, args);
        }
    }
    private class BaseInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            return method.invoke(proxy, args);
        }
        
    }

}
