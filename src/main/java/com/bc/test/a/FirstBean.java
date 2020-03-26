package com.bc.test.a;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import com.bc.simple.bean.BeanDefinition;
import com.bc.simple.bean.common.annotation.Autowired;
import com.bc.simple.bean.common.annotation.Component;
import com.bc.simple.bean.common.annotation.Scope;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class FirstBean {
	@Autowired
	public String s;

	public void toReplace() {
		System.out.println("this is origin.");
	}

	public static class Replacer implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			System.out.println("now it has been replaced!");
			return null;
		}

	}
}
