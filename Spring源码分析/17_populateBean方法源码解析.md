### 引入
```
在整个创建bean的过程中, 由前面的文章我们可以看到, Spring通过createBeanInstance方法创建了对象, 在
该方法中通过调用后置处理器推断构造方法以及调用autowireConstructor来完成注入, 在对象被创建完成后
调用了populateBean方法完成了属性的填充, 即对@Autowired、@Resource、@Value注解标注的属性进行填充,
以及对beanDefinition.getPropertyValues获取到的属性进行处理, 如果我们手动设置了一个BeanDefinition
的注入模型为byName或者byType的情况下, 也会在这里进行处理, 处理的顺序是:

<1> 如果注入模型为byName/byType, 则为自动注入, 自动注入的时候, Spring会扫描所有的get/set方法,
如果一个set方法有参数, 则Spring就会将这些方法去除set前缀, 以set后面的字符串认为是一个属性名, 将其
封装成一个PropertyValue, 就像是程序员通过beanDefinition.getPropertyValues来提供参数一样, 到了之
后在处理PropertyValues的时候, 就会取出一个个的PropertyValue, 调用里面属性对应的set方法完成自动注
入, 所以由这个可以看出, Spring在处理程序员提供的PropertyValue的时候, 必须要有set方法才能完成的,
由此, 我们发现其实自动注入最终还是利用了PropertyValue的处理逻辑而已, 这里需要注意的是, 在获取这些
set方法的时候, Spring不是自己手动的扫描所有的method, 而是直接利用了Java内置的内省机制来完成的
<2> 开始处理@Autowired等注解标注的属性/方法, 其实就是拿到之前applyMergedBeanDefinitionPostProcessord
方法调用时产生的injectedMetadata, 遍历一个个的injectedElement(包含了属性/方法相关的信息), 从容器
中查找bean, 之后通过反射调用方法或者属性
<3> Spring开始处理PropertyValues, 其实很简单, 就是通过set方法调用来处理我们手动放入的属性的

总而言之, populateBean方法主要就完成了上述的工作, 接下来我们开始从整体上聊聊这个方法, 然后对方法中
的嵌套调用进行深入分析
```

### populateBean方法执行步骤
- 代码一
```java
// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
// state of the bean before properties are set. This can be used, for example,
// to support styles of field injection.
boolean continueWithPropertyPopulation = true;

if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
  for (BeanPostProcessor bp : getBeanPostProcessors()) {
    if (bp instanceof InstantiationAwareBeanPostProcessor) {
      InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
      if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
        continueWithPropertyPopulation = false;
        break;
      }
    }
  }
}

if (!continueWithPropertyPopulation) {
  return;
}

分析:
  这一段代码是Spring用来提供给程序员扩展使用的, 如果我们不希望一个bean参与到属性注入, 自动装配的流
  程中, 那么就可以创建一个InstantiationAwareBeanPostProcessor后置处理器的实现类, 重写其
  postProcessAfterInstantiation方法, 如果该方法返回false, 那么continueWithPropertyPopulation
  这个变量会被置为false, 而这个变量被置为false, 在下面我们可以看到直接就return了, 从而Spring就不
  会对属性进行注入
```

- 代码二
```java
PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME || mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
  MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
  // Add property values based on autowire by name if applicable.
  if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME) {
    autowireByName(beanName, mbd, bw, newPvs);
  }
  // Add property values based on autowire by type if applicable.
  if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
    autowireByType(beanName, mbd, bw, newPvs);
  }
  pvs = newPvs;
}

这一段代码是当我们手动设置了注入模型为byType/byName的时候, Spring就会利用Java的内省机制拿到所有的
set方法, 如果一个set方法有参数, Spring就会将其封装成一个PropertyValue, 然后放入到新创建的newPvs
中, 最终用这个newPvs来替换原来的pvs, 这里有一个注意点, 在获取pvs的时候, 如果程序员没有提供, pvs
被设置成了null, 因为 mbd.getPropertyValues()这段代码始终是能拿到一个集合对象的, 只是这个集合对象
中没有PropertyValue而已
```

- 代码三
```java
boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();

PropertyDescriptor[] filteredPds = null;
if (hasInstAwareBpps) {
  if (pvs == null) {
    pvs = mbd.getPropertyValues();
  }
  for (BeanPostProcessor bp : getBeanPostProcessors()) {
    if (bp instanceof InstantiationAwareBeanPostProcessor) {
      InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
      PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
      ....这里还有一些检验....
      pvs = pvsToUse;
    }
  }
}

分析: 在这段代码中, 如果pvs == null, Spring就获取beanDefinition中的集合对象了, 如果pvs == null,
我们也可以推断出, 程序员没有提供PropertyValue, 同时, 该beanDefinition也不是byName/byType的, 之后
Spring会调用InstantiationAwareBeanPostProcessor.postProcessProperties方法, 在之前我们分析
applyMergedBeanDefinitionPostProcessor的时候, 有讲解到, Spring会将所有需要被注入的属性/方法封装
成一个InjectedElement, 然后放入到InjectionMetadata中, 而这个InjectionMetada是位于后置处理器中的,
这是一个策略模式的应用, 不同的后置处理器处理不同的注入类型, 而在当前这一步, 就是遍历这些不同的后置
处理器, 开始将它们中的InjectionMetadata拿出来, 取出一个个InjectedElement完成注入
```

- 代码四
```java
if (pvs != null) {
  applyPropertyValues(beanName, mbd, bw, pvs);
}

分析:
  如果pvs不为null, Spring就会开始遍历里面的一个个PropertyValue, 通过反射调用setXXX方法来完成注入,
  所以这就很好理解为什么当注入模型为byName/byType的时候, Spring能完成自动注入了
```

- 小小的总结
```
通过对populateBean方法的整体分析, 我们捋清楚了这个方法中主要完成的三件事情, 接下来我们会先对第二件
事情进行详细的分析, 因为第一件事情(处理byName/byType)在获取方法的参数的时候还是会调用到第二件事情
中的一些方法的, 所以我们先来分析Spring是如果处理@Autowired这些注解标注的属性
```

### AutowiredAnnotationBeanPostProcessor的postProcessProperties方法解析
```java
public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
  InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
  try {
    metadata.inject(bean, beanName, pvs);
  }
  catch (BeanCreationException ex) {
    throw ex;
  }
  catch (Throwable ex) {
    throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
  }
  return pvs;
}

分析:
  看到这个findAutowiringMetadata方法的调用, 相信大家应该会很熟悉了, 因为在之前我们分析
  applyMergedBeanDefinitionPostProcessors方法的时候, 就是通过调用findAutowiringMetadata来查找
  所有的被@Autowired注解标注的属性/方法并封装成一个个的InjectedElement的, 然后放入到
  InjectionMetadata中, 最后缓存起来, 而当前这个方法就是取到缓存中的metadata
```

### InjectionMetadata.inject方法解析
```java
public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
  Collection<InjectedElement> checkedElements = this.checkedElements;
  Collection<InjectedElement> elementsToIterate =
      (checkedElements != null ? checkedElements : this.injectedElements);
  if (!elementsToIterate.isEmpty()) {
    for (InjectedElement element : elementsToIterate) {
      if (logger.isTraceEnabled()) {
        logger.trace("Processing injected element of bean '" + beanName + "': " + element);
      }
      element.inject(target, beanName, pvs);
    }
  }
}

分析:
  在之前我们分析applyMergedBeanDefinitionPostProcessors方法的时候, 我们知道当一个个InjectedElement
  被创建后, 放入到InjectionMetadata后, Spring还会进行一次检验, 通过for循环, 将InjectedElement放
  入到checkedElements中, 可以通过上面的代码看到, inject方法就是遍历checkedElements中的一个个
  element, 然后完成注入的, 这里需要注意的是, 一个element有可能代表的是方法, 也有可能代表的是属性,
  而在InjectedElement中, Spring是通过接口编程, 放置的不是Method, 也不是Field, 而是它们的公共父类
  Member
```

### AutowiredFieldElement.inject方法解析
- 代码一
```java
Field field = (Field) this.member;
Object value;
if (this.cached) {
  value = resolvedCachedArgument(beanName, this.cachedFieldValue);
} else {
  .......
}

if (value != null) {
  ReflectionUtils.makeAccessible(field);
  field.set(bean, value);
}

分析:
  拿到Member对象, 将其向下转型为Field, 如果说存在缓存, 那么就会从缓存中取到这个值, 如果缓存中不存
  在, 则开始走else语句, 我们可以联想到, 当该属性对应的对象被查找出来后, 就会放入到缓存中, 这里我有
  个奇怪的想法, 一个属性对应一个InjectedElement, 不同的属性对应不同的element, 那么这个缓存的存在
  就意味着可能会对一个属性进行多次的注入, 这种情况笔者倒是没想到啥时候会出现....除非是程序员手动注
  入????

  在value被找出来后, Spring就会调用Java原生的反射方法field.set来完成属性的注入了, 所以核心就是这
  个value是怎么被找出来的(从Spring容器获得), 接下来我们看看else里面的代码
```

- 代码二
```java
else {
  DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
  desc.setContainingClass(bean.getClass());
  Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
  TypeConverter typeConverter = beanFactory.getTypeConverter();
  value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
  synchronized (this) {
    if (!this.cached) {
      if (value != null || this.required) {
        this.cachedFieldValue = desc;
        registerDependentBeans(beanName, autowiredBeanNames);
        if (autowiredBeanNames.size() == 1) {
          String autowiredBeanName = autowiredBeanNames.iterator().next();
          if (beanFactory.containsBean(autowiredBeanName) &&
              beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
            this.cachedFieldValue = new ShortcutDependencyDescriptor(
                desc, autowiredBeanName, field.getType());
          }
        }
      }
      else {
        this.cachedFieldValue = null;
      }
      this.cached = true;
    }
  }
}

分析:
  Spring在真正查找属性对应的对象之前, 会先将该属性的描述封装成一个DependencyDescriptor, 里面保存
  了Filed、是否强制需要即required, 以及属性所在的类(即Field所在的类Class对象), 然后调用bean工厂
  的resolveDependency方法开始解析依赖的对象, 当解析完成后, Spring利用一个同步块对解析出来的结果进
  行缓存

  而我们可以看到, Spring没有直接缓存解析出来的value, 而是缓存了一个ShortcutDependencyDescriptor,
  即属性的描述, 在代码的最上面, 我们可以看到, Spring创建了一个Set集合, 存储的是所有的BeanName, 想
  象这样一种场景, 我需要注入A对象, 但是容器中存在多个A对象, 此时在resolveDependency中就会获取到
  这些bean对应的beanName, 放入到autowiredBeanNames中, 之后利用一定的策略来进行判断注入哪个bean还
  是说抛出一个错误

  进一步的分析, 我们可以看到, 在同步块中, 如果之前解析仅仅得到一个合适的bean, 并且这个bean已经在
  容器中了, 即beanFactory.containsBean(autowiredBeanName), 那么Spring设置cachedFieldValue
  的值为ShortcutDependencyDescriptor, 此时缓存起来了

  联想上一步的代码, 如果缓存中存在值, 那么就从缓存中取得, 其实就是获取到这个ShortcutDependencyDescriptor
  对象, 由于仅仅只有一个bean并且该bean在容器中才会创建该对象, 所以如果是从缓存中取得, 其实就是调用
  beanFactory.getBean方法获得而已
```

### beanFactory.resolveDependency方法解析依赖的对象
```java
if (Optional.class == descriptor.getDependencyType()) {
  return createOptionalDependency(descriptor, requestingBeanName);
}
else if (ObjectFactory.class == descriptor.getDependencyType() ||
    ObjectProvider.class == descriptor.getDependencyType()) {
  return new DependencyObjectProvider(descriptor, requestingBeanName);
}
else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
  return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
}
else {
  Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
      descriptor, requestingBeanName);
  if (result == null) {
    result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
  }
  return result;
}

分析:
  如果需要被注入的类型为Optional、ObjectFactory、javaxInjectProviderClass类型的, 那么Spring就
  会利用特殊的方法来获取对象, 通常情况下我们获取的是一个普通的类型, 所以会走else

  在else中的第一行代码, 故名思意, 就是如果需要的话则获取到一个代理对象, 那么什么时候才需要呢?当我们
  需要注入的属性上面除了加了一个@Autowired注解外, 还有一个@Lazy注解的时候, Spring此时就会先返回
  一个代理对象进行注入, 因为解析一个对象在Spring中也是很麻烦的, 当项目中需要懒加载的时候则可以利用
  @Lazy来完成, 原理就是先返回一个代理对象

  如果没有@Lazy标注, 则Spring开始调用doResolveDependency方法来真正开始解析对象了
```

### DefaultListableBeanFactory.doResolveDependency方法步骤分析
- 代码一
```java
Object shortcut = descriptor.resolveShortcut(this);
if (shortcut != null) {
  return shortcut;
}

在doResolveDependency方法中, 我们可以看到传入了一个DependencyDescriptor依赖描述对象, 如果这个对象
的resolveShortcut返回了值, 则直接将这个值返回了, 联想到之前的代码, 当Spring解析完成一个对象后, 会
创建一个ShortcutDependencyDescriptor, 并将其放入缓存, 我们可以想到, 当Spring从缓存中获取对象的
时候, 同样会调用doResolveDependency方法来获取, 只不过此时其传入的依赖描述对象descriptor就是缓存
中的ShortcutDependencyDescriptor了

分析ShortcutDependencyDescriptor和DependencyDescriptor中的resolveShortcut方法我们可以清晰的看
到, 前者是直接调用了beanFactor的getBean方法, 从bean容器中获取对象, 而后者则是直接返回null
```

- 代码二
```java
Class<?> type = descriptor.getDependencyType();
Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
if (multipleBeans != null) {
  return multipleBeans;
}

Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
if (matchingBeans.isEmpty()) {
  if (isRequired(descriptor)) {
    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
  }
  return null;
}

分析:
  将上面的代码可以分成两个部分, 一个是解析MultipleBeans, 一个是解析正常的bean, Spring首先会获取
  需要注入的类型, 然后先调用resolveMultipleBeans方法解析, 这里我们不深入分析, 大家有兴趣可以去看
  看, 其实就是各种if判断, 如果type为Array则xxx, 如果type为Collection则xxx, MultipleBeans故名
  思意就是多个bean, 比如我需要将所有的UserService注入进来, 则可以:
  @Autowired
  private List<UserService> userServiceList;

  最后其实还是会回归到第二部分的代码的findAutowireCandidates中的, 换句话说, 解析multipleBeans
  其实还是利用的findAutowireCandidates方法查找出所有的合适的bean, 如果查找出来了则封装成List等等
  返回

  第二部分代码中, findAutowireCandidates故名思意就是查找出所有的需要注入的类型的所有对象, 比如查找
  出所有UserSerivce, 放入一个Map中, 这里之所以直接采用Object, 是因为有可能放入的是一个Class对象,
  当一个UserSerivce如果还没有被解析出来的时候, 即在容器中还没有这个对象的时候, Spring会先将存入
  该对象的Class对象, 如果容器中已经存在了, 则存入容器中的那个对象, 所以在后面我们会看到, Spring会
  判断Map中的Object是否是Class类型, 如果不是则直接返回了, 如果是则要走Spring的生命周期来完成对象
  的创建, 然后再返回
```

- 代码三
```java
String autowiredBeanName;
Object instanceCandidate;

if (matchingBeans.size() > 1) {
  autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
  if (autowiredBeanName == null) {
    if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
      return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
    }
    else {
      return null;
    }
  }
  instanceCandidate = matchingBeans.get(autowiredBeanName);
}
else {
  Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
  autowiredBeanName = entry.getKey();
  instanceCandidate = entry.getValue();
}

分析:
  通过代码二, 我们知道, Spring会将找出来的对象/Class放入到matchingBeans这个Map中, 如果这个里面的
  个数大于1个, 并且被注入的属性中@Autowired(required=true)的话, Spring就会抛出一个异常, 如果为
  false, 则返回null, 其实在determineAutowireCandidate中还有其它判断, 这里就不进行展开了

  如果size只有一个, 那么Spring就会获取到存在里面的属性对应的beanName, 以及value, 这个value可能是
  已经在容器中的对象, 也可能是还没创建的对象, 如果还没创建, 则instanceCandidate是一个Class对象
```

- 代码四
```java
if (autowiredBeanNames != null) {
  autowiredBeanNames.add(autowiredBeanName);
}
if (instanceCandidate instanceof Class) {
  instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
}
Object result = instanceCandidate;

分析:
  Spring首先会将找出来的beanName放入到autowiredBeanNames中, 这个autowiredBeanNames之后会放入
  缓存中, 可以看到, 如果instanceCandidate是Class类型, Spring此时才会去解析该对象, 如果已经在容器
  中了, 则instanceCandidate就是容器中的那个对象了, 而这个resolveCandidate也很简单, 里面就一行
  代码:
    return beanFactory.getBean(beanName);
```

- 小小的总结
```
在doResolveDependency方法中, 主要做了以下几件事情:
  <1> 如果descriptor是ShortcutDependencyDescriptor, 那么会走resolveShortcut来获取被注入的对象,
      其实就是通过beanFactory.getBean获取, 这一步是走缓存获取
  <2> 对MultipleBeans的解析, 如果我们要注入的对象是Array, List这样的情况下, 则resolveMultipleBeans
      方法就会找到所有满足类型的Bean, 然后进行注入
  <3> 如果不是MultipleBeans, 则利用findAutowireCandidates方法查找依赖, 在该方法中, Spring会返回
      一个Map, 即matchingBeans, 如果被注入的对象已经存在于容器中, 则Map中的value就是该容器中的对
      象, 如果还没被创建, 则value为该对象的Class对象
  <4> 如果matchingBeans的个数大于1个, 表示找到了多个, 则Spring会利用一定的规则进行判断, 或者抛出
      一个异常, 如果只有一个, 则获取到key, value
  <5> 将key放入到autowiredBeanName中, 用于存入缓存, 此时会对value再做一次判断, 如果为Class类型,
      则调用resolveCandidate来创建bean, 其实就是调用beanFactory.getBean方法
```


### findAutowireCandidates方法步骤分析(查找容器中的bean, 返回一个Map)
- 代码一
```java
String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				this, requiredType, true, descriptor.isEager());
Map<String, Object> result = new LinkedHashMap<>(candidateNames.length);
for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
  Class<?> autowiringType = classObjectEntry.getKey();
  if (autowiringType.isAssignableFrom(requiredType)) {
    Object autowiringValue = classObjectEntry.getValue();
    autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
    if (requiredType.isInstance(autowiringValue)) {
      result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
      break;
    }
  }
}

分析:
  方法的参数中有一个requiredType, 表示我们期望注入的属性的类型, Spring会在bean工厂中利用这个属性
  来找到所有该类型的beanName, 其实就是遍历所有的BeanDefinition而已, 将这些beanName放入到
  candidateNames中, 利用一个result的Map来存储结果, 最终返回的就是这个result

  Spring会遍历this.resolvableDependencies集合, 如果我们注入的类型是这个集合中的类型, 则Spring会
  做一定的处理, 通常情况下这个集合会有如下几个类型
    ResourceLoader
    BeanFactory
    ApplicationContext
    ApplicationEventPublisher
```

- 代码二
```java
for (String candidate : candidateNames) {
  if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
    addCandidateEntry(result, candidate, descriptor, requiredType);
  }
}

private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
			DependencyDescriptor descriptor, Class<?> requiredType) {

  if (descriptor instanceof MultiElementDescriptor) {
    Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
    if (!(beanInstance instanceof NullBean)) {
      candidates.put(candidateName, beanInstance);
    }
  }
  else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor &&
      ((StreamDependencyDescriptor) descriptor).isOrdered())) {
    Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
    candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
  }
  else {
    candidates.put(candidateName, getType(candidateName));
  }
}

分析:
  遍历所有满足条件的beanName, 调用addCandidateEntry方法, 如果被注入的bean还没解析, 则Map中放入
  该bean的Class对象, 如果已经解析了, 则从容器中获取到该对象
```


### 总结
```
本篇文章深入的分析了populateBean方法, 从整体上看, 先对byName/byType进行了处理, 获取到一个个的set
方法, 封装成PropertyValue, 然后处理@Autowired等注解标注的属性/方法, 通过反射完成注入, 最后处理
PropertyValue, 通过反射set方法完成注入

文章深入的分析了第二步骤, 即如果处理@Autowired等注解的, Spring会先判断是否是multipleBean, 之后再
处理不同的bean, 在获取bean的时候, 会先从容器中获取被注入bean类型的所有bean, 如果已经在容器中了,
则直接放入map中, 如果没有, 则放入Class对象, 之后遍历整个map, 如果value为Class类型, 则开始创建bean

其中, 在第二步骤中, Spring还利用了缓存的机制, 利用ShortcutDependencyDescriptor来完成了缓存的功能


总结, 对于属性填充, 无非就是利用反射, 填充的对象无非就是从容器中获取而已, 如果对象没创建, 则利用
getBean方法走生命周期, 完成创建
```
