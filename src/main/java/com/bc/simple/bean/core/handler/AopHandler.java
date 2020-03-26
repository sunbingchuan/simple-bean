package com.bc.simple.bean.core.handler;

import java.lang.reflect.InvocationHandler;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.BeanFactory;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.common.util.StringUtils;
import com.bc.simple.bean.core.processor.AspectProcessor;

public class AopHandler implements Handler {



	private BeanFactory beanFactory;


	public AopHandler() {}


	@SuppressWarnings("unchecked")
	@Override
	public BeanDefinition handle(Node element, BeanDefinition containingBd, Node root) {
		AspectProcessor processor = new AspectProcessor(beanFactory);
		String pointcut = element.attrString(Constant.ATTR_POINTCUT);
		if (StringUtils.isNotEmpty(pointcut)) {
			String handlerClassStr = element.attrString(Constant.ATTR_HANDLER_CLASS);
			if (StringUtils.isNotEmpty(handlerClassStr)) {
				Class<? extends InvocationHandler> handlerClass = (Class<? extends InvocationHandler>) BeanUtils.forName(handlerClassStr);
				if (handlerClass != null) {
					processor.addPoint(pointcut, handlerClass);
				} else {
					String handlerRef = element.attrString(Constant.ATTR_HANDLER_REF);
					if (StringUtils.isNotEmpty(handlerRef)) {
						processor.addPoint(pointcut, handlerRef);
					}
				}
			}
		}
		beanFactory.addProcessor(processor);
		return containingBd;
	}

	@Override
	public void setBeanFactory(BeanFactory factory) {
		this.beanFactory = factory;
	}

}
