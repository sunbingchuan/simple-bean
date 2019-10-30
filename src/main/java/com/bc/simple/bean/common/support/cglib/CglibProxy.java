package com.bc.simple.bean.common.support.cglib;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.core.asm.ClassReader;
import com.bc.simple.bean.core.asm.ClassWriter;
import com.bc.simple.bean.core.asm.MethodVisitor;
import com.bc.simple.bean.core.asm.Opcodes;
import com.bc.simple.bean.core.asm.Type;

@SuppressWarnings({ "rawtypes" })
public class CglibProxy {

	private static final Method classLoaderDefineClassMethod;
	private static final ClassLoader DEFAULT_CLASSLOADER = CglibProxy.class.getClassLoader();
	private static final String LINK_STR = "$$";
	private static final CglibProxy proxy = new CglibProxy();

	private ClassLoader classLoader = DEFAULT_CLASSLOADER;
	static {
		Method classLoaderDefineClass;
		try {
			classLoaderDefineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class,
					Integer.TYPE, Integer.TYPE, ProtectionDomain.class);
			classLoaderDefineClass.setAccessible(true);
		} catch (Throwable t) {
			classLoaderDefineClass = null;
		}
		classLoaderDefineClassMethod = classLoaderDefineClass;
	}

	public static Object getProxyInstance(InvocationHandler handler, Class<?>[] interfaceClasses) {
		return getProxyInstance(handler, Object.class, interfaceClasses);
	}

	public static Object getProxyInstance(InvocationHandler handler, Class<?> parent, Class<?>[] interfaceClasses) {
		return getProxyInstance(handler, parent, null, interfaceClasses);
	}

	public static Object getProxyInstance(InvocationHandler handler, Class<?> parent, Method[] overrides,
			Class<?>[] interfaceClasses) {
		try {
			String className = BeanUtils.convertClassNameToResourcePath(CglibFactory.class.getName()) + LINK_STR
					+ Integer.toHexString(handler.hashCode());
			byte[] bytes = proxy.generateClass(CglibFactory.class, className, parent, overrides, interfaceClasses);
			//Files.write(Paths.get("D:\\programData\\other\\cglib2.class"), bytes);
			Class clazz = proxy.defineClass(BeanUtils.convertResourcePathToClassName(className), bytes, 0, bytes.length,
					null);
			Object factory = clazz.newInstance();
			proxy.setInvoker(factory, handler);
			return factory;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private byte[] generateClass(Class<? extends CglibFactory> initializer, String className, Class<?> parent,
			Method[] overrides, Class<?>[] interfaceClasses) throws IOException {
		String[] interfaces = new String[] {};
		if (interfaceClasses != null) {
			interfaces = Arrays.asList(interfaceClasses).stream().map(Type::getInternalName).toArray(String[]::new);
		}
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(initializer.getName()).setClassName(className).addMethodFilter("<init>").accept(writer,
				ClassReader.SKIP_DEBUG);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(parent), interfaces);
		modifyConstructor(writer, parent, className);
		implInterfaces(writer, interfaceClasses, className);
		if (overrides != null && overrides.length > 0) {
			overrideMethods(writer, overrides, className);
		}
		return writer.toByteArray();
	}

	private void setInvoker(Object factory, InvocationHandler invoker) throws NoSuchMethodException, SecurityException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method method = factory.getClass().getDeclaredMethod("setInvocationHandler",
				new Class<?>[] { InvocationHandler.class });
		method.invoke(factory, invoker);
	}

	private void implInterfaces(ClassWriter writer, Class<?>[] interfaceClasses, String className) {
		for (Class clazz : interfaceClasses) {
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				if (method.isDefault()) {
					continue;
				}
				String[] exceptions = getExceptionsDesc(method);
				MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
						Type.getMethodDescriptor(method), null, exceptions);
				redirectMethod(method, methodVisitor, className);
			}
		}
	}

	private String[] getExceptionsDesc(Method method) {
		return Arrays.asList(method.getExceptionTypes()).stream().map(Type::getDescriptor).toArray(String[]::new);
	}

	private void overrideMethods(ClassWriter writer, Method[] methods, String className) {
		for (Method method : methods) {
			String[] exceptions = getExceptionsDesc(method);
			MethodVisitor methodVisitor = writer.visitMethod(method.getModifiers() & ~Opcodes.ACC_STATIC,
					method.getName(), Type.getMethodDescriptor(method), null, exceptions);
			redirectMethod(method, methodVisitor, className);
			MethodVisitor methodVisitorSuper = writer.visitMethod(method.getModifiers() & ~Opcodes.ACC_STATIC,
					method.getName() + "$SUPER", Type.getMethodDescriptor(method), null, exceptions);
			redirectMethodSuper(method, methodVisitorSuper, className);

		}
	}

	private void modifyConstructor(ClassWriter writer, Class<?> parent, String className) {
		MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, new String[] {});
		methodVisitor.visitMaxs(2, 1);
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(parent), "<init>", "()V", false);
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitInsn(Opcodes.ACONST_NULL);
		methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, className, "invocationHandler",
				"Ljava/lang/reflect/InvocationHandler;");
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "init", "()V", false);
		methodVisitor.visitInsn(Opcodes.RETURN);
	}

	private void redirectMethodSuper(Method method, MethodVisitor methodVisitor, String className) {
		int count = method.getParameterCount();
		methodVisitor.visitMaxs(count + 1, count + 1);
		for (int i = 0; i < count + 1; i++) {
			methodVisitor.visitVarInsn(Opcodes.ALOAD, i);
		}
		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(method.getDeclaringClass()),
				method.getName(), Type.getMethodDescriptor(method), false);
		if (method.getReturnType().equals(Void.TYPE)) {
			methodVisitor.visitInsn(Opcodes.RETURN);
		} else {
			methodVisitor.visitInsn(Type.getType(method.getReturnType()).getOpcode(Opcodes.IRETURN));
		}
	}

	private void redirectMethod(Method method, MethodVisitor methodVisitor, String className) {
		int count = method.getParameterCount();
		methodVisitor.visitMaxs(3, count + 3);
		if (count == 1 && method.getParameterTypes()[0].isArray()
				&& !method.getParameterTypes()[0].getComponentType().isPrimitive()) {
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
					"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Method;", false);
			methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke",
					"(Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", false);
		} else {
			newArray(method, methodVisitor, count);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
					"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Method;", false);
			methodVisitor.visitVarInsn(Opcodes.ASTORE, count + 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke",
					"(Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", false);
		}
		Class<?> returnType = method.getReturnType();
		if (returnType.equals(Void.TYPE)) {
			methodVisitor.visitInsn(Opcodes.POP);
			methodVisitor.visitInsn(Opcodes.RETURN);
		} else {
			if (returnType.isPrimitive()) {
				Class<?> wrapType = BeanUtils.resolvePrimitiveIfNecessary(returnType);
				methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(wrapType));
				methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(wrapType),
						returnType.getName() + "Value", "()" + Type.getDescriptor(returnType), false);
			} else {
				methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
			}
			methodVisitor.visitInsn(Type.getType(returnType).getOpcode(Opcodes.IRETURN));
		}
		methodVisitor.visitEnd();
	}

	private void newArray(Method method, MethodVisitor methodVisitor, int length) {
		methodVisitor.visitIntInsn(Opcodes.BIPUSH, length);
		methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		methodVisitor.visitVarInsn(Opcodes.ASTORE, length + 1);
		for (int i = 0; i < length; i++) {
			Class<?> type = method.getParameterTypes()[i];
			methodVisitor.visitVarInsn(Opcodes.ALOAD, length + 1);
			methodVisitor.visitIntInsn(Opcodes.BIPUSH, i);
			if (type.isPrimitive()) {
				Class<?> wrapType = BeanUtils.resolvePrimitiveIfNecessary(type);
				methodVisitor.visitVarInsn(Type.getType(type).getOpcode(Opcodes.ILOAD), i + 1);
				methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(wrapType), "valueOf",
						"(" + Type.getDescriptor(type) + ")" + Type.getDescriptor(wrapType), false);
			} else {
				methodVisitor.visitVarInsn(Opcodes.ALOAD, i + 1);
			}
			methodVisitor.visitInsn(Opcodes.AASTORE);
		}
	}

	private Class<?> defineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
		try {
			return (Class<?>) classLoaderDefineClassMethod.invoke(classLoader, name, b, off, len, protectionDomain);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public static void main(String[] args) throws NoSuchMethodException, SecurityException {
		Method[] overrides = new Method[1];
		overrides[0] = CglibProxy.class.getDeclaredMethod("main", new Class<?>[] { String[].class });
		Object obj = CglibProxy.getProxyInstance(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

				System.out.println(method.getName());
				System.out.println(args != null && args.length > 0 ? args[0] : null);
				if (method.getName().equals("main")) {
					return method.invoke(proxy, args);
				}
				return 123;
			}
		}, CglibProxy.class, overrides, new Class<?>[] { List.class });
		List list = (List) obj;
		System.out.println(list.get(1));
		CglibProxy proxy = (CglibProxy) obj;
		System.out.println(proxy);
		proxy.main(new String[] { "asdf" });
	}

}
