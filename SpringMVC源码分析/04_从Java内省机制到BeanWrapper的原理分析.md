## Java内省机制
### 描述
```
之所以在SpringMVC源码分析中穿插这么一篇关于Java知识的文章, 是因为我们即将分析的HandlerAdapter底层对数据
的绑定就是基于Java内省机制的, 在之前Spring源码分析中, 笔者也或多或少提到过, 但是当时没有细讲, 到了
SpringMVC中就不的不提了, 不然有些代码可能会让读者感到迷惑

在这里笔者先以我自己的理解来说下什么是Java的内省机制, Java内省机制是对反射的一种封装, 是Java提供给开发
者对一个对象属性的查看和操作、方法的操作以及对象的描述等, 或许这样说比较抽象, 之后我们会举一些例子来说明

当然, 这个机制在对于属性操作的时候是有一定限制的, 这个我们留个悬念, 文章之后会特别对这一块说明

Java的内省机制, 不可避免的会涉及到几个类: Introspector、PropertyDescriptor、PropertyEditor以及
BeanInfo, 接下来我们详细描述下这几个类, 并用一些例子来演示, 这样大家就能够更加清晰的理解Java的内省机制
了
```

### PropertyDescriptor属性描述器
```java
先以一个例子来入门吧:

public class User {
    private String name;

    private String aName;

    getter / setter / toString
}

public static void main (String[] args) throws Exception {
    User user = new User();
    System.out.println( user );
    PropertyDescriptor propertyDescriptor = new PropertyDescriptor( "name", User.class );
    Method readMethod = propertyDescriptor.getReadMethod();
    System.out.println( readMethod.invoke( user ) );
    Method writeMethod = propertyDescriptor.getWriteMethod();
    writeMethod.invoke( user, "hello" );
    System.out.println( user );
}

输出结果:
    User{name='null', aName='null'}
    null
    User{name='hello', aName='null'}

分析:
    可以看到, 我们先创建了一个对象, 然后输出的结果中里面的属性都是null, 这个就不用解释了吧..........
    
    然后我们创建了一个PropertyDescriptor属性描述器, 传入了属性名称和所在的类, 这时候大家应该就可能会
    想到, 这个PropertyDescriptor中应该是有两个属性, 可能分别叫targetClass, propertyName这样的, 从而
    保存了这两个传入的值, 好的, 接着往下看......

    getReadMethod获取读方法, 什么是读方法呢?就是getXXXX, 那我们描述的属性是name, 获取的自然就是getName
    了, 返回值是一个Method对象, 通过反射调用, 传入user对象, 就能获取到该对象中的name属性

    getWriteMethod获取写方法, 什么是写方法呢?就是setXXXX, 那我们描述的属性是name, 获取的自然就是setName
    了, 返回值是一个Method对象, 通过反射调用, 传入user对象和期望设置的值, 再次输出user对象的时候, 会发
    现name已经被设置好值了

    上面几步操作经过描述, 大家应该有点感觉了是吧.....没错, PropertyDescriptor属性描述器, 就是对属性反
    射的一种封装, 方便我们直接操作属性, 当我们利用构造方法new的时候, 内部就会帮我们找出这个属性的get和
    set方法, 分别作为这个属性的读方法和写方法, 我们通过PropertyDescriptor对属性的操作, 其实就是利用反
    射对其get和set方法的操作而已, 但是其内部实现还是有点意思的, 利用软引用和弱引用来保存方法、以及Class
    对象的引用, 这个软引用和弱引用, 笔者之后也会把之前写的关于这个Java四大引用类型的文章也上传到掘金, 
    大家要是之前没了解过四大引用类型的话, 可以理解为这是为了防止内存泄露的一种操作就好了, 或者直接理解为
    就是一个直接引用就可以了........

接下来再来看一个例子, 我们重用之前的User类:

public static void main (String[] args) throws Exception{
    PropertyDescriptor propertyDescriptor = new PropertyDescriptor( "aName", User.class );
    Method readMethod = propertyDescriptor.getReadMethod();
    Method writeMethod = propertyDescriptor.getWriteMethod();
}

分析:
    可以看到, 此时我们要创建的属性描述器是User这个类中的aName属性, 这个aName属性的get和set方法是这样的
        public String getaName() {
            return aName;
        }

        public void setaName(String aName) {
            this.aName = aName;
        }

    这个是idea自动生成的, 或者编辑器自动生成的get/set就是这样的, 但是我们会发现, 当执行上述的main方法
    的时候, 竟然报错了, 报错信息: java.beans.IntrospectionException: Method not found: isAName

    因为Java内省机制是有一定的规范的, 查找一个属性的get/set方法, 需要将该属性的第一个字母变成大写, 然后
    前面拼接上get/set前缀, 由于我们编辑器生成的get/set方法在属性前两个字母一大一小的情况下, 不会改变其
    set和get方法的前两个字母, 所以导致了报错, 这一点需要注意, 笔者之前可是在项目中吃过一次亏的!!!因为
    SpringMVC的数据绑定就是基于Java的内省机制来完成的, 那么当笔者的一个属性开头两个字母是一大一小的时候
    死活绑定不到对应的数据.......然而在内部处理的时候, 异常已经被吞掉了....
```

### PropertyEditor属性编辑器
```java
PropertyEditor也是Java提供的一种对属性的扩展了, 用于类型的转换, 比如我们设置属性的时候, 期望将String类
型转为其他类型后再设置到对象中, 就需要利用到这个属性编辑器, Java中PropertyEditor接口有一个直接子类
PropertyEditorSupport, 该类基本是这样定义的(伪代码):
public class PropertyEditorSupport implements PropertyEditor {
    private Object value;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setAsText(String text) throws java.lang.IllegalArgumentException {
        if (value instanceof String) {
            setValue(text);
            return;
        }
        throw new java.lang.IllegalArgumentException(text);
    }

    public String getAsText() {
        return (this.value != null)
                ? this.value.toString()
                : null;
    }
}


分析:
    上面这个代码是PropertyEditorSupport的一小部分, 可以看到其实就是一个简单的类而已, 里面有一个Object
    类型的属性value, 提供了get/set方法, 与此同时, 提供了setAsText和getAsText方法, 通常我们需要继承这
    个类来完成扩展, 比如说这个value是一个Date类型, 我们期望设置的时候提供的是字符串, 那么就要继承这个类
    并重写其setAsText方法, 如下:

    public class DatePropertyEditor extends PropertyEditorSupport {
        @Override
        public String getAsText() {
            Date value = (Date) getValue();
            DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
            return dateFormat.format( value );
        }

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
            try {
                Date date = dateFormat.parse( text );
                setValue( date );
            } catch (ParseException e) {}
        }
    }

我们先来写个测试类玩玩吧, 这时候先不去分析PropertyEditorSupport中的其他功能, 先以最简单的开始:

public static void main(String[] args) throws Exception{
    DatePropertyEditor datePropertyEditor = new DatePropertyEditor();
    datePropertyEditor.setAsText( "2020-03-06 15:33:33" );
    Date value = (Date) datePropertyEditor.getValue();
    System.out.println( value );
}

创建了一个自定义的日期属性编辑器, 结合上面该类的代码, 当调用setAsText的时候, 传入了一个字符串日期, 那么
就会被解析成一个Date类型, 最后保存到value中, 从而getValue返回的类型就是Date类型, 这个应该很容易理解, 
那么到这里, 我们算是入门了, 简单的体会了下其功能, 该类中还有一个source属性和listeners属性, 这个我们就
简单介绍下, source, 通常是我们需要操作的对象, listeners就是监听器, 在setValue调用时, 除了直接赋值
this.value = value外, 还会触发所有的监听器, 调用监听器的方法, 监听器方法中会传入一个事件对象, 事件对象
中保存了该source, 也就是说, PropertyEditorSupport中有一个Object类型的source属性, 同时有一个监听器对象
集合, 这个source属性可以在监听器对象方法被调用的时候获取到(存在于事件中, 调用监听器方法会放入一个事件对
象, 构造该事件对象会传入source), 由于暂时没有用到这两个, 所以先不进行扩展, 没有应用场景, 扩展也没多大意
义
```


### BeanInfo
```java
BeanInfo是一个接口, 有一个子类GenericBeanInfo, 通常情况下创建的是GenericBeanInfo, 其是Introspector
中的一个包访问权下的类, 我们先来简单看看其结构吧:

class GenericBeanInfo extends SimpleBeanInfo {
    private BeanDescriptor beanDescriptor;
    private PropertyDescriptor[] properties;
    private MethodDescriptor[] methods;
}

分析:
    只列举出了几个简单的属性, 但是够用了, BeanDescriptor就是持有类Class对象的引用而已, 
    PropertyDescriptor中就是这个类的所有属性描述器, MethodDescriptor自然就所有的方法描述器了, 跟属性
    描述器是类似的, 都是为了方便反射调用的, 那么BeanInfo的作用就出来了, 就是对一个类所有的属性、方法等
    反射操作封装后的集合体, 那么它如何得到呢?这就用到了Introspector这个类了, 如下:

    public static void main(String[] args) throws Exception {
        BeanInfo beanInfo = Introspector.getBeanInfo( Customer.class );
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        MethodDescriptor[] methodDescriptors = beanInfo.getMethodDescriptors();
        BeanDescriptor beanDescriptor = beanInfo.getBeanDescriptor();
    }

    那么到此为止, 我们要讲解的内省机制的关系就出来了, 通过Introspector获取一个类的BeanInfo, 通过
    BeanInfo能够获取属性描述器、方法描述器、类Class对象, 利用获取到的属性描述器, 我们能够往一个该类实例
    中放入数据
```

### CachedIntrospectionResults
```java
--------------看到下面代码别慌......请直接看着下面我的文字分析来看代码---------------
public class CachedIntrospectionResults {
    static final ConcurrentMap<Class<?>, CachedIntrospectionResults> strongClassCache =
        new ConcurrentHashMap<>(64);

    static final ConcurrentMap<Class<?>, CachedIntrospectionResults> softClassCache =
        new ConcurrentReferenceHashMap<>(64);

    private final BeanInfo beanInfo;

	private final Map<String, PropertyDescriptor> propertyDescriptorCache;
    
    static CachedIntrospectionResults forClass(Class<?> beanClass) throws BeansException {
		CachedIntrospectionResults results = strongClassCache.get(beanClass);
		if (results != null) {
			return results;
		}
		results = softClassCache.get(beanClass);
		if (results != null) {
			return results;
		}

		results = new CachedIntrospectionResults(beanClass);
		ConcurrentMap<Class<?>, CachedIntrospectionResults> classCacheToUse;

		if (ClassUtils.isCacheSafe(beanClass, CachedIntrospectionResults.class.getClassLoader()) ||
				isClassLoaderAccepted(beanClass.getClassLoader())) {
			classCacheToUse = strongClassCache;
		}
		else {
			classCacheToUse = softClassCache;
		}

		CachedIntrospectionResults existing = classCacheToUse.putIfAbsent(beanClass, results);
		return (existing != null ? existing : results);
	}
}

分析:
    CachedIntrospectionResults这个类是Spring提供的对类的内省机制使用的工具类, 不同于Introspector之处
    在于, 该类提供类内省机制时的数据缓存, 即内省获得的PropertyDescriptor这些数据进行了缓存

    首先我们来看看最上面的两个静态方法, 全局变量保存了两个Map, key是class对象, value是
    CachedIntrospectionResults对象, 大家应该可以想到, 这应该是类似于利用Map实现的单例吧, 提供了缓存的
    功能, 之后可以通过static方法直接访问

    再来看看其两个属性, 一个是BeanInfo, 一个是propertyDescriptorCache, 前者就不用笔者描述了吧, 就是
    内省机制中对一个类的功能的封装, 前面已经专门对这个类进行说明了, 后者是属性名到属性描述器的映射Map,
    这个应该也不用详细解释了

    CachedIntrospectionResults类实例, 封装了一个类通过内省机制获得的BeanInfo和属性描述器映射, 全局
    的static变量中保存了所有要操作的类的CachedIntrospectionResults类实例缓存, 采用强引用和软引用是为
    了防止内存泄露用的

    再来看看forClass, 表示根据Class对象获取该类的CachedIntrospectionResults类实例, 可以看到, 先从强
    引用缓存中获取, 没拿到则从软引用中获取, 这里大家不熟悉四大引用类型的话, 可以直接认为是从Map中根据
    Class对象获取对应的CachedIntrospectionResults类实例, 如果没有获取到, 则创建一个并放到Map中去

小小的总结:
    CachedIntrospectionResults类对象通过全局变量Map提供了对内省机制获得的BeanInfo信息的缓存, 从而可以
    方便我们通过static方法获取对应类的内省信息
```

### BeanWrapper
```java
public class BeanWrapperImpl extends AbstractNestablePropertyAccessor implements BeanWrapper {
	@Nullable
	private CachedIntrospectionResults cachedIntrospectionResults;
}

分析: 
    可以看到, BeanWrapper实例中内置了一个CachedIntrospectionResults, 之前分析DispathcherSerlvet的
    初始化流程的时候, 小小的说明了下BeanWrapper的作用, 但是没有分析其怎么实现属性的设置的, 这个时候就
    有必要说一下了, 因为其跟我们SpringMVC数据绑定有点关系

    那既然内部存储了一个CachedIntrospectionResults实例, 大家应该很容易的想到, 内部就是通过该实例来获取
    对应的属性描述器, 然后获取读方法和写方法来设置属性的吗?确实如此, 接下来我们看看setPropertyValue这个
    方法吧, 有很多个重载方法, 我们以直接通过属性名和属性值来设置的这个方法为例子

public void setPropertyValue(String propertyName, @Nullable Object value) {
    AbstractNestablePropertyAccessor nestedPa=getPropertyAccessorForPropertyPath(propertyName);
    
    PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
    nestedPa.setPropertyValue(tokens, new PropertyValue(propertyName, value));
}

简单解析下, AbstractNestablePropertyAccessor提供了嵌套属性设置的功能, 比如一个实体类中还有另一个实体
类, 这种情况下也是能设置成功的, 不用管这些代码什么意思, 下面跟着代码走, 可以看到一个豁然开朗的东西...
nestedPa.setPropertyValue中调用了processLocalProperty(tokens, pv), processLocalProperty中获取了一
个PropertyHandler, 就是通过这个PropertyHandler来完成属性的设置的, 接下来我们看看这个PropertyHandler
是什么

protected BeanPropertyHandler getLocalPropertyHandler(String propertyName) {
    PropertyDescriptor pd 
        = getCachedIntrospectionResults().getPropertyDescriptor(propertyName);
    if (pd != null) {
        return new BeanPropertyHandler(pd);
    }
    return null;
}

是不是觉得豁然开朗, 原来BeanWrapper中, 最终就是通过PropertyDescriptor来完成属性的设置的！！！！
```

### 总结
```
我们从Java内省机制进行出发, 引出了PropertyDescriptor、PropertyEditor(类型转换用)、Introspector、
BeanInfo这四个Java内省机制中的核心类, 同时也捋清楚了内省机制原来就是对反射的封装, 进而引出了
CachedIntrospectionResults类, 该类是Spring对内省机制中获得的数据的缓存, 进而引出了BeanWrapper的实现
原理, 里面内置了一个CachedIntrospectionResults对象, 对属性的操作最终就会变成该对象中的
PropertyDescriptor的操作, 需要说明的是, CachedIntrospectionResults还能提供嵌套的属性设置, 这个需要
注意, 其实Spring对Java的内省机制的封装还有很多很多可以说的, 但是如果仅仅是为了读懂SpringMVC的源码的话,
上面这些内容就够了, 或许在不久的将来, 笔者会专门写一个系列来描述Spring对Java内省机制的封装, 大家可以
期待期待哈.....
```