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
package com.chuan.simple.bean.core.element.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chuan.simple.helper.common.ObjectHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.Element;
import com.chuan.simple.bean.core.element.entity.ParameterElement;

public abstract class ParameterInstaller<B extends Builder<?>, T extends ParameterElement>
        extends ElementInstaller<B, T> {

    public void parseParameters(List<T> elements,
            List<Element> params) {
        List<T> indexElements = new ArrayList<>();
        for (T element : elements) {
            if (element.getParameterIndex() == null) {
                params.add(element);
            } else {
                indexElements.add(element);
            }
        }
        if (ObjectHelper.isEmpty(indexElements)) {
            return;
        }
        Collections.sort(indexElements, (a, b) -> {
            return a.getParameterIndex() - b.getParameterIndex();
        });
        for (T element : indexElements) {
            while (params.size() < element.getParameterIndex() - 1) {
                params.add(null);
            }
            params.add(element.getParameterIndex() - 1, element);
        }
    }

}
