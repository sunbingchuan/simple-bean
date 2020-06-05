package com.bc.simple.bean.core.handler;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.config.ConfigLoader.Node;
import com.bc.simple.bean.core.BeanFactory;

/**
 * this interface is stand for the class which is implement it and is used to
 * handle something like resolve propteries or scan annotationed class
 */
public interface Handler {

	public void setBeanFactory(BeanFactory factory);
	
	

	public BeanDefinition handle(Node element, BeanDefinition containingBd, Node root);

}
