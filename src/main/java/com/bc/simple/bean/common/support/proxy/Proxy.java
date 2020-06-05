package com.bc.simple.bean.common.support.proxy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.core.asm.ClassReader;
import com.bc.simple.bean.core.asm.ClassReader.MethodFilter;
import com.bc.simple.bean.core.asm.ClassVisitor;
import com.bc.simple.bean.core.asm.ClassWriter;
import com.bc.simple.bean.core.asm.MethodVisitor;
import com.bc.simple.bean.core.asm.Opcodes;
import com.bc.simple.bean.core.asm.Type;
import com.bc.simple.bean.core.support.SimpleException;

@SuppressWarnings({ "rawtypes" })
public class Proxy {

	private static Log log = LogFactory.getLog(Proxy.class);

	private static final Method classLoaderDefineClassMethod;
	private static final ClassLoader DEFAULT_CLASSLOADER = Proxy.class.getClassLoader();
	public static final String LINK_STR = "$$";
	public static final String PROXY_SUFFIX = "$SUPER";
	private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>(256);

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

	/**
	 * @param handler
	 * @param interfaceClasses
	 * @return
	 * @see Proxy#instance(InvocationHandler, Class, Method[], Class[])
	 */
	public Object instance(InvocationHandler handler, Class<?>[] interfaceClasses) {
		return instance(handler, Object.class, interfaceClasses);
	}

	/**
	 * @param handler
	 * @param parent
	 * @param interfaceClasses
	 * @see Proxy#instance(InvocationHandler, Class, Method[], Class[])
	 * @return
	 */
	public Object instance(InvocationHandler handler, Class<?> parent, Class<?>[] interfaceClasses) {
		return instance(handler, parent, null, interfaceClasses);
	}

	/**
	 * @param handler
	 *            the InvocationHandler
	 * @param parent
	 *            the class to be extended
	 * @param overrides
	 *            the method of parent to be overrided or aoped
	 * @param interfaceClasses
	 *            the interface to implements
	 * @return
	 */
	public Object instance(InvocationHandler handler, Class<?> parent, Method[] overrides,
			Class<?>[] interfaceClasses) {
		try {
			String parentName = parent.getCanonicalName();
			if (parentName.startsWith("java.")) {
				parentName = "proxy." + parentName;
			}
			String className = BeanUtils.convertClassNameToResourcePath(parentName) + LINK_STR
					+ Integer.toHexString(handler.hashCode());
			byte[] bytes = generateClass(Template.class, className, parent, overrides, interfaceClasses);
			Class clazz = defineClass(BeanUtils.convertResourcePathToClassName(className), bytes, 0, bytes.length,
					null);
			Object bean = clazz.newInstance();
			setInvoker(bean, handler);
			return bean;
		} catch (Exception e) {
			log.error("instance failed!", e);
		}
		return null;
	}

	private byte[] generateClass(Class<?> initializer, String className, Class<?> parent, Method[] overrides,
			Class<?>[] interfaceClasses) throws IOException {
		String[] interfaces = new String[] {};
		if (interfaceClasses != null) {
			interfaces = Arrays.asList(interfaceClasses).stream().map(Type::getInternalName).toArray(String[]::new);
		}
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(initializer.getName()).setClassName(className).setMethodFilter(new MethodFilter() {
			@Override
			public MethodVisitor visitMethod(ClassReader classReader, ClassVisitor classVisitor, int access,
					String name, String descriptor, String signature, String[] exceptions) {
				if ("<init>".equals(name)) {
					return null;
				}
				return classVisitor.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}).accept(writer, ClassReader.SKIP_DEBUG);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(parent), interfaces);
		modifyConstructor(writer, parent, className);
		implInterfaces(writer, interfaceClasses, className);
		if (overrides != null && overrides.length > 0) {
			overrideMethods(writer, overrides, className);
		}
		return writer.toByteArray();
	}

	public Object proxy(Class<?> target, Class<? extends InvocationHandler> handler) {
		try {
			String className = BeanUtils.convertClassNameToResourcePath(target.getCanonicalName()) + LINK_STR
					+ handler.hashCode();
			Class clazz;
			if (classCache.containsKey(className)) {
				clazz = classCache.get(className);
			} else {
				byte[] bytes = generateClass(target, className, handler);
				clazz = defineClass(BeanUtils.convertResourcePathToClassName(className), bytes, 0, bytes.length, null);
				classCache.put(className, clazz);
			}
			Object bean = clazz.newInstance();
			return bean;
		} catch (Exception e) {
			log.error("proxy failed!", e);
		}
		return null;
	}

	private byte[] generateClass(Class<?> target, String className, Class<? extends InvocationHandler> handler)
			throws IOException {
		String[] interfaces = Arrays.asList(target.getInterfaces()).stream().map(Type::getInternalName)
				.toArray(String[]::new);
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(Template.class.getName()).setClassName(className).setMethodFilter(new MethodFilter() {
			@Override
			public MethodVisitor visitMethod(ClassReader classReader, ClassVisitor classVisitor, int access,
					String name, String descriptor, String signature, String[] exceptions) {
				if ("<init>".equals(name)) {
					return null;
				}
				return classVisitor.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}).accept(writer, ClassReader.SKIP_DEBUG);
		new ClassReader(target.getName()).setMethodFilter(new MethodFilter() {
			@Override
			public MethodVisitor visitMethod(ClassReader classReader, ClassVisitor classVisitor, int access,
					String name, String descriptor, String signature, String[] exceptions) {
				if ("<init>".equals(name)) {
					name = "init";
					classReader.addCodeSkip(4);
				}
				name += PROXY_SUFFIX;
				return classVisitor.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}).accept(writer, ClassReader.SKIP_DEBUG);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(target.getSuperclass()),
				interfaces);
		proxyMethods(writer, target, className);
		proxyConstructors(writer, target, className, handler);
		return writer.toByteArray();
	}

	private void proxyMethods(ClassWriter writer, Class<?> target, String className) {
		Method[] methods = target.getDeclaredMethods();
		for (Method method : methods) {
			String[] exceptions = getExceptionsDesc(method);
			MethodVisitor methodVisitor = writer.visitMethod(method.getModifiers() & ~Opcodes.ACC_STATIC,
					method.getName(), Type.getMethodDescriptor(method), null, exceptions);
			proxyMethod(method, methodVisitor, className);
		}
	}

	private void proxyConstructors(ClassWriter writer, Class<?> target, String className,
			Class<? extends InvocationHandler> handler) {
		Constructor<?>[] constructors = target.getDeclaredConstructors();
		for (Constructor<?> constructor : constructors) {
			String[] exceptions = getExceptionsDesc(constructor);
			MethodVisitor methodVisitor = writer.visitMethod(constructor.getModifiers(), "<init>",
					Type.getConstructorDescriptor(constructor), null, exceptions);
			methodVisitor.visitMaxs(2, 1);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(target.getSuperclass()), "<init>",
					"()V", false);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitLdcInsn(Type.getObjectType(Type.getInternalName(handler)));
			methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "newInstance",
					"()Ljava/lang/Object;", false);
			methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(InvocationHandler.class));
			methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, className, "invocationHandler$TEMPLATE",
					"Ljava/lang/reflect/InvocationHandler;");
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "init$TEMPLATE", "()V", false);
			int count = constructor.getParameterCount();
			methodVisitor.visitMaxs(3, count + 3);
			if (count == 1 && constructor.getParameterTypes()[0].isArray()
					&& !constructor.getParameterTypes()[0].getComponentType().isPrimitive()) {
				methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
				methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
						"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Executable;", false);
				methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
				methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke$TEMPLATE",
						"(Ljava/lang/reflect/Executable;[Ljava/lang/Object;)Ljava/lang/Object;", false);
			} else {
				newArray(constructor, methodVisitor, count);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
				methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
						"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Executable;", false);
				methodVisitor.visitVarInsn(Opcodes.ASTORE, count + 2);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 2);
				methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
				methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke$TEMPLATE",
						"(Ljava/lang/reflect/Executable;[Ljava/lang/Object;)Ljava/lang/Object;", false);
			}
			methodVisitor.visitInsn(Opcodes.RETURN);
		}
	}

	public void setInvoker(Object bean, InvocationHandler invoker) {
		try {
			Method method = bean.getClass().getDeclaredMethod("setInvocationHandler$TEMPLATE",
					new Class<?>[] { InvocationHandler.class });
			method.invoke(bean, invoker);
		} catch (Exception e) {
			throw new SimpleException(e);
		}
	}

	private void implInterfaces(ClassWriter writer, Class<?>[] interfaceClasses, String className) {
		if (interfaceClasses == null) {
			return;
		}
		for (Class clazz : interfaceClasses) {
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				if (method.isDefault()) {
					continue;
				}
				String[] exceptions = getExceptionsDesc(method);
				MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
						Type.getMethodDescriptor(method), null, exceptions);
				proxyMethod(method, methodVisitor, className);
			}
		}
	}

	private String[] getExceptionsDesc(Executable method) {
		return Arrays.asList(method.getExceptionTypes()).stream().map(Type::getDescriptor).toArray(String[]::new);
	}

	private void overrideMethods(ClassWriter writer, Method[] methods, String className) {
		for (Method method : methods) {
			String[] exceptions = getExceptionsDesc(method);
			MethodVisitor methodVisitor = writer.visitMethod(method.getModifiers() & ~Opcodes.ACC_STATIC,
					method.getName(), Type.getMethodDescriptor(method), null, exceptions);
			proxyMethod(method, methodVisitor, className);
			MethodVisitor methodVisitorSuper = writer.visitMethod(method.getModifiers() & ~Opcodes.ACC_STATIC,
					method.getName() + PROXY_SUFFIX, Type.getMethodDescriptor(method), null, exceptions);
			redirectSuper(method, methodVisitorSuper, className);

		}
	}

	private void modifyConstructor(ClassWriter writer, Class<?> parent, String className) {
		MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, new String[] {});
		methodVisitor.visitMaxs(2, 1);
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(parent), "<init>", "()V", false);
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitInsn(Opcodes.ACONST_NULL);
		methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, className, "invocationHandler$TEMPLATE",
				"Ljava/lang/reflect/InvocationHandler;");
		methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "init$TEMPLATE", "()V", false);
		methodVisitor.visitInsn(Opcodes.RETURN);
	}

	private void redirectSuper(Method method, MethodVisitor methodVisitor, String className) {
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

	private void proxyMethod(Method method, MethodVisitor methodVisitor, String className) {
		int count = method.getParameterCount();
		methodVisitor.visitMaxs(3, count + 3);
		if (count == 1 && method.getParameterTypes()[0].isArray()
				&& !method.getParameterTypes()[0].getComponentType().isPrimitive()) {
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
					"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Executable;", false);
			methodVisitor.visitVarInsn(Opcodes.ASTORE, 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke$TEMPLATE",
					"(Ljava/lang/reflect/Executable;[Ljava/lang/Object;)Ljava/lang/Object;", false);
		} else {
			newArray(method, methodVisitor, count);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(BeanUtils.class),
					"getCurrentMethod", "([Ljava/lang/Object;)Ljava/lang/reflect/Executable;", false);
			methodVisitor.visitVarInsn(Opcodes.ASTORE, count + 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 2);
			methodVisitor.visitVarInsn(Opcodes.ALOAD, count + 1);
			methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "invoke$TEMPLATE",
					"(Ljava/lang/reflect/Executable;[Ljava/lang/Object;)Ljava/lang/Object;", false);
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

	private void newArray(Executable method, MethodVisitor methodVisitor, int length) {
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

}
