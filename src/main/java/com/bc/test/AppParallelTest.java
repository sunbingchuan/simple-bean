package com.bc.test;

import java.lang.reflect.InvocationTargetException;

import com.bc.simple.bean.ApplicationContext;
import com.bc.test.a.AutowareByMicroControlTest;
import com.bc.test.a.ConfigurationBeanTest;
import com.bc.test.a.ConstructorArgTest;
import com.bc.test.a.FirstBean;
import com.bc.test.a.FiveBean;
import com.bc.test.a.MutiTypeTest;
import com.bc.test.a.PropertyTest;
import com.bc.test.a.SecondBean;
import com.bc.test.a.SelfReference;
import com.bc.test.a.ThirdTestBean;
import com.bc.test.b.AspectBean;
import com.bc.test.b.AspectTest;
import com.bc.test.parallel.ParallelTest;

public class AppParallelTest {
	public static void main(String[] args) throws Exception {
		ApplicationContext context = new ApplicationContext("spring.config");
		context.refresh();
		FirstBean bean;
		
		ParallelTest.execute(new Runnable() {
			@Override
			public void run() {
//				System.out.println(context.getBean("makeSuperMan"));
//				Object obj = context.getBean("firstBean");
				FirstBean bean=null;
//				System.out.println(bean = context.getBean(FirstBean.class));
//				System.out.println(bean.s);
//				System.out.println("---------replaced------------");
//				bean = (FirstBean) context.getBean("firstBeanTmp");
//				System.out.println(bean);
//				System.out.println(bean.s);
//				bean.toReplace();
//				System.out.println("---------replaced------------");
//				SecondBean second = context.getBean(SecondBean.class);
//				System.out.println(second.factory);
//				System.out.println(second.applicationContext);
//				System.out.println("------------is on other----------------");
//				System.out.println(context.getBean(ConfigurationBeanTest.class));
//				System.out.println(context.getBean("makeSuperMan"));
		//
//				System.out.println("-------------second----------");
//				SelfReference self = context.getBean(SelfReference.class);
//				System.out.println(self.self);
		//
//				System.out.println("----------------third--------------");
//				ThirdTestBean third = context.getBean(ThirdTestBean.class);
//				System.out.println(third);
				System.out.println("-------------------alias-------------");
				System.out.println(context.getBean("firstBeanTmpAlias"));
				System.out.println("---------------------constructor-arg----------------");
				System.out.println(context.getBean(ConstructorArgTest.class));
				System.out.println("----------------------property-------------------------");
				System.out.println(context.getBean(PropertyTest.class));
				System.out.println("--------------------------mutiTypeTest--------------------------");
				System.out.println(context.getBean(MutiTypeTest.class));
				System.out.println("--------------------------micro-control----------------------");
				System.out.println(context.getBean(AutowareByMicroControlTest.class).hashCode());
				System.out.println(context.getBean(AutowareByMicroControlTest.class).hashCode());
				System.out.println(context.getBean(AutowareByMicroControlTest.class));
				System.out.println(context.getBean(String.class));
				System.out.println(context.getBean(FiveBean.class));
				System.out.println(context.getBean("66666"));
				System.out.println(context.getBean("66666"));
				System.out.println("-------------------------processor/aop---------------------");
				System.out.println(context.getBean(AspectTest.class));
				AspectBean aspect =context.getBean(AspectBean.class);
				System.out.println(aspect);
				aspect.angry();
				try {
					Object aspectA = context.getBean("aspectBeanA");	
					System.out.println(aspectA);
					aspectA.getClass().getDeclaredMethod("angry").invoke(aspectA);
					//aspectA.angry();
					Object aspectB =context.getBean("aspectBeanB");
					System.out.println(aspectB);
					aspectB.getClass().getDeclaredMethod("angry").invoke(aspectB);
					//aspectB.angry();
					Object aspectC =context.getBean("aspectBeanC");
					System.out.println(aspectC);
					aspectC.getClass().getDeclaredMethod("angry").invoke(aspectC);
					//aspectC.angry();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
				
			}
		});

	}
}
