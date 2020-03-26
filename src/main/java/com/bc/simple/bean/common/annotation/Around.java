package com.bc.simple.bean.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Around {

	/**
	 * {modifier} {return type} {class.method(..|arg Type,arg Type)}
	 */
	String value();

}
