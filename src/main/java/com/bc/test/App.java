package com.bc.test;

import com.bc.simple.bean.ApplicationContext;

public class App {
	public static void main(String[] args) {
		ApplicationContext context = new ApplicationContext("spring.config");
		context.refresh();
		FirstBean bean;
		System.out.println(context.getBean("makeSuperMan"));
		System.out.println(context.getBean("firstBean"));
		System.out.println(bean = context.getBean(FirstBean.class));
		System.out.println(bean.s);
		System.out.println("---------replaced------------");
		bean = (FirstBean) context.getBean("firstBeanTmp");
		System.out.println(bean);
		System.out.println(bean.s);
		bean.toReplace();
		System.out.println("---------replaced------------");
		SecondBean second = context.getBean(SecondBean.class);
		System.out.println(second.factory);
		System.out.println(second.applicationContext);
		System.out.println("------------is on other----------------");
		System.out.println(context.getBean(ConfigurationBeanTest.class));
		System.out.println(context.getBean("makeSuperMan"));

		System.out.println("-------------second----------");
		SelfReference self = context.getBean(SelfReference.class);
		System.out.println(self.self);

		System.out.println("----------------third--------------");
		ThirdTestBean third = context.getBean(ThirdTestBean.class);
		System.out.println(third);
	}
}
