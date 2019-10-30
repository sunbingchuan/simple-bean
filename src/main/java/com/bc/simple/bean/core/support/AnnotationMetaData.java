package com.bc.simple.bean.core.support;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bc.simple.bean.common.Resource;
import com.bc.simple.bean.common.util.AnnotationUtils;
import com.bc.simple.bean.common.util.BeanUtils;
import com.bc.simple.bean.common.util.Constant;
import com.bc.simple.bean.core.asm.AnnotationVisitor;
import com.bc.simple.bean.core.asm.ClassVisitor;
import com.bc.simple.bean.core.asm.MethodVisitor;
import com.bc.simple.bean.core.asm.Opcodes;
import com.bc.simple.bean.core.asm.SpringAsmInfo;
import com.bc.simple.bean.core.asm.Type;

public class AnnotationMetaData extends ClassVisitor {


	public AnnotationMetaData() {
		super(SpringAsmInfo.ASM_VERSION);
	}

	protected final LinkedHashMap<String, LinkedHashMap<String, Object>> superAnnotationAttributes = new LinkedHashMap<>();

	protected final Set<String> annotationSet = new LinkedHashSet<>(4);

	protected final Set<String> annotationTypes = new LinkedHashSet<>(4);

	protected final Map<String, Set<String>> metaAnnotationMap = new LinkedHashMap<>(4);

	protected final Set<MethodMetaData> methodMetadataSet = new LinkedHashSet<>(4);



	protected String className = "";

	protected boolean isInterface;

	protected boolean isAnnotation;

	protected boolean isAbstract;

	protected boolean isFinal;

	protected String enclosingClassName;

	protected boolean independentInnerClass;

	protected String superClassName;

	protected String[] interfaces = new String[0];

	protected Set<String> memberClassNames = new LinkedHashSet<>(4);

	protected Resource resource;

	protected ClassLoader classLoader;

	protected final LinkedHashMap<String, LinkedHashMap<String, Object>> attributesMap = new LinkedHashMap<>(4);

	@Override
	public void visit(int version, int access, String name, String signature, String supername, String[] interfaces) {

		this.className = BeanUtils.convertResourcePathToClassName(name);
		this.isInterface = ((access & Opcodes.ACC_INTERFACE) != 0);
		this.isAnnotation = ((access & Opcodes.ACC_ANNOTATION) != 0);
		this.isAbstract = ((access & Opcodes.ACC_ABSTRACT) != 0);
		this.isFinal = ((access & Opcodes.ACC_FINAL) != 0);
		if (supername != null && !this.isInterface) {
			this.superClassName = BeanUtils.convertResourcePathToClassName(supername);
		}
		this.interfaces = new String[interfaces.length];
		for (int i = 0; i < interfaces.length; i++) {
			this.interfaces[i] = BeanUtils.convertResourcePathToClassName(interfaces[i]);
		}
	}

	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		this.enclosingClassName = BeanUtils.convertResourcePathToClassName(owner);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		if (outerName != null) {
			String fqName = BeanUtils.convertResourcePathToClassName(name);
			String fqOuterName = BeanUtils.convertResourcePathToClassName(outerName);
			if (this.className.equals(fqName)) {
				this.enclosingClassName = fqOuterName;
				this.independentInnerClass = ((access & Opcodes.ACC_STATIC) != 0);
			} else if (this.className.equals(fqOuterName)) {
				this.memberClassNames.add(fqName);
			}
		}
	}

	@Override
	public AnnotationVisitor visitAnnotation(final String desc, boolean visible) {
		String className = Type.getType(desc).getClassName();
		this.annotationSet.add(className);
		LinkedHashMap<String, Object> annotationAttributes = new LinkedHashMap<>();
		superAnnotationAttributes.put(className, annotationAttributes);
		annotationTypes.add(className);
		return new SimpleAnnotationVisitor(annotationAttributes, this.classLoader);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		if ((access & Opcodes.ACC_BRIDGE) != 0) {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
		MethodMetaData metaData = null;
		String[] parameterTypes = Arrays.asList(Type.getArgumentTypes(descriptor)).stream().map(Type::getClassName)
				.toArray(String[]::new);
		this.methodMetadataSet.add(metaData = new MethodMetaData(name, access, getClassName(),
				Type.getReturnType(descriptor).getClassName(), parameterTypes, this.classLoader, this));
		return metaData;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public boolean isInterface() {
		return isInterface;
	}

	public void setInterface(boolean isInterface) {
		this.isInterface = isInterface;
	}

	public boolean isAnnotation() {
		return isAnnotation;
	}

	public void setAnnotation(boolean isAnnotation) {
		this.isAnnotation = isAnnotation;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public void setAbstract(boolean isAbstract) {
		this.isAbstract = isAbstract;
	}

	public boolean isFinal() {
		return isFinal;
	}

	public void setFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}

	public String getEnclosingClassName() {
		return enclosingClassName;
	}

	public void setEnclosingClassName(String enclosingClassName) {
		this.enclosingClassName = enclosingClassName;
	}

	public boolean isIndependentInnerClass() {
		return independentInnerClass;
	}

	public void setIndependentInnerClass(boolean independentInnerClass) {
		this.independentInnerClass = independentInnerClass;
	}

	public String getSuperClassName() {
		return superClassName;
	}

	public void setSuperClassName(String superClassName) {
		this.superClassName = superClassName;
	}

	public String[] getInterfaces() {
		return interfaces;
	}

	public void setInterfaces(String[] interfaces) {
		this.interfaces = interfaces;
	}

	public Set<String> getMemberClassNames() {
		return memberClassNames;
	}

	public void setMemberClassNames(Set<String> memberClassNames) {
		this.memberClassNames = memberClassNames;
	}

	public Resource getResource() {
		return resource;
	}

	public void setResource(Resource resource) {
		this.resource = resource;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	public static class SimpleAnnotationVisitor extends AnnotationVisitor {

		private static final Log LOG = LogFactory.getLog(SimpleAnnotationVisitor.class);

		protected final LinkedHashMap<String, Object> annotationAttributes;
		protected ClassLoader classLoader;

		public SimpleAnnotationVisitor(LinkedHashMap<String, Object> annotationAttributes, ClassLoader classLoader) {
			super(SpringAsmInfo.ASM_VERSION);
			this.classLoader = classLoader;
			this.annotationAttributes = annotationAttributes;
		}

		@Override
		public void visit(String attributeName, Object attributeValue) {
			this.annotationAttributes.put(attributeName, attributeValue);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String attributeName, String asmTypeDescriptor) {
			String annotationType = Type.getType(asmTypeDescriptor).getClassName();
			LinkedHashMap<String, Object> nestedAttributes = new LinkedHashMap<>();
			nestedAttributes.put(Constant.ATTR_ANNOTATION_TYPE, annotationType);
			this.annotationAttributes.put(attributeName, nestedAttributes);
			return new SimpleAnnotationVisitor(nestedAttributes, this.classLoader);
		}

		@Override
		public AnnotationVisitor visitArray(String attributeName) {
			LinkedHashMap<String, Object> nestedAttributes = new LinkedHashMap<>();
			this.annotationAttributes.put(attributeName, nestedAttributes);
			return new SimpleAnnotationVisitor(nestedAttributes, this.classLoader);
		}

		@Override
		public void visitEnum(String attributeName, String asmTypeDescriptor, String attributeValue) {
			Object newValue = getEnumValue(asmTypeDescriptor, attributeValue);
			visit(attributeName, newValue);
		}

		protected Object getEnumValue(String asmTypeDescriptor, String attributeValue) {
			Object valueToUse = attributeValue;
			try {
				Class<?> enumType = BeanUtils.forName(Type.getType(asmTypeDescriptor).getClassName(), this.classLoader);
				Field enumConstant = BeanUtils.findField(enumType, attributeValue);
				if (enumConstant != null) {
					enumConstant.setAccessible(true);
					valueToUse = enumConstant.get(null);
				}
			} catch (Exception e) {
				LOG.info("Failed to classload enum type while reading annotation metadata", e);
			}
			return valueToUse;
		}

	}

	public boolean hasAnnotation(String annotationName) {
		return this.annotationSet.contains(annotationName);
	}

	public boolean hasMetaAnnotation(String metaAnnotationType) {
		Collection<Set<String>> allMetaTypes = this.metaAnnotationMap.values();
		for (Set<String> metaTypes : allMetaTypes) {
			if (metaTypes.contains(metaAnnotationType)) {
				return true;
			}
		}
		return false;
	}

	public boolean isAnnotated(String annotationName) {
		return (!AnnotationUtils.isInJavaLangAnnotationPackage(annotationName)
				&& this.superAnnotationAttributes.containsKey(annotationName));
	}

	public Set<String> getAnnotationTypes() {
		return annotationTypes;
	}

	public LinkedHashMap<String, Object> getAttributes(String type) {
		return superAnnotationAttributes.get(type);
	}

	public static class MethodMetaData extends MethodVisitor {

		private String factaryBeanName;

		protected final String methodName;

		protected final int access;

		protected final String declaringClassName;

		protected final String returnTypeName;

		protected final String[] parameterTypeName;

		protected final ClassLoader classLoader;

		protected final AnnotationMetaData parent;

		protected final LinkedHashMap<String, Object> attributesMap = new LinkedHashMap<>(4);

		public MethodMetaData(String methodName, int access, String declaringClassName, String returnTypeName,
				String[] parameterTypeName, ClassLoader classLoader, AnnotationMetaData parent) {
			super(SpringAsmInfo.ASM_VERSION);
			this.methodName = methodName;
			this.access = access;
			this.declaringClassName = declaringClassName;
			this.returnTypeName = returnTypeName;
			this.parameterTypeName = parameterTypeName;
			this.classLoader = classLoader;
			this.parent = parent;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String desc, boolean visible) {
			String className = Type.getType(desc).getClassName();
			LinkedHashMap<String, Object> annotationAttributes = new LinkedHashMap<>();
			attributesMap.put(className, annotationAttributes);
			return new SimpleAnnotationVisitor(annotationAttributes, this.classLoader);
		}

		public String getMethodName() {
			return this.methodName;
		}

		public boolean isAbstract() {
			return ((this.access & Opcodes.ACC_ABSTRACT) != 0);
		}

		public boolean isStatic() {
			return ((this.access & Opcodes.ACC_STATIC) != 0);
		}

		public boolean isFinal() {
			return ((this.access & Opcodes.ACC_FINAL) != 0);
		}

		public boolean isOverridable() {
			return (!isStatic() && !isFinal() && ((this.access & Opcodes.ACC_PRIVATE) == 0));
		}

		public boolean isAnnotated(String annotationName) {
			return this.attributesMap.containsKey(annotationName);
		}

		public Object getAnnotationAttributes(String annotationName) {
			return attributesMap.get(annotationName);
		}

		public String getDeclaringClassName() {
			return this.declaringClassName;
		}

		public String getReturnTypeName() {
			return this.returnTypeName;
		}

		public String[] getParameterTypeName() {
			return parameterTypeName;
		}

		public ClassLoader getClassLoader() {
			return classLoader;
		}

		public AnnotationMetaData getParent() {
			return parent;
		}

		public String getFactaryBeanName() {
			return factaryBeanName;
		}

		public void setFactaryBeanName(String factaryBeanName) {
			this.factaryBeanName = factaryBeanName;
		}

	}

	public boolean hasAnnotatedMethods(String annotationName) {
		for (MethodMetaData methodMetaData : this.methodMetadataSet) {
			if (methodMetaData.isAnnotated(annotationName)) {
				return true;
			}
		}
		return false;
	}

	public Set<MethodMetaData> getAnnotatedMethods(String annotationName) {
		Set<MethodMetaData> annotatedMethods = new LinkedHashSet<>(4);
		for (MethodMetaData methodMetaData : this.methodMetadataSet) {
			if (methodMetaData.isAnnotated(annotationName)) {
				annotatedMethods.add(methodMetaData);
			}
		}
		return annotatedMethods;
	}

}
