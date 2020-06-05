package com.bc.simple.bean.common.util.pattern;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.StringUtils;

public class ApiGenerater {


	
	public void generate(List<Util> utils,String className,String path) {
		StringBuffer code =  new StringBuffer();
		String packageName = "";
		int index = className.lastIndexOf(".");
		if (index>0) {
			packageName=className.substring(0,index);
			className=className.substring(index+1);
		}
		code.append("package "+packageName+";\r\n");
		code.append("public class "+className+"{ \r\n");
		for (Util util : utils) {
			for (Method method : util.getClass().getDeclaredMethods()) {
				if (Modifier.isPublic(method.getModifiers())&&!UtilFactory.GET_DOMAIN.equals(method.getName())) {
					code.append("public static "+method.getReturnType().getTypeName()+" ");
					code.append(UtilFactory.buildFullName(util, method)+"(");
					String[] names;
					if (method.getParameterCount()>0) {
						names = BeanUtils.getParameterNames(method);
						int i=0;
						for (Class<?> clazz : method.getParameterTypes()) {
							if(StringUtils.isEmpty(names[i])) {
								names[i]=method.getParameters()[i].getName();
							}
							code.append(clazz.getTypeName()+" "+names[i++]);
							if (i!=method.getParameterCount()) {
								code.append(" ,");
							}
						}
					}else {
						names=new String[0];
					}
					code.append("){\r\n");
					if (!method.getReturnType().equals(Void.TYPE)) {
						code.append("return ("+method.getReturnType().getTypeName()+")");
					}
					code.append("com.bc.simple.bean.common.util.pattern.UtilFactory.util");
					code.append(Arrays.asList(names).toString().replace("[", "(").replace("]", ")"));
					code.append(";\r\n}\r\n");
					
				}
			}
		}
		code.append("}");
		System.out.println(code.toString());
		try {
			Files.write(Paths.get(path, className+".java"), code.toString().getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	
	public static void main(String[] args) {
		new ApiGenerater().generate(Arrays.asList(new SomeUtil()), "com.bc.simple.bean.common.util.pattern.MyApi", "E:\\workplace\\eclipse\\simple-bean\\src\\main\\java\\com\\bc\\simple\\bean\\common\\util\\pattern\\");
	}
	
}
