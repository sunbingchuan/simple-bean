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

import java.util.List;

import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.bean.core.build.builder.Builder;
import com.chuan.simple.bean.core.element.entity.FieldElement;

public class FieldInstaller extends ElementInstaller<Builder<?>, FieldElement> {

    @Override
    public void install(Builder<?> builder, List<FieldElement> elements,
            String period) {
        if (!StringHelper.equals(period, PERIOD_AFTER_CREATE)) {
            return;
        }
        for (FieldElement fieldElement : elements) {
            builder.setField(fieldElement.getName(), fieldElement);
        }
    }

}
