### 一、getBean到doGetBean
```java
在我们创建一个bean的时候, 是由getBean方法开始创建的, 该方法往下调, 最终会调到doGetBean方法, 而
doGetBean方法才可以算是开始创建bean了, 在调用的过程中, 会涉及到两个getSingleton方法, 为了在文章
中更好的显示它们, 笔者对这两个不同的重载方法利用geteSingleton1和getSingleton2进行区分, 同时我们
假设A类和B类形成了一个循环依赖, 并且A类开始创建

protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

  final String beanName = transformedBeanName(name);
  Object bean;

  // Eagerly check singleton cache for manually registered singletons.
  Object sharedInstance = getSingleton1(beanName);
  if (sharedInstance != null && args == null) {
    bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
  } else {
    .....
    if (mbd.isSingleton()) {
      sharedInstance = getSingleton2(beanName, () -> {
          return createBean(beanName, mbd, args);
      });
      bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
    }
  }
}

protected Object getSingleton1(String beanName, boolean allowEarlyReference) {
  Object singletonObject = this.singletonObjects.get(beanName);
  if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
    synchronized (this.singletonObjects) {
      singletonObject = this.earlySingletonObjects.get(beanName);
      if (singletonObject == null && allowEarlyReference) {
        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
        if (singletonFactory != null) {
          singletonObject = singletonFactory.getObject();
          this.earlySingletonObjects.put(beanName, singletonObject);
          this.singletonFactories.remove(beanName);
        }
      }
    }
  }
  return singletonObject;
}

分析:
  以单例bean的创建为例子, 在doGetBean中, Spring会获取到bean的真正的beanName, 然后调用
  getSingleton1方法, 在getSingleton1方法中, Spring会从singletonObjects中取bean, 最终我们创建的
  bean都会放到这里来, 在创建一个A类实例的时候, 从缓存中自然是拿不到的, 此时singletonObject==null

  我们可以看到还要一个isSingletonCurrentlyInCreation方法被调用了, 大家可以点进去看, 其实SPring
  就是判断singletonsCurrentlyInCreation这个集合中是否存在当前beanName, 在上述流程中, 我们可以
  看到是没有往里面放入任何一个beanName的, 所以不会进入if语句块, 即本次调用getSingleton1返回的是null

  sharedInstance为null, 则Spring开始走getSinleton2来真正的创建A类实例
```

- getSingleton2方法中调用createBean方法
```java
Object singletonObject = this.singletonObjects.get(beanName);
if (singletonObject == null) {
  beforeSingletonCreation(beanName);
  singletonObject = singletonFactory.getObject();
  afterSingletonCreation(beanName);
  addSingleton(beanName, singletonObject);
}
return singletonObject;

分析:
  beforeSingletonCreation方法中, 有行代码this.singletonsCurrentlyInCreation.add(beanName),
  可以看到, Spring此时才会将A类对应的beanName放到这个集合中, 然后调用singletonFactory.getObject
  方法创建bean, 其实就是调用createBean方法, bean对象创建完成后, afterSingletonCreation方法中才
  会将beanName从singletonsCurrentlyInCreation中移除, 需要注意的是!!!当A类实例还在创建时, 这个
  集合中是存在A类的beanName的, 换句话说, 如果再一次调用getSingleton1方法, 是能进入到那个if判断中
```

- createBean方法最终调到了doCreateBean方法
```java
createBean方法中调用了resolveBeforeInstantiation方法, 之后调用了doCreateBean方法, 之前我们在解
析AOP源码的时候已经对该方法进行了详细的描述, 里面完成AOP的初始化工作, 找出了所有的通知等

protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {
  // Instantiate the bean.
  BeanWrapper instanceWrapper = createBeanInstance(beanName, mbd, args);
  final Object bean = instanceWrapper.getWrappedInstance();
  Class<?> beanType = instanceWrapper.getWrappedClass();

  applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);

  // Eagerly cache singletons to be able to resolve circular references
  // even when triggered by lifecycle interfaces like BeanFactoryAware.
  boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
      isSingletonCurrentlyInCreation(beanName));
  if (earlySingletonExposure) {
    addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
  }

  // Initialize the bean instance.
  Object exposedObject = bean;
  populateBean(beanName, mbd, instanceWrapper);
  exposedObject = initializeBean(beanName, exposedObject, mbd);

  return exposedObject;
}

分析:
  笔者将整个doCreateBean方法冗余的代码都删除了, 只留下了核心的代码, createBeanInstance方法完成了
  A类实例的创建, 此时bean对象被创建了, 该方法之前笔者也详细进行了分析, 从推断构造方法到构造方法的
  自动注入都进行了分析, 然后调用applyMergedBeanDefinitionPostProcessors方法完成了@Autowired等
  注解标注的属性的扫描, 将它们缓存到一个InjectionMetada中, 之后调用populateBean中取出一个个的
  InjectedElement, 从容器中利用getBean方法找到需要注入的属性, 最后调用initializeBean方法完成AOP
  以及一些初始化方法的调用(@PostConstruct)

  需要注意的是, 在调用populateBean方法的时候, 还处于A类的创建中, 其里面有一个属性B, 我们调用
  populateBean方法填充B, 其实就是调用getBean方法从容器中获取B, 此时B还没创建, 于是又回到了整篇
  文章的开头

  B类调用getSingleton1方法, 从singletonObjects这个map中找不到(因为还没创建), 而此时因为还没调用
  getSingleton2, 所以在singletonsCurrentlyInCreation这个集合中也没有B的beanName, 所以if判断不
  会进入, 之后调用getSingleton2, 此时会将B的beanName放入到singletonsCurrentlyInCreation集合中,
  然后调用createBean, 之后又到了doCreateBean方法中

  调用createBeanInstance方法创建B对象, 调用populateBean方法填充B对象中的属性即A类实例, 此时会调
  用getBean方法从容器中获取A类实例

  调用getSingleton1方法, 从singletonObjects这个map中找不到(因为A虽然处于创建过程中, 但是还没有
  一步是将A对象放入到这个map), 需要注意的是, 此时singletonsCurrentlyInCreation这个集合中已经存在
  了A类的beanName, 所以能够进入到if, 如下:
  if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
    singletonObject = this.earlySingletonObjects.get(beanName);
    if (singletonObject == null && allowEarlyReference) {
      ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
      if (singletonFactory != null) {
        singletonObject = singletonFactory.getObject();
        this.earlySingletonObjects.put(beanName, singletonObject);
        this.singletonFactories.remove(beanName);
      }
    }
  }

  可以看到, 先从earlySingletonObjects中获取对象, 我们此时是获取不到的, 因为我们没放入过, 但是, 
  this.singletonFactories.get(beanName)是能返回一个单例工厂的, 而这个单例工厂是我们创建A的时候,
  调用doCreateBean方法中调用的, 在createBeanInstance方法后, populateBean方法前, 有下面的代码:
  boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
      isSingletonCurrentlyInCreation(beanName));
  if (earlySingletonExposure) {
    addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
  }

  protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

  跟进addSingletonFactory方法, 我们发现, 其实就是往singletonFactories放入了一个单例工厂, 而这个
  单例工厂只有一个方法, 调用这个单例工厂的方法就等于调用getEarlyBeanReference方法, 所以说当我们
  在上述getSingleton1中调用singletonFactory.getObject()方法的时候, 其实就是调用了这个
  getEarlyBeanReference方法获取A对象, 获取完成后放入到earlySingletonObjects中


  换句话说, 当循环依赖产生的时候, A类会先往singletonFactories中放入一个单例工厂对象, 这个叫做单例
  对象的一级缓存, 然后当B要填充属性时, 填充的对象为A对象时, 此时会调到getSingleton1中, 由于在
  singletonsCurrentlyInCreation这个集合中已经存在A了, 所以能够进if判断, 从而调用
  this.singletonFactories.get(beanName)方法获取到之前放入到singletonFactories那个单例工厂对象,
  从而调用单例工厂对象的getObject方法获取单例bean, 然后放入到earlySingletonObjects这个map中,
  这也叫二级缓存, 最后在整个A对象创建完成后, 才会放到singletonObjects中, 这叫三级缓存, 三级缓存
  是最终的缓存

  为什么需要一级缓存, 即为什么一开始要放到单例工厂中?
    我们点进getEarlyBeanReference方法看到, 其实里面就是调用了AOP对象的创建流程, 因为在循环依赖发
    生的时候, 我们真正创建AOP对象是在initializeBean方法, 而populateBean方法是在initializeBean
    方法之前创建的, 假设A最终会是一个代理对象, 如果没有一级缓存利用getEarlyBeanReference方法来创
    建代理对象, 那么我们注入到B的时候, 就会注入的是一个原始对象, 而不是一个代理对象

  为什么需要二级缓存, 而不是直接放入到singletonObjects中?
    对于这个, 笔者认为, 对于一个单例bean, 其放入真正的单例缓存应该是一个固定的位置, 即getSingleton2
    方法的最后, 即createBean方法之后, 而不是在其它地方, 所以这里采用一个二级缓存来进行过渡
```
