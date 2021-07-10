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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.installer.ElementInstaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ElementProcedure extends BuildProcedure
                implements ProcedureDependant {

    private static final Log log = LogFactory.getLog(ElementProcedure.class);

    protected CompoundProcedure compoundProcedure;

    public ElementProcedure(Builder<?> builder) {
        super(builder);
    }

    protected Set<String> periodsInstalled = new HashSet<>();

    public void installElement(String period) {
        if (periodsInstalled.contains(period)) {
            return;
        }
        synchronized (periodsInstalled) {
            if (periodsInstalled.contains(period)) {
                return;
            }
            initElementsByInstaller();
            for (Map.Entry<ElementInstaller<? extends Builder<?>, ? extends Element>, List<Element>> entry : elementsByInstaller
                    .entrySet()) {
                @SuppressWarnings("unchecked")
                ElementInstaller<Builder<?>, Element> installer =
                        (ElementInstaller<Builder<?>, Element>) entry.getKey();
                List<Element> list = entry.getValue();
                installer.install(builder, list, period);
            }
            periodsInstalled.add(period);
        }
    }

    public List<Object> getElementParsedValues(List<Element> params) {
        if (ObjectHelper.isEmpty(params)) {
            return new ArrayList<>();
        }
        List<Object> paramValues = new ArrayList<>(params.size());
        for (Element element : params) {
            if (element == null) {
                paramValues.add(null);
                continue;
            }
            @SuppressWarnings("unchecked")
            ElementInstaller<Builder<?>, Element> installer =
                    (ElementInstaller<Builder<?>, Element>) builder.getContext()
                            .getElementInstaller(element.getClass());
            if (installer==null){
                log.error("Couldn't find installer for element "+element);
                continue;
            }
            installer.parse(builder, element);
            paramValues.add(element.getParsedValue());
        }
        return paramValues;
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
    }

    protected Map<ElementInstaller<? extends Builder<?>, ? extends Element>, List<Element>> elementsByInstaller =
            null;

    protected
            Map<ElementInstaller<? extends Builder<?>, ? extends Element>, List<Element>>
            initElementsByInstaller() {
        if (elementsByInstaller != null) {
            return elementsByInstaller;
        }
        synchronized (this) {
            if (elementsByInstaller != null) {
                return elementsByInstaller;
            }
            elementsByInstaller = new HashMap<>();
            Map<Class<? extends Element>, ElementInstaller<? extends Builder<?>, ? extends Element>> installers =
                    builder.getContext().getElementInstallers();
            for (Element element : builder.getElements()) {
                Class<?> elementClass = element.getClass();
                ElementInstaller<? extends Builder<?>, ? extends Element> installer =
                        installers.get(elementClass);
                if (installer!=null) {
                    List<Element> list = elementsByInstaller.get(installer);
                    if (list==null) {
                        list = new ArrayList<>();
                        elementsByInstaller.put(installer, list);
                    }
                    list.add(element);
                }
            }
            return elementsByInstaller;
        }
    }
    
    public void clearElementParsedValue() {
        for (Element element:builder.privates.getParsedElements()) {
            element.setParsedValue(null);
        }
    }

    public synchronized void clear() {
        this.periodsInstalled.clear();
        this.elementsByInstaller = null;
    }
    
    
}
