## 引入
```
在讲解循环依赖的解决方案之前, 我们必须要对AOP的源码有一定的了解, 因为循环依赖涉及到的二级缓存就跟
AOP源码有关系, 在源码分析的时候, 会适当的删减一部分的代码(如log日志及一些不会影响主线的代码)
```

### 调用后置处理器完成AOP的初始化工作的步骤分析
#### 从整体上分析初始化工作
- 一、由创建单例bean的入口代码讲起
```java
if (mbd.isSingleton()) {
  sharedInstance = getSingleton(beanName, () -> {
    return createBean(beanName, mbd, args);
  });
  bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
}

可以看到, 最终是通过调用createBean方法来创建bean的, 换句话说, 在getSingleton方法中调用了这个lamda
表达式中的方法, 从而开始了创建bean的流程
```

- 二、由createBean引出bean的后置处理器
```java
protected Object createBean(String beanName, RootBeanDefinition mbd, Object[] args) {
  // 解析获得bean对应的class对象
  RootBeanDefinition mbdToUse = mbd;
  Class<?> resolvedClass = resolveBeanClass(mbd, beanName);

  // 在真正创建bean之前, 先执行一次bean的后置处理器
  // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
  Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
  if (bean != null) {
    return bean;
  }

  // 开始真正的创建bean
  Object beanInstance = doCreateBean(beanName, mbdToUse, args);
  return beanInstance;
}

分析:
  可以看到在真正创建bean之前, 即调用doCreateBean之前, 会先调用一次bean的后置处理器来解析, 根据源
  代码中的英文注释可以看到, 给予BeanPostProcessors一个机会去返回一个代理对象来替换目标bean实例,
  根据这个注释, 我们也许会想到, 难道AOP的代理对象就是在这里产生的吗?其实不是的, 这里是给予程序员去
  扩展的, 如果我们自己来实现一个代理逻辑, 那么可以在这里执行后置处理器的时候返回一个对象, 这样Spring
  就不会去创建bean对象了, 但是在这个方法中, Spring也做了一件很重要的事情, 接下来就来看看这个
  resolveBeforeInstantiation方法
```

- 三、由resolveBeforeInstantiation方法来看看在真正创建bean之前Spring的操作
```java
protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
  Object bean = null;
  // 当我们通过后置处理器生成了bean对象的时候, 就会把beanDefinition中设置该变量为true
  if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {

    // 如果bean不是合成的, 即由应用程序自己生成的, 并且有InstantiationAwareBeanPostProcessor
    // 后置处理器在容器中, 那么就会开始调用后置处理器
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
      Class<?> targetType = determineTargetType(beanName, mbd);
      if (targetType != null) {
        // 开始调用后置处理器的before方法, 如果该方法返回了一个对象, 那么说明创建了bean对象, 此时
        // 才会去调用after方法, 由上面的结论可以得知, Spring在这里是不会创建bean对象的, 换句话说,
        // after方法通常情况下是不会被调用的, 当我们自己实现了后置处理器并编写了代理逻辑的时候,
        // 才会调用到after方法
        bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
        if (bean != null) {
          bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
        }
      }
    }
    mbd.beforeInstantiationResolved = (bean != null);
  }
  return bean;
}

分析:
  整个resolveBeforeInstantiation方法最为核心的就是applyBeanPostProcessorsBeforeInstantiation
  方法的调用, 通常情况下, 这个方法的返回值为null, 仅仅当这个方法返回了一个bean对象的时候, 才会去
  调用afterInitialization方法, 换句话说, 对于一个bean对象的创建, 需要在该bean创建之前调用后置处
  理器的before方法, 在bean创建之后调用后置处理器的after方法, 根据之前的英文注释, Spring提供这个扩
  展点给程序员在此时此刻实现自己的代理逻辑, 接下来我们看看applyBeanPostProcessorsBeforeInstantiation
  方法的调用
```
- 四、applyBeanPostProcessorsBeforeInstantiation方法调用后置处理器
```java
protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
  for (BeanPostProcessor bp : getBeanPostProcessors()) {
    if (bp instanceof InstantiationAwareBeanPostProcessor) {
      InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
      Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
      if (result != null) {
        return result;
      }
    }
  }
  return null;
}

分析：
  applyBeanPostProcessorsBeforeInstantiation方法的主要工作就是调用InstantiationAwareBeanPostProcessor
  这个后置处理器的postProcessBeforeInstantiation方法, 即允许程序员在此处实现自己的代理逻辑, 通过
  该后置处理器的所有实现类可以看到, 基本上都是返回null
```

#### AbstractAutoProxyCreator的postProcessBeforeInstantiation方法
- 一、postProcessBeforeInstantiation方法
```java
public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
  Object cacheKey = getCacheKey(beanClass, beanName);

  if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
    if (this.advisedBeans.containsKey(cacheKey)) {
      return null;
    }

    if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) {
      this.advisedBeans.put(cacheKey, Boolean.FALSE);
      return null;
    }
  }

  return null;
}

分析:
  对于InstantiationAwareBeanPostProcessor所有实现类来说, 大家可以一个个去查看, 会发现, 几乎所有
  的实现类中这个方法都是返回null的, 因为并不需要在此时此刻创建bean, 这是Spring提供给程序员扩展自己
  的代理逻辑用的, 但是对于AOP的实现原理中, 一个核心的类AbstractAutoProxyCreator在实现这个方法的
  时候, 除了返回null之外, 还做了一些额外的事情, 这些事情跟AOP是息息相关的


AOP在此时做的事情:
  首先我们需要明白的一点, 当我们开启了AOP后, 所有的beanDefinition都会执行到这个before方法, 所以
  所有的beanDefinition都会进行这些判断
  <1> Object cacheKey = getCacheKey(beanClass, beanName), 对于这一行代码, 大家可以看看其里面
      的实现,其实就是用于获取这个beanClass真正的beanName而已

  <2> advisedBeans, 这个属性是一个Map<Object, Boolean>, 用于存储所有不用经过AOP代理判断的类以及
      已经被AOP代理过的类, 如果是前者, 那么value为false, 如果是后者, 那么value为true

  <3> 我们可以想象一下, 如果一个类被@Aspect注解标注了, 说明是一个切面类, 那么对于一个切面类, AOP
      肯定是不用去进行代理的, 这就是所谓的不用经过AOP代理判断的类, 而这个before方法的最重要的作用
      就是在每一个beanDefinition调用该方法的时候, 筛选出所有不用经过AOP代理判断的类, 并将其放入到
      advisedBeans这个map中
  <4> 但是对于一个bean来说, 如果没有被AOP的PointCut所包含, 其仍然也会放入到这个advisedBean中, 
      value为false, 换句话说, 在这个Map中, 当value为true的对象就是需要被AOP代理的对象, value为
      false的则不用, 在此时此刻, 仅仅将一些特殊的对象(如被@Aspect注解标注的对象)放入到这里而已,
      同时value均为false, 仅仅在创建代理对象的时候, 将代理对象放入到这个Map时, 对应的value为true
  <5> 利用shouldSkip方法完成了两件事情, 第一个是扫描整个容器中的类, 找出通知类(被@Aspect标注的类),
      以及通知类中的一个个通知, 将这些通知类的名字和这些类中的一个个通知缓存起来, 之后在AOP的时候
      就可以直接拿来用了
```

- 二、isInfrastructureClass筛选出所有的通知类
```java
// 调用AnnotationAwareAspectJAutoProxyCreator的isInfrastructureClass
protected boolean isInfrastructureClass(Class<?> beanClass) {
  return (super.isInfrastructureClass(beanClass) ||
      (this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass)));
}

// 调用super的isInfrastructureClass即调用AbstractAutoProxyCreator的isInfrastructureClass方法
protected boolean isInfrastructureClass(Class<?> beanClass) {
  boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
      Pointcut.class.isAssignableFrom(beanClass) ||
      Advisor.class.isAssignableFrom(beanClass) ||
      AopInfrastructureBean.class.isAssignableFrom(beanClass);
  return retVal;
}

分析:
  根据上面的代码可以看到, isInfrastructureClass方法在遇到一个类被@Aspect注解标注以及是Pointcut、
  Advice、Advisor、AopInfrastructureBean的子类时将会返回true
```

- 三、shouldSkip方法
```java
protected boolean shouldSkip(Class<?> beanClass, String beanName) {
  List<Advisor> candidateAdvisors = findCandidateAdvisors();
  for (Advisor advisor : candidateAdvisors) {
    if (advisor instanceof AspectJPointcutAdvisor &&
        ((AspectJPointcutAdvisor) advisor).getAspectName().equals(beanName)) {
      return true;
    }
  }
  return super.shouldSkip(beanClass, beanName);
}

分析:
  在findCandidateAdvisors方法中Spring完成的工作是查找出所有的通知类以及这些类中的通知方法, 然后
  在下面的for循环中如果一个通知是AspectJPointcutAdvisor类型的(通常不是), 并且这个通知的切面名字
  和bean的名字相同, 也是需要跳过的, 之后调用父类的shouldSkip方法, 在父类的方法中, 就是判断一个类
  是否以ORIGINAL(笔者也不知道是干嘛的)

  总而言之, 这里我们需要知道的是, shouldSkip方法完成了所有通知类和通知方法的扫描, 并进行了缓存
```

#### findCandidateAdvisors方法分析
- findCandidateAdvisors
```java
protected List<Advisor> findCandidateAdvisors() {
  // Add all the Spring advisors found according to superclass rules.
  List<Advisor> advisors = super.findCandidateAdvisors();
  // Build Advisors for all AspectJ aspects in the bean factory.
  if (this.aspectJAdvisorsBuilder != null) {
    advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
  }
  return advisors;
}

分析:
  整个findCandidateAdvisors方法是为了查出所有的通知, 并将其进行缓存起来

  对于super.findCandidateAdvisors()方法来说, 里面其实就是遍历了整个容器中的Advisor类, 如果该类
  的实例还没创建, 即没有走Spring的创建流程, 那么就会调用getBean方法进行创建, 之后将它们放在List中
  返回

  buildAspectJAdvisors方法才是开始构建我们自己提供的通知, 并将其放在缓存中, 然后将它们返回, 从而
  整合上一步骤的advisors, 即找到所有的通知advisors
```

- buildAspectJAdvisors方法构建程序员提供的通知
```java
public List<Advisor> buildAspectJAdvisors() {
  List<String> aspectNames = this.aspectBeanNames;

  if (aspectNames == null) {
    .......开始构建......
  }

  if (aspectNames.isEmpty()) {
    return Collections.emptyList();
  }
  List<Advisor> advisors = new ArrayList<>();
  for (String aspectName : aspectNames) {
    List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
    if (cachedAdvisors != null) {
      advisors.addAll(cachedAdvisors);
    }
    else {
      MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
      advisors.addAll(this.advisorFactory.getAdvisors(factory));
    }
  }
  return advisors;
}

分析:
  先从整体上看这个方法, Spring首先从缓存中拿到所有被@Aspect标注的类beanName, 如果缓存中没有, 在
  if判断中就会开始查找所有被@Aspect标注的类, 同时解析里面的通知, 将其变为一个Advisors, 然后放入
  缓存中

  换句话说, 在整个if判断中, 做了两件事情, 第一是找到所有被@Aspect标注的类, 第二是解析里面的通知方
  法, 将其变成一个个的Advisor, 之后将这两件事情得到的结果分别缓存起来, @Aspect标注的类的beanName
  就会缓存到this.aspectBeanNames中, 解析出来的Advisor缓存到this.advisorsCache中, 这是一个Map,
  key是beanName, value是一个List, 存储了这个bean所有的通知方法, 最后返回所有的通知

  如果缓存中取到了, 那么就不会进if里面, 从而Spring就开始遍历一个个被@Aspect标注的beanName, 然后
  根据这个beanName从advisorsCache中获取通知, 整合所有的通知并返回

  接下来我们分析下if里面的代码-----------------------

List<Advisor> advisors = new ArrayList<>();
aspectNames = new ArrayList<>();
String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
    this.beanFactory, Object.class, true, false);

分析:
  Spring利用BeanFactoryUtils根据类型从容器中拿到所有的beanName, 即所有Object类

for (String beanName : beanNames) {
  Class<?> beanType = this.beanFactory.getType(beanName);
  if (this.advisorFactory.isAspect(beanType)) {
    aspectNames.add(beanName);
    AspectMetadata amd = new AspectMetadata(beanType, beanName);
    if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
      MetadataAwareAspectInstanceFactory factory =
          new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
      List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
      if (this.beanFactory.isSingleton(beanName)) {
        this.advisorsCache.put(beanName, classAdvisors);
      }
      else {
        this.aspectFactoryCache.put(beanName, factory);
      }
      advisors.addAll(classAdvisors);
    }
  }
}
this.aspectBeanNames = aspectNames;
return advisors;

分析:
  Spring遍历所有的bean, 如果这个bean被@Aspect注解标注了, 那么就会将其加入到aspectNames中, 在最后
  可以看到, Spring会把所有被@Aspect标注的类的beanName缓存起来, 与此同时, Spring以beanName为参数
  创建了一个MetadataAwareAspectInstanceFactory, 利用advisorFactory的getAdvisors方法对该bean
  中的所有通知进行解析, 解析完成后将其放入到advisorsCache中, 最后将所有的通知返回

  在getAdvisors中, Spring会遍历该bean中的所有方法, 找到被通知注解(如@Before这样的)标注的方法, 将
  其封装成一个个的Advisor后返回
```

- 小小的总结
```
在findCandidateAdvisors方法中, Spring对容器中所有的类进行扫描, 查找出了所有通知类及通知方法, 并
将结果缓存了起来, 并且还查找了容器中Advisor类型的类(这些也是通知)
```




#### 小小的总结
```
第一次调用后置处理器完成AOP的初始化工作即最终会调用InstantiationAwareBeanPostProcessor接口的
postProcessBeforeInstantiation方法, 该方法是Spring提供给开发者的一个扩展点, 允许开发者自己实现
代理逻辑, 从而不采用Spring提供的代理逻辑, 但是在该方法的实现类中, 同时也做了一些AOP的初始化工作,
即会对所有的beanDefinition进行筛选, 将不用经过AOP代理的类筛选出来, 放入到一个advisedBeans的map中,
与此同时, 在第一次调用这个后置处理器, 即整个容器中第一个bd走到这个后置处理器的时候, 就会将整个容器
中的bd扫描一遍, 找出所有的通知类和通知, 将它们缓存起来
```

### AOP的实现原理步骤分析
#### 一、doCreateBean简单分析
```java
在上面AOP初始化工作的第二步中可以看到, 调用完resolveBeforeInstantiation完成了AOP的初始化之后, 就
调用了doCreateBean方法, 接下来我们简单的来解释下这个方法

protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) {
  // Instantiate the bean
  BeanWrapper instanceWrapper = createBeanInstance(beanName, mbd, args);

  final Object bean = instanceWrapper.getWrappedInstance();
  Class<?> beanType = instanceWrapper.getWrappedClass();

  Object exposedObject = bean;
  populateBean(beanName, mbd, instanceWrapper);
  exposedObject = initializeBean(beanName, exposedObject, mbd);

  .......
  return exposedObject;
}

分析:
  doCreateBean方法由于涉及到许多内容, 包括循环依赖的解决原理, 以及属性注入等, 这些我们之后的章节会
  进行详细的介绍, 在这里我们删除了许多的代码, 仅仅提取出一部分代码, 从而引入AOP真正的执行逻辑
  <1> createBeanInstance方法是Spring中真正用来创建bean对象的方法, 在该方法中, Spring同样是通过
      后置处理器来决定使用哪个构造方法来创建bean对象, 这个在之前bean的后置处理器章节有简单的提到,
      在创建完对象后, 封装成了一个BeanWrapper返回了, 之后便通过调用getWrappedInstance以及
      getWrappedClass方法来分别获取到bean对象以及bean对象对应的Class对象

  <2> 调用populateBean方法来对属性进行填充, 即对@Autowire进行解析以及set方法和get方法注入

  <3> 到此为止, 一个bean对象基本就被创建完了, 同时其属性也基本填充完成了
  <4> 调用initializeBean来对bean对象进行后续的操作, 包括InitializedBean接口方法的调用以及
      @PostContructor注解的解析, 还有我们本章内容的核心-AOP
```

#### 二、initializeBean方法
```java
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
  // 调用各种xxxAwarexx接口的方法
  invokeAwareMethods(beanName, bean);
  Object wrappedBean = bean;

  // 执行BeanPostProcessor的before方法, 其中完成了@PostContructor注解的处理
  if (mbd == null || !mbd.isSynthetic()) {
    wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
  }

  // 调用InitializingBean的afterPropertiesSet方法
  invokeInitMethods(beanName, wrappedBean, mbd);

  // 一个bean对象已经完成创建, 属性完成填充, 初始化方法完成调用, 此时开始执行AOP代理
  if (mbd == null || !mbd.isSynthetic()) {
    wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
  }

  return wrappedBean;
}
```

#### 调用AbstractAutoProxyCreator的postProcessAfterInitialization方法完成AOP
- postProcessAfterInitialization方法
```java
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
  if (bean != null) {
    // 获取bean对象真正的beanName
    Object cacheKey = getCacheKey(bean.getClass(), beanName);

    // 通常情况下这个earlyProxyReferences是不会包含的, 仅仅在出现循环依赖的时候才会出现
    // 因为在循环依赖出现的时候, 会提前进行代理, 那么此时就不用再代理了, 抛弃循环依赖的情况, 这一步
    // 是一定会进入的
    if (!this.earlyProxyReferences.contains(cacheKey)) {
      return wrapIfNecessary(bean, beanName, cacheKey);
    }
  }
  return bean;
}
```

- wrapIfNecessary
```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
  // 这三个判断是对上面AOP初始化工作的一个承前启后, 在AOP初始化工作的时候, 会筛选出所有不用
  // 参与AOP代理的类, 比如@Aspect注解标注的类, 到了这一步, 就会直接return bean, 从而不再进行AOP
  if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
    return bean;
  }
  if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
    return bean;
  }
  if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
  }

  // 开始创建AOP代理, 获取通知类型, 调用createProxy方法完成代理对象的创建
  // Create proxy if we have advice.
  Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
  if (specificInterceptors != DO_NOT_PROXY) {
    // 如果一个对象被AOP通知所包含, 则先添加到advisedBean中, value为true
    this.advisedBeans.put(cacheKey, Boolean.TRUE);
    Object proxy = createProxy(
        bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
    this.proxyTypes.put(cacheKey, proxy.getClass());
    return proxy;
  }

  // 如果一个对象没有被AOP通知所包含, 则添加到advisedBean中, value为false
  this.advisedBeans.put(cacheKey, Boolean.FALSE);
  return bean;
}
```

- getAdvicesAndAdvisorsForBean获取所有适用于当前bean的通知
```java
protected Object[] getAdvicesAndAdvisorsForBean(
    Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {

  List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
  if (advisors.isEmpty()) {
    return DO_NOT_PROXY;
  }
  return advisors.toArray();
}

分析:
  通过findEligibleAdvisors方法找到合适的通知, 如果找到了, 那么就转为数组并返回, 如果没找到, 则返
  回DO_NOT_PROXY(是一个null)

protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
  List<Advisor> candidateAdvisors = findCandidateAdvisors();
  List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
  extendAdvisors(eligibleAdvisors);
  if (!eligibleAdvisors.isEmpty()) {
    eligibleAdvisors = sortAdvisors(eligibleAdvisors);
  }
  return eligibleAdvisors;
}

分析:
  可以看到Spring会调用findCandidateAdvisors方法, 这个方法在之前描述AOP准备工作的shouldSkip方法
  中已经详细介绍过了, 就是找出所有的通知, 并将其缓存起来, 但是此时此刻调用这个方法则是从缓存中拿取
  然后调用findAdvisorsThatCanApply方法查找出适用于当前类的通知, extendAdvisors方法是个空方法,
  用来扩展的, 如果找到了合适的通知, 则进行排序, 因为之后在调用这些通知方法的时候是有顺序的, 比如
  @Before在@After之前执行
```

- findAdvisorsThatCanApply方法找到合适的通知
```java
在该方法中, 有多个嵌套调用, 不太方便将代码放上来, 笔者通过文字来描述, Spring会循环所有的通知, 对
每一个通知都会做一件事情, 那就是循环当前类的所有方法, 判断是否满足该通知, 如果满足, 那么就是一个合
适的通知, 换句话说, 这个方法里面就是一个双层循环, 外层是对通知的循环, 内层是对方法的循环
```

- createProxy创建代理对象
```java
/**
 * createProxy方法中最主要的工作就是创建一个代理工厂, 在这个代理工厂中定义了AOP对象创建的规则,
 * 比如是否强制采用Cglib动态代理等, 最后调用这个工厂类对象的getProxy来创建代理对象
 */
protected Object createProxy(Class<?> beanClass, String beanName,
          Object[] specificInterceptors, TargetSource targetSource) {

  ProxyFactory proxyFactory = new ProxyFactory();
  proxyFactory.copyFrom(this);

  if (!proxyFactory.isProxyTargetClass()) {
    if (shouldProxyTargetClass(beanClass, beanName)) {
      proxyFactory.setProxyTargetClass(true);
    }
    else {
      evaluateProxyInterfaces(beanClass, proxyFactory);
    }
  }

  Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
  proxyFactory.addAdvisors(advisors);
  proxyFactory.setTargetSource(targetSource);
  customizeProxyFactory(proxyFactory);

  proxyFactory.setFrozen(this.freezeProxy);
  if (advisorsPreFiltered()) {
    proxyFactory.setPreFiltered(true);
  }

  return proxyFactory.getProxy(getProxyClassLoader());
}

分析:
  在这个方法中, Spring构建一个代理工厂来创建代理对象, 在代理工厂中, Spring会将所有的通知放入到工厂
  中, 这些合适的通知会放入到代理对象所调用的InvocationHandler中, 作为成员变量存储, 即后面创建的
  JdkDynamicAopProxy对象(是一个InvocationHandler), 在这些对象中, 都会存储着对应其原始对象的通知

  与此同时, 在代理工厂中还会设置一个targtSource, 这个targetSource是createProxy传入的, 是Spring
  通过未代理的对象作为参数创建的一个SingletonTargetSource, 这个对象也会存入到代理对象所调用的
  InvocationHandler中, 即后面创建的JdkDynamicAopProxy, 这样在invoke方法中就可以通过targetSource
  来获取原始对象了
```

- getProxy方法创建代理对象
```java
public Object getProxy(@Nullable ClassLoader classLoader) {
  return createAopProxy().getProxy(classLoader);
}

protected final synchronized AopProxy createAopProxy() {
  return getAopProxyFactory().createAopProxy(this);
}

public AopProxyFactory getAopProxyFactory() {
  return this.aopProxyFactory;
}

public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
  if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
    Class<?> targetClass = config.getTargetClass();

    if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
      return new JdkDynamicAopProxy(config);
    }
    return new ObjenesisCglibAopProxy(config);
  }
  else {
    return new JdkDynamicAopProxy(config);
  }
}

分析:
  可以根据上面四个方法看到, getProxy中的createAopProxy方法其实就是获取到AOP的代理工厂, 然后创建
  对应的AOP代理策略, 即决定是通过JDK动态代理还是Cglib动态代理, 之后调用这些策略的getProxy方法来
  完成代理对象的创建, 对于JDK动态代理来说, 其实就是调用Proxy.newInstance方法, 对于Cglib来说, 就是
  创建Enhancer对象, 然后完成代理对象的创建, 这里就不再进行深入的分析了
```

### 总结
```
AOP源码的分析就差不多了, 我们从AOP初始化的一些配置开始讲起, 然后讲到了AOP真正的实现即BeanPostProcessor
的postProcessAfterInitialization方法, 在该方法中通过选取代理策略, 最终完成了AOP代理对象的创建,
在这里笔者需要提醒大家的是, wrapIfNecessary这个方法很重要, 根据名称可以看到, 如果必要的话则对bean
进行包装, 所以说, 在这个方法中, 则是判断一个对象是否需要代理, 如果需要代理, 则创建代理对象, 这个方
法大家需要多多注意, 因为其是我们理解循环依赖的二级缓存的关键！！！
```
