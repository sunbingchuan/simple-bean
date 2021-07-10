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
package com.chuan.simple.bean.core.element.entity;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

public class ParameterElement extends Element {

    protected Parameter parameter;

    protected Class<?> declaringClass;

    protected String declaringExecutableName;

    protected String declaringClassName;

    protected Integer parameterIndex;
    
    private Executable declaringExecutable;

    public ParameterElement(String declaringClassName,
            String declaringExecutableName, String name) {
        this.declaringClassName = declaringClassName;
        this.declaringExecutableName = declaringExecutableName;
        this.name = name;
    }

    public ParameterElement(Parameter parameter) {
        setParameter(parameter);
    }

    public Parameter getParameter() {
        return parameter;
    }

    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
        setDeclaringExecutable(this.parameter.getDeclaringExecutable());
    }

    public Executable getDeclaringExecutable() {
        return declaringExecutable;
    }

    public void setDeclaringExecutable(Executable declaringExecutable) {
        this.declaringExecutable = declaringExecutable;
        this.declaringExecutableName = this.declaringExecutable.getName();
        this.declaringClass = this.declaringExecutable.getDeclaringClass();
        this.declaringClassName = this.declaringClass.getName();
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(Class<?> declaringClass) {
        this.declaringClass = declaringClass;
    }

    public String getDeclaringExecutableName() {
        return declaringExecutableName;
    }

    public void setDeclaringExecutableName(String declaringExecutableName) {
        this.declaringExecutableName = declaringExecutableName;
    }

    public String getDeclaringClassName() {
        return declaringClassName;
    }

    public void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

    public Integer getParameterIndex() {
        return parameterIndex;
    }

    public void setParameterIndex(Integer parameterIndex) {
        this.parameterIndex = parameterIndex;
    }

}
