
package com.bc.simple.bean.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.bc.simple.bean.BeanDefinition;


@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bean {


	@AliasFor("name")
	String[] value() default {};


	@AliasFor("value")
	String[] name() default {};


	int autowire() default BeanDefinition.AUTOWIRE_NO;


	boolean autowireCandidate() default true;


	String initMethod() default "";


	String destroyMethod() default BeanDefinition.INFER_METHOD;

}
