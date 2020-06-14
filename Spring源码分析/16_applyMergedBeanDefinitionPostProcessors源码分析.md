### 引入
```
在前面的文章中, 我们讲解到Spring在真正创建一个bean的时候是通过调用一个lamada表达式中的createBean
方法来创建的, 并且我们之前也对这个createBean方法中的resolveBeforeInstantiation方法进行了详细的
讲解(完成SpringAOP的初始化工作, 设置了不需要参与AOP的类), 往后我们又通过createBean中的doCreateBean
方法对Spring创建bean实例的源码进行了分析, 即createBeanInstance方法, 通过上面的流程, Spring已经
创建好了bean实例, 在doCreateBean方法中, Spring调用完createBeanInstance方法后又调用了一次后置处
理器, 即我们本篇内容需要讲解的方法-applyMergedBeanDefinitionPostProcessors
```

### applyMergedBeanDefinitionPostProcessors源码分析
- applyMergedBeanDefinitionPostProcessors
```java
protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd,
                                                  Class<?> beanType, String beanName) {
  for (BeanPostProcessor bp : getBeanPostProcessors()) {
    if (bp instanceof MergedBeanDefinitionPostProcessor) {
      MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
      bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
    }
  }
}

分析:
  根据代码可以看到, Spring其实就是调用了所有类型为MergedBeanDefinitionPostProcessor后置处理器的
  postProcessMergedBeanDefinition方法, 在没有手动的添加bean的后置处理器的条件下, 笔者分析发现,
  实现了该方法的后置处理器有以下几个:
  ApplicationListenerDetector
  ScheduledAnnotationBeanPostProcessor
  RequiredAnnotationBeanPostProcessor
  InitDestroyAnnotationBeanPostProcessor
  CommonAnnotationBeanPostProcessor
  AutowiredAnnotationBeanPostProcessor

  经过一个个的源码查看, 发现ScheduledAnnotationBeanPostProcessor和RequiredAnnotationBeanPostProcessor
  两个后置处理器都仅仅是对postProcessMergedBeanDefinition方法进行了空实现, 而ApplicationListenerDetector
  虽然进行了实现, 但是其里面代码就一行:
    this.singletonNames.put(beanName, beanDefinition.isSingleton());
  即将一个bean是否是单例设置到了singletonNames这个map中, 而这个map是在ApplicationListenerDetector
  中的, 所以对于前三个后置处理器来说, 我们可以跳过了....

  在真正分析后三个后置处理器之前, 我们首先从整体上说一下, 后三个后置处理器的工作都差不多, 都是为了
  查找出满足条件的属性、方法, 将他们封装起来, 以便后面在填充属性的时候可以直接使用, 在分析源码之前,
  我们先来聊聊几个源码中会出现的类的作用
```
### Member、InjectedElement、InjectionMetadata
```java
在Java反射中, 我们会经常的遇到Field、Method、Constructor类, 而本次我们提到的第一个类就是Member,
该类就是上面几个类的父类, Spring为了能够使得获取到的方法、属性都放在一个地方, 采用了接口编程, 将其
都变成了Member类型

当Spring扫描到一个方法加了@Autowired的时候, 就会将该方法反射获得到Method变为一个Member, 然后将其
放到InjectedElement中, 换句话说, InjectedElement就是对一个方法或者属性的一个封装, 除了有Member
存储原始的反射信息外, 还会有额外的信息, 比如required属性, 表示是否是必须注入的

一个类中可能会有很多个方法、属性被标注了@Autowired注解, 那么每一个被标注的方法、属性都用一个
InjectedElement表示, 而所有这些InjectedElement均被放入到一个Collections中, 这个集合则存在于
InjectionMetadata中, 即InjectionMetadata中的Collection<InjectedElement> injectedElements存储
了所有需要被注入的信息, 里面有一个targetClass属性则是存储了这些方法、属性所在的类Class对象
public class InjectionMetadata {
	private final Class<?> targetClass;

	private final Collection<InjectedElement> injectedElements;

  private volatile Set<InjectedElement> checkedElements;
}

可以看到, 在InjectionMetadata中还有一个checkedElements, 里面也是存储了InjectedElement, 之前提到
injectedElements的时候, 有人可能会认为, 难道Spring在后面进行属性填充的时候, 就是取injectedElements
中的一个个InjectedElement进行反射操作进行注入的吗, 其实不是的, Spring实际取的是checkedElements中
的InjectedElement, 在MergedBeanDefinitionPostProcessor中postProcessMergedBeanDefinition方法
中, Spring主要是找到所有被@PostConstruct、@PreDestory、@Autowired、@Resource、@Value标注的属性
或者方法, 将其封装成一个个的InjectedElement, 最后放到一个新创建的InjectedMetada中, 完成这些工作
后, Spring又会经过一些判断, 最终将这些InjectedElement从injectedElements取出来放到checkedElements
中, 在进行属性填充的时候, Spring就会取出一个个的InjectedElement, 通过反射的方式完成属性填充, 那么
上述提到的三个后置处理器有什么作用呢, 其实这是一个策略模式的典型应用, Spring对@PostConstruct、
@PreDestory注解的处理(转为InjectedElement)用的InitDestroyAnnotationBeanPostProcessor, 而对
@Resource的处理用的CommonAnnotationBeanPostProcessor, 对于@Autowired以及@Value的处理则是用的
AutowiredAnnotationBeanPostProcessor, 不同的后置处理器处理不同的注解, 下面我们以@Autowired注解
的处理为例子进行讲解, 其它注解的处理的代码跟这个是类似的, 就不再进行展开了
```

### AutowiredAnnotationBeanPostProcessor的postProcessMergedBeanDefinition方法
#### postProcessMergedBeanDefinition方法
```java
public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition,
                                            Class<?> beanType, String beanName) {
  InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
  metadata.checkConfigMembers(beanDefinition);
}

分析:
  findAutowiringMetadata方法完成了@Autowired注解的处理, 将被该注解标注的属性、方法封装为一个个的
  InjectedElement, 然后放入到InjectionMetadata中的集合injectedElements中

  checkConfigMembers方法则将injectedElements中的一个个InjectedElement取出来, 进行一些判断, 最
  后放入到checkedElements这个Set中(属性注入的时候就是取得checkedElements中得InjectedElement)

  下面我们分别来讲解下这两个方法
```

#### findAutowiringMetadata
```java
private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
  String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
  InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
  if (InjectionMetadata.needsRefresh(metadata, clazz)) {
    synchronized (this.injectionMetadataCache) {
      metadata = this.injectionMetadataCache.get(cacheKey);
      if (InjectionMetadata.needsRefresh(metadata, clazz)) {
        if (metadata != null) {
          metadata.clear(pvs);
        }
        metadata = buildAutowiringMetadata(clazz);
        this.injectionMetadataCache.put(cacheKey, metadata);
      }
    }
  }
  return metadata;
}

分析:
  Spring会先从缓存injectionMetadataCache中获取当前bean对应得注入元数据, 如果needsRefresh返回了
  true, 那么Spring就会调用buildAutowiringMetadata方法开始构建注入元数据, 构建完成后就会将其放入
  到缓存injectionMetadataCache中了

  needsRefresh的判断很简单, 即metadata == null || metadata.targetClass != clazz
  当metadata不存在于缓存的时候肯定是要进行构建的, 由于findAutowiringMetadata方法会在属性注入的时
  候也被调用, 所以通常情况下会拿到缓存中的数据, 需要注意的是, 在上述后置处理器调用完成后, 如果程序
  员手动的修改了InjectedMetadata中的targetClass, 那么就不能用原来的元数据了, 而是要重新构建一次,
  这也是metadata.targetClass != clazz返回true的情况下Spring也会调用构建方法的原因
```
#### buildAutowiringMetadata
```java
private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
  List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
  Class<?> targetClass = clazz;

  do {
    final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();

    ..........获取一个个的InjectedElement..........

    elements.addAll(0, currElements);
    targetClass = targetClass.getSuperclass();
  }
  while (targetClass != null && targetClass != Object.class);

  return new InjectionMetadata(clazz, elements);
}

分析:
  可以看到, Spring会创建一个elements的list来存放所有需要被注入的数据, 然后在while循环中, 开始获取
  该targetClass中的元数据, 获取完成后放入到elements中, 之后开始获取targetClass的父类中素有需要
  被注入的数据, 直到Object为止

  当一个类及其所有的祖先类中的元数据被扫描完成后, Spring就会将其放入到InjectionMetadata中返回,
  接下来我们开始分析Spring在这个while循环是如何获取一个个的InjectedElement的
```
- 代码一
```java
ReflectionUtils.doWithLocalFields(targetClass, field -> {
  AnnotationAttributes ann = findAutowiredAnnotation(field);
  if (ann != null) {
    if (Modifier.isStatic(field.getModifiers())) {
      return;
    }
    boolean required = determineRequiredStatus(ann);
    currElements.add(new AutowiredFieldElement(field, required));
  }
});

public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
  for (Field field : getDeclaredFields(clazz)) {
    fc.doWith(field);
  }
}

分析:
  根据上面的代码可以看到, Spring会遍历一个个的属性, 然后获取到该属性上@Autowired注解, 如果该注解
  不为空, 则Spring会将其变成一个AutowiredFieldElement(继承于InjectedElement), 然后将其添加到
  currentElements中, 在此之间, Spring还会判断@Autowired注解中required属性, 判断是否是该属性的
  注入是必须的, 如果一个属性是static的, 那么就直接返回了, 并且会打印一个info日志(笔者没写出来)
```

- 代码二
```java
ReflectionUtils.doWithLocalMethods(targetClass, method -> {
  Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
  if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
    return;
  }
  AnnotationAttributes ann = findAutowiredAnnotation(bridgedMethod);
  if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
    if (Modifier.isStatic(method.getModifiers())) {
      return;
    }

    boolean required = determineRequiredStatus(ann);
    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
    currElements.add(new AutowiredMethodElement(method, required, pd));
  }
});

分析:
  在上面代码中, 其实我们可以看到, 其实对@Autowired标注的方法和之前对属性的处理是类似的, 都是对一个
  个的method进行循环, 然后一个个处理, 如果一个方法是static的则直接返回不进行处理了, 上面出现了一个
  桥接方法的定义, 笔者不太清楚这个桥接方法是什么, 通常情况下是直接返回我们在类中定义的method的, 再
  往后, Spring调用了findPropertyForMethod获取属性装饰器, 这里简单的扩展一下, PropertyDescriptor
  其实是属于Java层面的知识, 属于Java内省机制的一部分, 其实就是Java中的一些定义而已, 在java中, 定
  义一个setXXX, getXXX, isXXX方法为属性的描述, Java中约定这些方法名中除了set、get、is之后的字母,
  则是Java中的属性名称(仅仅是一种约定而已, 我们不一定要遵守), 用PropertyDescriptor类上的注释描述
  为: A PropertyDescriptor describes one property that a Java Bean exports via a
      pair of accessor methods.
  总之, findPropertyForMethod方法则是获取该方法所在类中的所有get、set、is方法的属性描述, 如果当前
  被遍历的方法属于其中一类, 则返回该方法的PropertyDescriptor, 如果不是, 比如checkXXX, 则返回null
```

#### 简单的总结
```
在buildAutowiringMetadata方法中, Spring将一个类中@Autowired注解标注的方法和属性变为了一个个的
InjectedElement, 放入到一个elements的集合中, 最后将这个集合放入到InjectionMetadata中并返回
```

#### checkConfigMembers
```java
public void checkConfigMembers(RootBeanDefinition beanDefinition) {
  Set<InjectedElement> checkedElements = new LinkedHashSet<>(this.injectedElements.size());
  for (InjectedElement element : this.injectedElements) {
    Member member = element.getMember();
    if (!beanDefinition.isExternallyManagedConfigMember(member)) {
      beanDefinition.registerExternallyManagedConfigMember(member);
      checkedElements.add(element);
    }
  }
  this.checkedElements = checkedElements;
}

分析:
  可以看到, Spring遍历findAutowiringMetadata方法中找出来的一个个InjectedElement, 如果其满足代码
  中的条件的话, 就将其放入到checkedElements中, 而这个条件如下:

public boolean isExternallyManagedConfigMember(Member configMember) {
  synchronized (this.postProcessingLock) {
    return (this.externallyManagedConfigMembers != null &&
        this.externallyManagedConfigMembers.contains(configMember));
  }
}

public void registerExternallyManagedConfigMember(Member configMember) {
  synchronized (this.postProcessingLock) {
    if (this.externallyManagedConfigMembers == null) {
      this.externallyManagedConfigMembers = new HashSet<>(1);
    }
    this.externallyManagedConfigMembers.add(configMember);
  }
}

分析:
  可以看到, 当externallyManagedConfigMembers中不存在这个InjectElement的member(Method/Field)时,
  则调用registerExternallyManagedConfigMember方法放入进去, 如果出现两个一模一样的member, 则只会
  放入一个, 或许checkConfigMembers方法就是对InjectedElement的一种去重吧
```

### 总结
```
applyMergedBeanDefinitionPostProcessors方法通过调用一个个的MergedBeanDefinitionPostProcessor
中的postProcessMergedBeanDefinition方法完成了对被@Autowired等注解标注的方法、属性的处理, 将其变
为一个个的InjectionMetadata, 最终放入到该后置处理器中的injectionMetadataCache缓存中, 之后便可以
通过该后置处理器取得这些注入元数据, 进而完成属性的注入, 这里Spring也通过策略模式, 对不同类型的元数
据利用不同的后置处理器进行处理
```
