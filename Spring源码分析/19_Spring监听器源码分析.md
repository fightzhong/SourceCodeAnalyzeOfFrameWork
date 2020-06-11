### 引入
```
本章节主要是对Spring监听器的源码进行分析, 为后面SpringMVC源码打下基础, 因为在SpringMVC中, 
DisptcherServlet的初始化就是利用了Spring的监听器来完成的, 在Spring中, 监听器其实就是对观察者模式
的一种应用, 观察者模式中本文不会进行深入分析, 相信有兴趣研究源码的同学对设计模式应该都是有深入学习
的, 这也是读框架源码的基础
```

### 从整体来看Spring监听器的调用流程
```
观察者模式最为核心的就是观察者和主题对象, 主题对象持有多个观察者, 主题对象在收到合适的信号时就会通
知观察者, 在Spring中, 我们所谓的Listener就是观察者, 这些观察者会被集中放在一个Set集合中, 不同的
观察者可能接收不同的信号, Spring会主动调用publish方法来触发不同的事件, 这个方法有一个参数, 就是事
件对象, 不同的事件会通知不同的观察者, 当Spring触发了事件后, 其实可以直接对监听器进行遍历, 找到合适
的观察者进行调用了, 但是Spring抽出了一个ApplicationEventMulticaster对象来实现单一职责, 即Spring
自身不会去查找监听器, 而是让这个事件多播器去查找并调用

Spring调用监听器的主要流程(不含缓存等):
  <1> Spring主动调用publish方法来触发事件, 该方法有一个参数, 表示事件, 触发不同的事件就创建不同的
      事件对象就好了, 比如: publishEvent(new ContextRefreshedEvent()), 表示触发上下文刷新被刷新
      事件, 这个方法在Spring容器初始化完成后会被调用(之后我们会分析这个源码)
  <2> 委派给ApplicationEventMulticaster事件多播器对象来通知不同的观察者(监听器), 在这个对象中,
      会维护着一个集合Set, 集合中就是我们注册到Spring中的所有监听器, 可以认为, 事件多播器对象就是
      观察者模式中的主题对象
  <3> 事件多播器对象会扫描所有的监听器, 查找出适合当前事件(比如ContextRefreshedEvent)的监听器,
      然后调用其onApplicationEvent方法
```

> 接下来我们开始分析Spring监听器的源代码

### 初始化主题对象ApplicationEventMulticaster事件多播器
```java
protected void initApplicationEventMulticaster() {
  ConfigurableListableBeanFactory beanFactory = getBeanFactory();
  if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
    this.applicationEventMulticaster =
        beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
  }
  else {
    this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
    beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
  }
}

APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster"

分析:
  首先我们要知道这个方法的调用时机, 在Spring上下文的构造方法中, refresh方法几乎完成了整个Spring
  的初始化过程, 在refresh方法中, 一共有12方法被调用, 而初始化主题对象的这个方法就是其中一个

  代码很简单, Spring会先从容器中查看一下是否已经存在了主题对象, 如果不存在, 就创建一个主题对象, 之
  后注册到Spring的单例池中, 可以看到, 从容器中拿到的主题对象或者此时创建的主题对象都会被赋值给
  this.applicationEventMulticaster, 而this则时ApplicationContext, 即我们所谓的上下文, 由此可以
  得出, Spring内置了一个观察者模式, 当Spring主动触发某些事件的时候, 就是利用这个主题对象去通知观察
  者的

  主题对象的创建很简单, 将当前bean工厂作为参数传进构造方法就好了, 之后这个bean工厂就会成为主题对象
  中的一个属性
```

### 注册监听器对象
#### 第一次注册(注册缓存在Spring上下文环境中的监听器到主题对象中)
```java
protected void registerListeners() {
  for (ApplicationListener<?> listener : getApplicationListeners()) {
    getApplicationEventMulticaster().addApplicationListener(listener);
  }

  String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
  for (String listenerBeanName : listenerBeanNames) {
    getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
  }

  Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
  this.earlyApplicationEvents = null;
  if (earlyEventsToProcess != null) {
    for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
      getApplicationEventMulticaster().multicastEvent(earlyEvent);
    }
  }
}

分析:
  首先我们要知道这个方法的调用时机, 同上面注册主题对象一样, 该方法也是refresh中调用的12个方法之一

  Spring会调用getApplicationListeners方法, 获取到Spring上下文中的属性applicationListeners,
  这个属性是一个Set, 存储的是一个集合, 在整个registerListeners方法调用之前, 这个集合中通常是没有
  监听器的, 可以利用idea的工具发现, 在整个Spring源码中, registerListeners方法之前都没有往这个集
  合中添加果监听器, 所以这个for循环不会进入

  通过getBeanNamesForType获取到整个容器中所有的ApplicationListener类实例的beanName, 在
  registerListeners方法调用时, Spring还没开始bean对象的实例化过程, 遍历所有的beanName, 将它注册
  到主题对象中

  观察者模式, 主题对象会维护一个Set, 保存了所有的监听器, 但是Spring不是这样, Spring的主题对象
  ApplicationEventMulticaster中维持了一个ListenerRetriever对象, 而在ListenerRetriever对象中
  才是维护所有的监听器对象, 需要注意的是, Spring在该对象中维护了两个Set, 一个用来保存监听器对象,
  一个用来保存监听器对象对应的beanName

  所以查看上面两个for循环, 一个是往Set<ApplicationListener>中放入监听器, 一个是往Set<String>中
  放入监听器对象的beanName

  再往后, Spring会获取到之前注册的事件, 然后触发这些事件, 这里其实算是一个扩展了, 因为监听器对象还
  没被创建, Spring也允许我们提前注册一些事件到earlyApplicationEvents中, 当监听器对象创建的时候,
  就可以先触发这些事件了

小小的总结:
  在该步骤中, Spring主要将存在于上下文对象中的监听器注册到主题对象中(因为之前还没有主题对象, 所以
  Spring利用一个熟悉变量将已经获得的监听器保存起来, 到了真正注册监听器的时候才注册到主题对象中),
  通常情况下这一步是没有监听器的, 即这个方法没有注册我们通过@Component等注解注册的监听器, 但是此时
  Spring将我们通过注解注册的监听器的beanName放入了主题对象中, 下面是Spring上下文、主题对象、监听器
  对象的简单表示

class AnnotationConfigApplicationContext{
  private ApplicationEventMulticaster applicationEventMulticaster;
}

class ApplicationEventMulticaster {
  private ListenerRetriever defaultRetriever = new ListenerRetriever();
}

class ListenerRetriever {
  public Set<ApplicationListener<?>> applicationListeners;

  public Set<String> applicationListenerBeans;
}
```

#### 第二次注册(注册程序员通过各种注入方式注入到容器中监听器到主题对象中)
##### 代码一: applyMergedBeanDefinitionPostProcessors
```java
对于applyMergedBeanDefinitionPostProcessors这个方法来说, 在doCreateBean方法中被调用, 之前笔者
的文章也详细的描述了这个方法的主要完成的功能了, 在这个方法中, 调用了ApplicationListenerDetector
后置处理器的postProcessMergedBeanDefinition方法

public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, 
                              Class<?> beanType, String beanName) {
  this.singletonNames.put(beanName, beanDefinition.isSingleton());
}

分析:
  this指的是ApplicationListenerDetector实例对象, 这个后置处理器即完成了一个功能, 当一个bean是单
  例的时候, 就将其放到singletonNames这个Map中, 后面我们注册监听器到主题对象的时候就会从这个map中
  取监听器bean
```

##### 代码二: initializeBean方法
```java
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
  invokeAwareMethods(beanName, bean);

  Object wrappedBean = bean;
  if (mbd == null || !mbd.isSynthetic()) {
    wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
  }

  invokeInitMethods(beanName, wrappedBean, mbd);
  if (mbd == null || !mbd.isSynthetic()) {
    wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
  }

  return wrappedBean;
}

分析:
  如果有看过笔者之前文章的同学, 看到这段代码会很熟悉, 在Spring创建完一个Bean之后, 就会开始填充属性,
  属性填充完成后, 就会调用initializeBean方法, 在这个方法中, 调用了各种Aware接口以及生命周期方法,
  同时在这里Spring完成了AOP对象的创建, 简单的回顾了一下这个方法的作用....

  在applyBeanPostProcessorsAfterInitialization方法的调用中, 主要是调用各种后置处理器的afterXXX
  方法, AOP也是在这里完成的, 与此同时, 调用了一个ApplicationListenerDetector的后置处理器, 而程序
  员注入的监听器便是在这个时候被注入到主题对象中的, 接下来我们便是要分析这个后置处理器如何将程序员
  注册的监听器注入主题对象的

  需要注意的是, 此时监听器对象是已经被创建, 并放到容器中了, 并且, 如果这个监听器是一个代理对象, 比
  如这个对象被切面切中了呢?而ApplicationListenerDetector这个后置处理器是在处理AOP的后置处理器调用
  之后才被调用的, 所以我们可以认为, 当我们注入这个监听器到主题对象中前, 我们拿到的是最终在容器中的
  主题对象(有可能是代理对象)
```

##### 代码三: ApplicationListenerDetector
```java
public Object postProcessAfterInitialization(Object bean, String beanName) {
  if (bean instanceof ApplicationListener) {
    Boolean flag = this.singletonNames.get(beanName);
    if (Boolean.TRUE.equals(flag)) {
      this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
    }
    else if (Boolean.FALSE.equals(flag)) {
      this.singletonNames.remove(beanName);
    }
  }
  return bean;
}

分析:
  可以看到, 当一个bean是ApplicationListener的实例的时候, 才会进入判断, 否则就直接返回了

  Spring利用beanName从singletonNames判断是否存在该监听器, 如果存在才去注册, 一个监听器是单例对象
  的情况下, 在之前的applyMergedBeanDefinitionPostProcessors方法中就会将beanName放到map中

  满足条件, 则调用Spring上下文环境applicationContext的addApplicationListener方法开始注册监听器
  到主题对象, 可以看到, 此时我们的参数是一个bean实例!!!!而之前注册监听器的时候, 放入的可是beanName,
  我们可以联想到, 这个bean就会被放入到主题对象中ListenerRetriever属性中的Set<ApplicationListener>
  这个集合中, 接下来我们看看这个方法
```

##### 代码四: addApplicationListener方法
```java
public void addApplicationListener(ApplicationListener<?> listener) {
  if (this.applicationEventMulticaster != null) {
    this.applicationEventMulticaster.addApplicationListener(listener);
  }
  else {
    this.applicationListeners.add(listener);
  }
}

分析:
  如果主题对象不存在, 那么就将监听器放在Spring上下文中的applicationListeners这个集合中, 这种情况
  只会发生在主题对象没被创建时, Spring代码中调用了Spring上下文的addApplicationListener方法, 比如
  说我们写一个bean工厂的后置处理器, 然后手动添加一个监听器???至少笔者在Spring源码中没看到会跑到这
  块代码, 但是其它集成Spring的框架可能会出现这种情况

  执行到这里, 通常情况下主题对象会存在, 那么就调用主题对象的addApplicationListener方法来将监听器
  注册到主题对象中

public void addApplicationListener(ApplicationListener<?> listener) {
  synchronized (this.retrievalMutex) {
    Object singletonTarget = AopProxyUtils.getSingletonTarget(listener);
    if (singletonTarget instanceof ApplicationListener) {
      this.defaultRetriever.applicationListeners.remove(singletonTarget);
    }
    this.defaultRetriever.applicationListeners.add(listener);
    this.retrieverCache.clear();
  }
}

分析:
  由于我们此时获取到的监听器对象可能是一个代理对象, AopProxyUtils.getSingletonTarget(listener)
  方法的作用就是获取到源对象(未被代理前的bean), 其实很简单, 如果这个监听器是一个代理对象, 那么就
  获取到代理对象的原始对象, 要先从主题对象中移除原始对象监听器, 将代理对象监听器放到主题对象中

  所以这个方法同步块中前几行的作用就是从主题对象中移除原始对象监听器, 防止一个监听器重复放入到主题
  对象中

  this.retrieverCache是一个Map缓存, key为事件对象, value为监听器集合, 即缓存了事件对象指向的所有
  监听器, 由于我们增加了监听器, 所以要清空这个缓存, 当我们触发事件的时候, 就会先从缓存中拿取对应的
  所有监听器, 如果拿到了就调用, 没拿到则扫描所有的监听器, 找出应用于该事件的监听器, 放入缓存, 并
  调用监听器的方法, 之后我们分析触发事件的源码时可以看到这些流程
```

> 当事件被触发时, 对该事件感兴趣的监听器就会被调用, 我们以ContextRefreshedEvent事件为例进行分析

### 发布事件, 主题对象通知所有的观察者对象, 事件对应的监听器被调用
#### 代码一: 触发ContextRefreshedEvent事件
```java
public void publishEvent(ApplicationEvent event) {
    publishEvent(event, null);
}

分析: 在refresh方法中, 调用了一个finishRefresh方法, 由此我们可以联想到, ContextRefreshedEvent
      事件就是在refresh方法调用即将结束时被触发的, 在该finishRefresh方法中, 可以看到publishEvent
      方法的调用, 顾名思义, 发布事件, 而它的参数就是一个事件对象, 我们需要发布哪个事件或者说我们需
      要触发哪种事件就传入对应的事件对象就好了, 在finishRefresh中可以看到Spring就是new了一个
      ContextRefreshedEvent对象, 而在new这个事件对象的时候, 传入了一个参数this, this表示的是
      整个Spring上下文, 换句话说, 这个事件对象持有一个Spring上下文的引用, 跟进构造方法可以看到,
      事件对象内部有一个属性为Object类型, 这个属性的名称就是source, 而其指向就是Spring上下文

      publishEvent方法内部调用了一个重载方法, 这个重载方法多了一个参数, 是一个ResolvableType类型的
```

#### 代码二: ResolvableType简单分析
```java
在看这个重载方法的代码之前, 我们先来了解一下什么是ResolvableType, 可以通过看这个重载方法的签名,
ResolvableType eventType,由此我们可以猜到, ResolvableType表示的或许是事件对象所属的类型? 猜的差
不多, 下面是ResolvableType的javadoc简单描述:
ResolvableTypes may be obtained from fields, method parameters, method returns or classes.
Most methods on this class will themselves return ResolvableTypes, allowing easy navigation.
For example:
  private HashMap<Integer, List<String>> myMap;

  public void example() {
      ResolvableType t = ResolvableType.forField(getClass().getDeclaredField("myMap"));
      t.getSuperType(); // AbstractMap<Integer, List<String>>
      t.asMap(); // Map<Integer, List<String>>
      t.getGeneric(0).resolve(); // Integer
      t.getGeneric(1).resolve(); // List
      t.getGeneric(1); // List<String>
      t.resolveGeneric(1, 0); // String
  }

由上面的Javadoc我们可以看到, 其实ResolableType就是对Java反射的一种封装, 通过这个类, 我们可以获取
泛型类型, 以及其他对反射的操作, 但是在Spring中, 对于这个重载的publishEvent方法的第二个参数, 即
ResolvableType一直传的是null
```

#### 代码三: 调用重载的发布事件方法
```java
protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
    ApplicationEvent applicationEvent;
    if (event instanceof ApplicationEvent) {
        applicationEvent = (ApplicationEvent) event;
    }
    else {
        applicationEvent = new PayloadApplicationEvent<>(this, event);
        if (eventType == null) {
            eventType = ((PayloadApplicationEvent)applicationEvent).getResolvableType();
        }
    }

    // Multicast right now if possible - or lazily once the multicaster is initialized
    if (this.earlyApplicationEvents != null) {
        this.earlyApplicationEvents.add(applicationEvent);
    }
    else {
        getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
    }

    // Publish event via parent context as well...
    if (this.parent != null) {
        if (this.parent instanceof AbstractApplicationContext) {
            ((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
        }
        else {
            this.parent.publishEvent(event);
        }
    }
}

分析:
    首先我们先来看看第一个if-else, 主要功能就是完成事件对象的强转, 如果事件是ApplicationEvent类
    型的, 就转为该类型, 如果不是, 则将其变为PayloadApplicationEvent事件类型, 根据
    PayloadApplicationEvent这个类的javadoc：
        Mainly intended for internal use within the framework.
    即这个事件类型主要是提供给框架内部使用的, 其实也很简单, PayloadApplicationEvent这个类继承了
    ApplicationEvent, 并且多了一个payLoad参数, 这个payLoad参数通过构造方法可以传入, 其实就是指
    向了原始的事件对象而已, 以当前else为例, 该PayloadApplicationEvent事件对象中的payLoad属性指
    向的就是传入的event对象

    获取到真正的事件类型后, 此时就应该真正的触发事件了, 可以看到Spring会先判断
    this.earlyApplicationEvents是否为空, 如果为空, 那么就将事件放入到
    this.earlyApplicationEvents中, 这一步在我们此次分析是不会被执行的, 其被执行的时间点为:
        在Spring调用之前我们分析的registerListeners方法之前, 如果程序员主动调用了publisEvent方法来触发事件, 由于此时
        还没有注册监听器对象, 所以这时候触发的事件是不会执行的, 称为早期事件, 而在registerListeners方法中就会将
        this.earlyApplicationEvents置为null, 然后发布这些早期事件

    从而我们的执行逻辑就调用到了else里面, 这个代码就是真正开始广播事件给对应的监听器的代码了

    最后, 如果当前的Spring上下文存在父容器, 那么事件在当前容器中触发后, 同时会在父容器中进行触发

    接下来我们开始分析广播事件的代码:
        getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
```

#### 代码四: multicastEvent广播事件给对应的监听器
```java
getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType)

分析:
    getApplicationEventMulticaster方法就是获取Spring上下文中的事件广播器, 在上面的描述中, 我们
    可以了解到, 这个事件广播器就是观察者模式中的主题对象, 主题对象中利用一个ListenerRetriever对象
    保存了所有在容器中的监听器对象, 如下:
        private class ListenerRetriever {
            public final Set<ApplicationListener<?>> applicationListeners;
            public final Set<String> applicationListenerBeans;
        }
    可以联想, 调用multicastEvent方法广播事件其实就是从这个ListenerRetriever对象中找到适用于该事
    件的所有监听器, 然后调用这些监听器的onApplicationEvent方法而已, 但是Spring在这里又增加了一
    层缓存, 比如在第一次广播ContextRefreshedEvent事件的时候, 找到该事件所感兴趣的所有监听器后将
    其缓存下来, 下次再触发该事件时就不用再去找了

public void multicastEvent(final ApplicationEvent event, @Nullable ResolvableType eventType) {
    ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
    for (final ApplicationListener<?> listener : getApplicationListeners(event, type)) {
        Executor executor = getTaskExecutor();
        if (executor != null) {
            executor.execute(() -> invokeListener(listener, event));
        }
        else {
            invokeListener(listener, event);
        }
    }
}

分析:
    如果eventType为null(由上面描述可以看到, 传的是null), 那么就调用resolveDefaultEventType方法,
    将事件对象传入从而获取到ResolvableType对象, 跟进代码可以看到, 最后就是调用了
    new ResolvableType(clazz)而已, 这个clazz就是我们的事件对象所对应的Class对象

    接下来就是一个for循环了, 找到该事件对应的所有监听器, 调用该监听器的onApplicationEvent方法,
    如果提供了一个Executor的话, 则可以利用多线程的方式来调用监听器, 所以核心就是
    getApplicationListeners方法, 该方法完成的功能是找到事件对应的所有监听器, 并缓存
```

#### 代码五: getApplicationListeners方法
```java
protected Collection<ApplicationListener<?>> getApplicationListeners(
			ApplicationEvent event, ResolvableType eventType) {

    Object source = event.getSource();
    Class<?> sourceType = (source != null ? source.getClass() : null);
    ListenerCacheKey cacheKey = new ListenerCacheKey(eventType, sourceType);

    // Quick check for existing entry on ConcurrentHashMap...
    ListenerRetriever retriever = this.retrieverCache.get(cacheKey);
    if (retriever != null) {
        return retriever.getApplicationListeners();
    }

    ..........
}

分析:
    我们先看这个方法的一部分代码, 根据前面的描述, event.getSource()方法就是获取Spring上下文对象,
    sourceType自然就是该上下文对象的Class对象了

    利用事件的类型以及上下文类型, 构建出缓存的key, 然后利用这个key从缓存中拿到对应的
    ListenerRetriever, 如果缓存中存在, 那么就直接调用缓存中的ListenerRetriever的
    getApplicationListeners方法获取监听器了, 显然此时我们触发该事件的时候是没有缓存的, 于是
    Spring就会开始去找

if (this.beanClassLoader == null ||
      (ClassUtils.isCacheSafe(event.getClass(), this.beanClassLoader) &&
        (sourceType == null || ClassUtils.isCacheSafe(sourceType, this.beanClassLoader)))){
    // Fully synchronized building and caching of a ListenerRetriever
    synchronized (this.retrievalMutex) {
        retriever = this.retrieverCache.get(cacheKey);
        if (retriever != null) {
            return retriever.getApplicationListeners();
        }
        retriever = new ListenerRetriever(true);
        Collection<ApplicationListener<?>> listeners =
                retrieveApplicationListeners(eventType, sourceType, retriever);
        this.retrieverCache.put(cacheKey, retriever);
        return listeners;
    }
}
else {
    // No ListenerRetriever caching -> no synchronization necessary
    return retrieveApplicationListeners(eventType, sourceType, null);
}

分析:
    这个if-else判断: 当目标事件对象所在的类是由主题对象中的属性对应的classLoader加载或者sourceType
    也是由该classLoader加载的时候, 则返回true, 通常情况是返回true的, 至于什么时候返回false, 笔者
    也不太清除.....

    if里面就是一个典型的doubleCheck模型, 如果对doubleCheck有所了解的话, 看到这样的代码就不会陌生
    了, 这里笔者就不深入分析doubleCheck了

    Spring创建了一个新的ListenerRetriever, 调用retrieveApplicationListeners方法找到当前事件相
    关的所有监听器, 并放入到这个新的ListenerRetriever中, 最终以上面生成的key和这个新创建的
    ListenerRetriever作为一对键值对放入到缓存中, 而核心就是这个retrieveApplicationListeners方
    法, 该方法的作用是找到该事件对应的所有监听器
```

#### 代码六: retrieveApplicationListeners方法找到事件对应的所有监听器
```java
LinkedList<ApplicationListener<?>> allListeners = new LinkedList<>();
Set<ApplicationListener<?>> listeners;
Set<String> listenerBeans;
synchronized (this.retrievalMutex) {
    listeners = new LinkedHashSet<>(this.defaultRetriever.applicationListeners);
    listenerBeans = new LinkedHashSet<>(this.defaultRetriever.applicationListenerBeans);
}

分析:
    allListeners里面存储的是所有的监听器, 因为retrieveApplicationListeners方法需要返回所有的监
    听器, 而返回的就是这个allListeners集合, 从主题对象的ListenerRetriever
    (即this.defaultRetriever)中取出监听器和以及监听器bean对应的beanName, 分别放入到listeners和
    listenerBeans集合中, 由上面的描述可以得知, 我们在调用refresh方法中的registerListeners方法的
    时候, 会将容器中所有的ApplicationListener类的beanName放入到主题对象中ListenerRetriever中的
    applicationListenerBeans集合, 而在bean的后置处理器ApplicationListenerDetector中就是将容器
    中的监听器对象放入到applicationListeners集合中, 此时将这两个集合的监听器拿出来, 就是为了一个
    个遍历, 找到适用于当前事件的监听器对象

for (ApplicationListener<?> listener : listeners) {
    if (supportsEvent(listener, eventType, sourceType)) {
        if (retriever != null) {
            retriever.applicationListeners.add(listener);
        }
        allListeners.add(listener);
    }
}

分析:
    遍历所有的listeners, 调用supportEvent方法判断是否支持当前事件, 如果支持, 则将其放入到我们传
    入的ListenerRetriever中(新创建的, 之后就是把这个对象放在缓存中的), 同时将其放入到allListener中

if (!listenerBeans.isEmpty()) {
    BeanFactory beanFactory = getBeanFactory();
    for (String listenerBeanName : listenerBeans) {
        Class<?> listenerType = beanFactory.getType(listenerBeanName);
        if (listenerType == null || supportsEvent(listenerType, eventType)) {
            ApplicationListener<?> listener =
                    beanFactory.getBean(listenerBeanName, ApplicationListener.class);
            if (!allListeners.contains(listener) && supportsEvent(listener, eventType, sourceType)) {
                if (retriever != null) {
                    retriever.applicationListenerBeans.add(listenerBeanName);
                }
                allListeners.add(listener);
            }
        }
    }
}  

分析:
    这一步是遍历所有的listenerBeans, 利用一个个BeanName从容器中拿到对应的监听器对象, 如果在上面
    那个循环中没有处理过, 并且调用supportsEvent返回true的话, 则将其加入到retriever对象的
    applicationListenerBeans中, 与此同时加入到allListeners中

小小的总结:
    整个retrieveApplicationListeners方法其实就是遍历主题对象中的所有监听器对象或者监听对象的
    beanName, 利用supportEvent方法来判断是否适用于当前事件
```

#### 代码七: supportsEvent判断监听器是否适用于当前事件
```java
protected boolean supportsEvent(
        ApplicationListener<?> listener, ResolvableType eventType,
        @Nullable Class<?> sourceType) {

    GenericApplicationListener smartListener = (listener instanceof GenericApplicationListener ?
            (GenericApplicationListener) listener : new GenericApplicationListenerAdapter(listener));
    return (smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType));
}

分析：
    先将当前的监听器对象封装成一个GenericApplicationListener, 然后调用其两个supportsEventType
    方法, 当两个方法都返回true的情况下, 表示当前监听器是适用于当前事件的

    可以看到, smartListener的实际类型是GenericApplicationListenerAdapter, 我们在创建这个对象的
    时候, 传入了监听器对象, 跟进这个构造方法可以看到:
        public GenericApplicationListenerAdapter(ApplicationListener<?> delegate) {
            this.delegate = (ApplicationListener<ApplicationEvent>) delegate;
            this.declaredEventType = resolveDeclaredEventType(this.delegate);
        }
    在GenericApplicationListenerAdapter对象中的delegate属性就是我们的监听器对象, 而
    declaredEventType属性就是这个监听器对象所关注的事件对象的Class被封装成的ResolvableType实例,
    总的来说, 这个Adapter对象中维护了监听器对象以及一个ResolvableType对象, 通过这个ResolvableType
    对象Spring可以反射获取获取监听器对象所关注的事件Class属性、方法、泛型类型等信息, 根据这个方法
    名也很好理解, 就是解析监视器对象所关注的事件的类型, 其实就是获取ApplicationListener的泛型而已

    接下来我们以第一个supportsEventType方法进行分析
    public boolean supportsEventType(ResolvableType eventType) {
		if (this.delegate instanceof SmartApplicationListener) {
			Class<? extends ApplicationEvent> eventClass
                            = (Class<? extends ApplicationEvent>) eventType.resolve();
			return (eventClass != null &&
                ((SmartApplicationListener) this.delegate).supportsEventType(eventClass));
		}
		else {
			return (this.declaredEventType == null ||
                        this.declaredEventType.isAssignableFrom(eventType));
		}
	}

    由此可见, 如果我们的监听器对象是SmartApplicationListener类型, 则进if判断了, 然后delegate指
    向的是我们定义的Listener, 通常我们是直接继承ApplicationListener的, 所以会进入else判断, 可以
    看到其实就是判断当前被触发的事件类型是不是监视器对象关注的类型
```

### 总结
```java
Spring监视器的原理就是对观察者模式的一种应用, 观察者模式中会涉及到主题对象和观察者对象, 在Spring中
SimpleApplicationEventMulticaster就是主题对象, ApplicationListener的子类实例就是观察者, 主题
对象会持有观察者, 在收到通知时就通知观察者, 在Spring中, 利用事件来区分不同类型的观察者, 触发不同的
事件就会通知不同的观察者, 在主题对象中, 没有直接利用一个集合来存储观察者对象, 而是利用
ListenerRetriever这个对象来存储, 主题对象持有一个该对象的引用, ListenerRetriever对象中有两个Set,
一个存储的是ApplicationListener, 一个存储的是beanName

在触发事件时, Spring通过获取监视器对象的泛型, 判断该泛型是否时该事件的类型来判断是否调用该监视器,
同时为了在下次重新调用该监视器的时候, 不用重新扫描, Spring增加了一层缓存, key是用事件对象Class和
事件对象中持有的source属性对应的Class来构成, 值就是一个新的ListenerRetriever对象, 存储了该事件触
发时应该调用的监视器对象
```
