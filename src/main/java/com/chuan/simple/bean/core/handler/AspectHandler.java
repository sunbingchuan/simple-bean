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
package com.chuan.simple.bean.core.handler;

import com.chuan.simple.helper.clazz.ClassHelper;
import com.chuan.simple.helper.common.StringHelper;
import com.chuan.simple.bean.core.SimpleContext;
import com.chuan.simple.bean.core.config.node.Node;
import com.chuan.simple.bean.core.processor.AspectProcessor;
import com.chuan.simple.constant.Constant;

/**
 * Handler to deal with Node 'aspect'.
 * <p>
 * The function of {@link Node} 'aspect' is intercepting specific
 * {@link java.lang.reflect.Executable} ({@link java.lang.reflect.Method} or
 * {@link java.lang.reflect.Constructor})
 */
public class AspectHandler implements Handler {

    private SimpleContext context;

    private AspectProcessor processor;

    public AspectHandler() {
    }

    @Override
    public void handle(Node ele) {
        initProcessor();
        if (processor != null) {
            String pointcut = ele.attrString(Constant.ATTR_POINTCUT);
            if (StringHelper.isNotEmpty(pointcut)) {
                String ref = ele.attrString(Constant.ATTR_REF);
                if (StringHelper.isNotEmpty(ref)) {
                    this.processor.addPoint(pointcut, ref);
                    return;
                }
                String handlerClassName = ele.attrString(Constant.ATTR_TYPE);
                if (StringHelper.isNotEmpty(handlerClassName)) {
                    this.processor.addPoint(pointcut,
                            ClassHelper.forName(handlerClassName));
                }
            }
        }
    }

    @Override
    public void setContext(SimpleContext context) {
        this.context = context;
    }

    private void initProcessor() {
        if (this.processor == null) {
            synchronized (this) {
                if (this.processor == null) {
                    this.processor = new AspectProcessor(context);
                    context.addProcessor(this.processor);
                }
            }
        }
    }

}
