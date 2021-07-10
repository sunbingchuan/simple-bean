# simple-bean 基本结构
<pre>
+-------------------------------------------------------+--------------------+
|                                                       |                    |
|                                                       |    simple-bean     |
|                                                       |      primary       |
|                            +----------------+         |     structure      |
|                            |                |         |                    |
|                            |     Loader     |         +--------------------+
|                            |                |                              |
|   +---------------+        +-------X--------+                              |
|   |               |                X                +                      |
|   |    Handler    |X X X X X X X X X                |                      |
|   |               |                X                |                      |
|   +---------------+                X                |   Helper             |
|                                    X                |                      |
|                                    X                |                      |
|                                    X                +----------------+     |
|   +---------------+        +----------------+                              |
|   |               |        |                |                              |
|   | Configuration +------> |     Parser     |                              |
|   |               |        |                |                              |
|   +---------------+        +-------+--------+       +----------------+     |
|                                    |                |                |     |
|                                    |                |    Element     |     |
|                                    |                |                |     |
|                                    |                +-------X--------+     |
|                                    |                        X              |
|   +---------------+        +-------v--------+               X              |
|   |               |        |                |               X              |
|   |    Builder     X X X X X    Context      X X X X X X X XX              |
|   |               |        |                |               X              |
|   +------X--------+        +-------+--------+               X              |
|          X                         |                        X              |
|   +------X--------+                |                +-------X---------+    |
|   |               |                |                |                 |    |
|   |   Procedure   |                |                |    Processor    |    |
|   |               |                |                |                 |    |
|   +---------------+        +-------v--------+       +-----------------+    |
|                            |                |                              |
|                            |      Bean      |                              |
|                            |                |                              |
|                            +----------------+                              |
|                                                                            |
|                                                                            |
+----------------------------------------------------------------------------+
</pre>

# sp文件基本配置  
sp 文件时simple-bean 框架自带的简单配置形式，
主要逻辑是用'\t'符号来标识层级关系，用':'表示属性。 
 
支持将重复a.b.c的节点或属性名称简写为..c，例如：  
<pre>	
	... 
aa
	bb
		cc(等效于aa.bb.cc)
			a.b.c:xx  
			..d:yy(等效于a.b.d:yy)  
..dd(等效于aa.bb.dd)
	xx:xx
.ee(等效于aa.ee)
	xx:xx
	...
</pre>	

对于简写默认不会重新建节点，如果想建新节点可以在想新建的节点对应
的位置添加'$'符号，例如：
<pre>
aa.bb.dd
	xx:yy
..cc
	xx:yy
等于
aa.bb
	dd
		xx:yy
	cc
		xx:yy
而
aa.bb.dd
	xx:yy
.$.cc
	xx:yy
则等于
aa
	bb
		dd
			xx:yy
	bb	
		cc
			xx:yy
</pre>

### Context基本属性配置

+ auto-init  
<p>是否自动生成单例 true/false ，默认false

+ default-autowired-fields
<p>匹配表达式，用于匹配builder  的id(即builderName)，
如果匹配，则将该builder的autowired-field 属性默认
设置为true。
<p>如果该builder自己有单独设置autowired-field属性，
则覆盖掉该全局设置。

+ default-autowired-executables  
<p>同上对应builder的autowired-executable属性。

### 节点配置
+ builder 

	+ name 
	<p>builder 名字。
	
	+ alias
	<p>builder 别名，可以为多个，多个别名以英文逗号、分号或空格分隔。
	
	+ class
	<p>builder所要创建的bean的class,即builder的builderClass 属性。
	
	+ scope
	<p>bean 创建方式 singleton-单例/prototype-多例 
	
	+ auto-init
	<p>context 初始化时自动初始化单例 true/false
	
	+ depends-on
	<p>依赖单例，必须在builder创建bean之前创建。
	考虑到单例创建的创建顺序依赖的情况存在。多个依赖以英文逗号分隔。
	（此属性中的多例无效）
	
	+ autowired-field
	<p>自动装配field
	
	+ autowired-executable
	<p>proxy模式建bean中自动注入方法参数（只针对建bean方法，对其他调用方法无效）
	
	+ order
	<p>builder优先级，int类型，默认0，值越大优先级越高。
	优先使用高优先级的builder来建bean。
	
	+ description
	<p>builder 描述，无实际使用意义，类似注释。
	
	+ method-name
	<p>方法建bean模式（MethodBuilder）中指定建bean方法的名字
	
	+ owner-name
	<p>方法建bean模式中获取bean的方法的调用者
	
	+ owner-class-name
	<p>方法建bean模式中获取bean的方法的调用者类名
	
	+ build-parameter-types
	<p>构造函数建bean模式中配置构造函数的参数类名 或者 
	方法建bean模式中建bean方法的参数类名（英文逗号分隔，用于指定方法）

	+ build-parameter
	<p>构造函数建bean模式中配置构造函数的参数 或者 方法建bean模式中建bean方法的参数
		+ index
		<p>指定参数序列
		+ 基础元素配置
	+ executable-parameter
	<p>proxy建bean模式中会优先注入配置的方法参数，
	其次使用自动装配的参数（autowired-executable设置为true时）
		+ index
		+ executable-name
		<p>指定参数序列
		+ 基础元素配置
	+ field
	<p>配置builder所建bean的字段
		+ 基础元素配置

+ alias
	配置别名
	+ name
	原名
	+ alias
	别名，多个别名以英文逗号、分号或空格分隔。
+ import
	<p>引用其他配置文件
	+ resource
	<p>相对或绝对的配置文件路径。
	<p>支持三类路径
		1. '.' 开头的，如 ./import.sp，表示当前解析配置文件的相对路径；
		2. 绝对路径（可以是文件绝对路径或URL格式的路径）
		3. 加载路径的相对路径，
		如：basic/import.sp ,就指代加载根路径下的basic文件夹下的import.sp 文件。
+ scan
	注解扫描模块
	+ base-package
	<p>要扫描的包路径（支持 表达式匹配规则 和 matchName 类型的特殊匹配规则

+ aop
	+ pointcut
	<p>方法匹配表达式
	+ ref
	<p>创建 handler 的builder的builderName 
	+ type
	<p>创建handler 的builder的builderClass类名。
	或者具有public 无参构造函数的class的类名（会直接调用Class.newInstance()创建）。
+ 基础元素配置
	+ type
	<p>类型class类名
	+ name
	<p>类型名字（由使用场景不同决定具体意义）
	+ ref
	<p>引用bean的builder的builderName（即所配置的builder id）
	+ val
	<p>String类型的值
	+ 复合元素

+ 复合元素
	+ map
		+ pair
		<p>标识键值对
			+ key
				+ 基础元素配置
			+ value
				+ 基础元素配置
	+ array
		+ ele
		<p>标识单个元素
			+ 基础元素配置
	+ list
		+ ele
			+ 基础元素配置
	+ set
		+ ele
			+ 基础元素配置



# 注解

# 部分工具类 helper简介
## PatternHelper
### 表达式匹配规则
<pre>
'*' 等效于 正则'.*', '?' 等效于 正则'.'
例如：abc* 可以匹配 abcde，abc?e 可以匹配abcde
对于matchPath和matchStart 
"**" 统配多层分割符号 '/'，
例如：abc/**/* 匹配  abc/d/f/d/xx.xx
对于matchName
'..' 统配多层分割符号 '.'
例如：abc..xx 匹配  abc.d.xx.xx
</pre>

## ClassHelper

## ExpressionHelper

## MathExpressionHelper

## AnnotationHelper、AnnotationAttributeHelper

## PatternHelper

## GenericTypeHelper

## ProxyHelper

## ResourceHelper

以上helper工具参考使用实例

<https://github.com/sunbingchuan/simple-bean-example>

