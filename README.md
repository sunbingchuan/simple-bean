#写simple-bean的起因
<p>simple-bean用我的空余时间断断续续的写了有两年左右（当然中间还弄其他事情）。
<p>最开始是为了学习阅读下spring的源码，学习中也萌生了自己做一个框架的想法。那做一个什么样的框架呢，
我觉得spring是一个很庞大的全家桶，像一把长剑，一枚大炮；我想自己做一个简单一点的bean管理框架，
像一把匕首，一支手枪，那简单易用就是我想要做的bean管理框架的基本格调。还有一点就是我也想把涉及的java
语言的一些更深层一点的技术造成易于理解又简单易用的轮子，想把做框架变得更简单一些，这样别人可以很不费劲的
用我的轮子来搭框架。

#历程
<p>第一版 是spring源码的学习过程：  
debug顺溜spring的处理过程，提炼出我想要简化的基本逻辑，然后整体理顺
<p>第二版 是开始融入自己设计思路的过程：  
做了很多有意思的意思的设计尝试，借鉴spring并通过自己的方式基本确定了诸如Prxoy、Expression、Config、
Resource、Handler、Processor、GenericType等技术模型。
<p>第三版 是深刻思考分析，彻底融入自己设计思路的过程：  
优化和革新第二版的各种技术模型，设计优化整体框架结构。各个扩展入口设计。

#整体设计思路
<p>对象的创建方法有两种，分别是方法和构造函数，当然方法里也是要调用构造函数的，
所以都是由构造函数产生的。不管你是用映射还是直接调用，终归都是一样的。simple-bean
也提供给用户两种建bean方法，一种是by Constructor，一种是 by Method，具体api见文档。simple-bean
中主要有Loader、Parser、Handler、Processor、Element、ElementInstaller、Helper几个模块。
Loader是配置加载器，主要作用是加载配置文件；Parser是配置解析器，主要作用是解析配置文件构建Builder；
Handler是配置元素处理器，处理像scan、aspect的扩展的配置元素；Processor是配置元素控制器，对应相应的
配置元素对simple-bean进行控制；Element是Builder控制元素，主要作用是控制bean的各种属性；ElementInstaller
是Element对应的安装器；Helper则是囊括了各种辅助工具的助手系统。

#配置系统
simple-bean的配置系统比较简单，可能缺乏特殊字符集、编码格式等的特殊处理，因为我这里是本着极简的原则，不太重要
的功能处理就略去或者留下空间给诸君实现扩展了。一个配置实体类Node，支持配置节点属性和子节点。配置文件格式详见api
文档。配置文件加载器Loader原理也很有意思，感兴趣的诸君可以阅读测试源码。配置解析器将Node配置转化为Builder或
Builder配置。

#核心功能系统

##Builder
Builder中存有bean构建的基本元素。
##SimpleContext
SimpleContext simpe-bean的整体环境和调用入口
##Procedure 
bean创建基本程序，包含BuildProcedure、CompoundProcedure、CreateProcedure、ElementProcedure、
PopulateProcedure、ProxyProcedure几块，分别处理bean组建、依赖组装、创建、element元素安装、属性配置、
代理几个功能。bean组建涉及逻辑众多，并且相互之间并无更深层次关系，就采用这种方式将其分组。
##Handler
Handler是配置元素处理器，处理像scan、aspect的扩展的配置元素
##Processor
是配置元素控制器，对应相应的配置元素对simple-bean进行控制
##Element
Element是Builder控制元素，主要作用是控制bean的各种属性
##ElementInstaller
ElementInstaller是Element对应的安装器
##Helper

###field：字段工具  

###generic：泛型工具  

###method：方法工具  

###class：类工具 
 
###common：通用工具  

###expression：包含数学表达式解析工具和参数表达式解析工具。

参数表达式工具用于解析表达式中的参数，数学表达式用于解析表达式中的一些运算。  

###proxy：字节码工具。

三种实现方式：
  
WrapProxy  
创建一个继承原来类的新类并持有原类的实例，将所有调用到原来类的方法指向所持有的实例，并在调用前执行切面方法。
特点：只支持public方法的切面，原类必须有一个无参public默认构造函数

InstanceProxy  
创建一个继承原来类的新类并将原来类的所有方法改名转存，将所有调用到原来类的方法指向原方法，并在调用前执行切面方法。
特点：只支持可以被继承或实现的方法的切面，原类必须有一个无参public默认构造函数，并且原类正常是要通过此构造方法
创建。

WholeProxy  
创建一个和原来类不同的新类并将原来类的所有方法改名转存，将所有调用到原来类的方法指向原方法，并在调用前执行切面方法。
特点：支持所有方法的切面，但是新类完全不同于原类，函数调用需通过映射调用。

#web扩展
<p>simple-bean自带的web功能比较基础，有很大的扩展和实现空间，自然以后有机会也可能会扩展一下，比如像spring-web能提供的
参数实体映射之类的。我这里只是给诸君留足扩展的空间，有待更多有识之士的参与。


#联系方式

1254598551@qq.com
电话/微信：15038156216

有什么问题和想法，欢迎与我联系。