package com.bc.simple.bean.core.handler;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.config.ConfigLoader.Node;

/**
 * this interface is stand for the class which is implement it and is used to
 * handle something like resolve propteries or scan annotationed class
 */
public interface Handler {

	public String getDomain();

	public BeanDefinition handle(Node element, BeanDefinition containingBd, Node root);

}
