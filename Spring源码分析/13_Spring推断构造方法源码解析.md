### 引入
```
在SpringBean的生命周期中, 首先是bean工厂的实例化, 然后开始扫描所有满足条件的类, 将他们变成一个个的
BeanDefinition放入到容器中, 接下来便是bean创建的过程, 在bean创建的过程中, 我们很容易的想到一个对象
的创建无非就是利用反射构造方法来完成的, 当对象被创建完后, Spring就会开始填充属性, 比如对@Autowired
的解析, 再之后就是开始调用程序员定义的初始化方法, 以及AOP的处理(前提是该类需要被AOP), 最后将这个创
建出来的对象放入到容器中, 上述便是对SpringBean生命周期的简单描述, 在创建一个bean对象的时候, Spring
会去推断构造方法, 因为一个类可能有多个构造方法, 那么Spring就需要推断到底用哪个构造方法来完成反射创
建, 并且在构造方法上我们可以提供@Autowired注解, Spring同时也会进行怎么样的处理呢?
```

### 引入createBeanInstance方法
```java
在之前的文章中, 我们可以简单的得知, Spring创建一个bean是从getBean方法开始的, 之后会调用到一个lamda
表达式中createBean方法, 再之后会调用到doCreateBean方法(之前AOP的源码就是分析doCreateBean方法前
面的那个bean后置处理器的调用)

// Instantiate the bean.
BeanWrapper instanceWrapper = null;
if (mbd.isSingleton()) {
  instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
}
if (instanceWrapper == null) {
  instanceWrapper = createBeanInstance(beanName, mbd, args);
}
final Object bean = instanceWrapper.getWrappedInstance();
Class<?> beanType = instanceWrapper.getWrappedClass();

分析:
  上述代码是doCreateBean方法的开头几行代码, 我们可以清晰的看到一个createBeanInstance方法的调用,
  首先解析下BeanWrapper这个类, 其实就是对Bean对象的一个包装, 一个Bean对象被创建出来后, 会放到这个
  BeanWrapper中, 然后返回, 即createBeanInstance方法返回的就是一个包裹了bean实例的对象, 再往后我
  们可以看到, 通过getWrappedInstance方法就可以获取到我们创建的bean对象了, 通过getWrappedClass就
  可以获取到这个bean对象对应的Class对象, 所以接下来我们需要着重的讲解一下createaBeanInstance方法
  是如何创建一个bean的
```

### createBeanInstance方法详解(一行行代码分析)
- 代码一
```java
Class<?> beanClass = resolveBeanClass(mbd, beanName);
if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) 
      && !mbd.isNonPublicAccessAllowed()) {
  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
      "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
}

分析:
  第一行代码就是获取需要实例化的bean对象的Class对象, 用于反射获取构造方法, 之后对这个类进行了权限
  的判断, 如果不是public类并且又没有手动设置类的访问权限的话, 就会抛出一个异常, 这个访问权限我们在
  反射中会经常使用, 比如设置一个private的方法为可访问的
```

- 代码二
```java
Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
if (instanceSupplier != null) {
  return obtainFromSupplier(instanceSupplier, beanName);
}

分析:
  这是Spring提供给开发者的扩展点, 如果我们要自己来实现创建对象的过程, 那么就可以提供一个Supplier的
  实现类, 当一个BeanDefinition中存在一个Supplier实现类的时候, Spring就利用这个类的get方法来获取
  实例, 而不再走Spring创建对象的逻辑

如果想测试这个功能, 只需要我们利用Spring提供的扩展点手动设置BeanDefinition的属性就好了, 如下:
GenericBeanDefinition beanDefinition
    = ( GenericBeanDefinition ) registry.getBeanDefinition( "userService" );
beanDefinition.setInstanceSupplier( () -> {
  return new UserService()
} );

之后当Spring实例化这个userService所在的beanDefinition的时候, 上面的mbd.getInstanceSupplier()
方法返回的值就不是null了, 从而通过obtainFromSupplier方法来获取bean对象
```

- 代码三
```java
if (mbd.getFactoryMethodName() != null)  {
  return instantiateUsingFactoryMethod(beanName, mbd, args);
}

factorMethod这个名称在xml中还是比较常见的, 即通过工厂方法来创建bean对象, 在目前流行的注解开发中,
@Bean注解所创建的bean对象也是称为factoryMethod, 所以说如果一个bean对象是由@Bean注解创建的, 那么
该对象则是会走instantiateUsingFactoryMethod方法来创建的
```

- 代码四
```java
// Shortcut when re-creating the same bean...
boolean resolved = false;
boolean autowireNecessary = false;
if (args == null) {
  synchronized (mbd.constructorArgumentLock) {
    if (mbd.resolvedConstructorOrFactoryMethod != null) {
      resolved = true;
      autowireNecessary = mbd.constructorArgumentsResolved;
    }
  }
}
if (resolved) {
  if (autowireNecessary) {
    return autowireConstructor(beanName, mbd, null, null);
  }
  else {
    return instantiateBean(beanName, mbd);
  }
}

分析:
  第一行就可以清晰的看到Spring的注释, 是一个重新创建bean时的一个快捷方式, 这些代码之后, 就是Spring
  开始推断用于实例化对象的构造方法的代码了, Spring在推断完构造方法后, 会将推断出来用于创建bean的构
  造方法缓存起来, 当下一次创建相同对象的时候, 就不用再去推断构造方法了, 而是直接采用缓存的, 这个通常
  用于多例对象的创建, 即scope为prototype的情况, 至于它是怎么通过autowireConstructor以及
  instantiateBean方法创建对象的, 我们之后的代码也会出现这两个方法, 再之后的代码中我们再来细聊, 当
  后面的代码看懂之后, 再来看这里的, 就会很清晰了
```

- 代码五(createBeanInstance方法的最后几行代码)
```java
// Candidate constructors for autowiring?
Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
    mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args))  {
  return autowireConstructor(beanName, mbd, ctors, args);
}

// Preferred constructors for default construction?
ctors = mbd.getPreferredConstructors();
if (ctors != null) {
  return autowireConstructor(beanName, mbd, ctors, null);
}

// No special handling: simply use no-arg constructor.
return instantiateBean(beanName, mbd);

在开始的一行代码中, 根据方法的名称我们可以看到, Spring是通过bean的后置处理器来完成构造方法的推断
的, 这里我们先说结论, 如果我们推断出来的构造方法存在的话, Spring就会走autowireConstructor方法
来创建bean对象, autowireConstructor这个方法的意思是通过自动装配的方式来创建对象, 我们想象一个
场景, 当我们提供了多个构造方法的时候, 并且多个构造方法中存在两个@Autowire( required = false )
标注的情况时, Spring此时会推断出两个合适的构造方法(其实是三个, 这里为了更好的理解才说两个), 这时
候Spring就迷茫了, 到底采用哪个构造方法来创建对象呢?而autowireConstructor这个方法就会再一次进行
推断, 从而决定一个构造方法来创建对象, 之后我们也会细讲这个方法

更多的是, 当我们一个bean的装配模型为按照构造方法自动注入的时候, 也会走autowireConstructor方法来
创建对象, 我们可以手动的调用beanDefinition的setAutowireMode方法来设置自动装配的类型, 默认为NO,
在AbstractBeanDefinition中已经设置了常量供我们选择装配模型

更多的是, 当我们手动的设置了构造方法需要用到的参数的时候, Spring也会走autowireConstructor方法来
创建对象, 因为我们手动设置了参数的情况下, Spring也要推断到底使用哪个构造方法来实例化对象, 我们可以
通过beanDefinition.getConstructorArgumentValues().addGenericArgumentValue( xxx )来手动设置
构造方法需要的参数, 并且可以指定索引的来设置, 比如第0个参数的值为xxx

更多的是, 当我们手动传入了args参数的时候, Spring也会调用autowireConstructor方法来创建对象, 这个
args是一个Object的数组, 其实应用场景很简单, 假设一个bean是多例的, 那么其在Spring容器初始化的时候
肯定是不会进行实例化的, 而是我们手动调用getBean的时候才会实例化, 而这个getBean方法是可以传入参数的,
我们在getBean方法传入的参数就是这个args

-----------------------------------------------------------------------------------------
小小的总结一下: 在满足上述四种情况下, Spring就会调用autowireConstructor方法来创建对象, 对于第一
种情况, 当Spring推断出构造方法的时候, 即ctors不为null的时候即满足条件, 之后我们会细讲Spring是如何
推断构造方法的
------------------------------------------------------------------------------------------

对于getPreferredConstructors这一段代码, 笔者也不太清楚应用场景, 这里略过.........

可以看到, 当我们推断出来的构造方法为null的时候, Spring会利用instantiateBean方法来实例化对象, 其实
这个方法很简单, 就是利用反射调用默认构造方法来创建对象而已, 大家有兴趣可以debug到里面看看, 会清晰
的看到ctor.newInstance(args)这样一行代码, 即通过反射的方式来创建对象
```

### determineConstructorsFromBeanPostProcessors方法详解
#### 查找后置处理器
```java
if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
  for (BeanPostProcessor bp : getBeanPostProcessors()) {
    if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
      SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
      Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
      if (ctors != null) {
        return ctors;
      }
    }
  }
}

分析:
  可以看到在determineConstructorsFromBeanPostProcessors方法中, 其实就是调用了
  SmartInstantiationAwareBeanPostProcessor后置处理器的determineCandidateConstructors方法, 在
  Spring的实现中, 仅仅只有AutowiredAnnotationBeanPostProcessor这个后置处理器对该方法进行了实现,
  其它子类返回的均为null
```

#### AutowiredAnnotationBeanPostProcessor.determineCandidateConstructors方法
```java
// 这里仅仅截取了重要的部分, 对于一些日志打印以及不会影响主线的代码就略过了(比如一开始的@Lookup注
// 解的解析), 以及一些异常的捕捉代码也会进行略过
public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName){
  Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
  if (candidateConstructors == null) {
    // Fully synchronized resolution now...
    synchronized (this.candidateConstructorsCache) {
      candidateConstructors = this.candidateConstructorsCache.get(beanClass);
      if (candidateConstructors == null) {

        ..........................一系列的判断操作.............................

        this.candidateConstructorsCache.put(beanClass, candidateConstructors);
      }
    }
  }
  return (candidateConstructors.length > 0 ? candidateConstructors : null);
}

分析:
  我们先把最核心的判断代码删除, 先从整体上来看看代码的架构, 可以看到, Spring会先从缓存中拿构造方法,
  拿不到再去推断, 一个很简单的double check代码, 并且在if的最后会将筛选出来的构造方法放入到缓存中,
  下一次就可以直接从缓存中拿取了, 在最后, 如果candidateConstructors中个数大于0个才会返回, 否则
  返回null, 即筛选出来了构造方法后才会返回, 接下来我们来重点解析if里面的代码

代码一：Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
分析:
  在if里面有这么一行代码, 找到primaryConstructor, 那么这个方法的作用到底是干什么呢, 根据Spring中
  findPrimaryConstructor方法的注释可以清晰的了解到, 这个方法是用于Kotlin的class的, 如果一个类不
  是Kotlin的类, 就会简单的返回一个null而已, 换句话说, 如果一个类不是Kotlin的, 我们就不用去管它,
  因为这个方法始终返回null, 即primaryConstructor始终为null, 其实这个方法的代码也很好理解, 大家
  有兴趣可以去看看, 这里就不进行展开了, 之所以提出这个, 是因为在筛选构造方法的时候, 有多次用到了这
  个primaryConstructor, 但是条件都是primaryConstructor != null, 根据上面的描述, 这个条件在Java
  中是不会出现的, 所以之后的分析中我们会略过跟primaryConstructor相关的内容

代码二:
  candidate.isSynthetic(), 表示一个构造方法是否是合成的, 在之前的文章中, 我们介绍了什么是合成类,
  联想可知, 如果一个构造方法不是由应用程序创建的, 那么这个构造方法就是合成的, 在if判断中, 如果一个
  构造方法不是合成的, 就会使得nonSyntheticConstructors这个int变量加1, 但是这个变量的使用又伴随着
  Kotlin的primaryConstructor来判断的, 所以可以认为这个参数也没啥用, 我们也会略过

代码三:
  Constructor<?>[] rawCandidates = beanClass.getDeclaredConstructors();
  List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
  Constructor<?> requiredConstructor = null;
  Constructor<?> defaultConstructor = null;
分析:
  Spring创建了四个变量, 首先rawCandidates可以清晰的看到, 就是获取到类中所有的构造方法, candidates
  就是用来存储所有被筛选出来的构造方法, 其实可以认为, 就是把所有@Autowired注解标注的方法放到里面,
  但是还多放了一个默认构造方法, requiredConstructor表示@Autowired标注并且required为true的构造方
  法, 因为只允许出现一个这样的构造方法, 所以当这个变量存在值后, 又出现了一个相同情况的构造方法的话,
  Spring就会抛出一个错误, defaultConstructor用来保存默认构造方法

代码四:
for (Constructor<?> candidate : rawCandidates) {
  AnnotationAttributes ann = findAutowiredAnnotation(candidate);
  if (ann == null) {
    Class<?> userClass = ClassUtils.getUserClass(beanClass);
    if (userClass != beanClass) {
        Constructor<?> superCtor =
            userClass.getDeclaredConstructor(candidate.getParameterTypes());
        ann = findAutowiredAnnotation(superCtor);
    }
  }
  if (ann != null) {
    if (requiredConstructor != null) {
      throw new BeanCreationException();
    }
    boolean required = determineRequiredStatus(ann);
    if (required) {
      if (!candidates.isEmpty()) {
        throw new BeanCreationException();
      }
      requiredConstructor = candidate;
    }
    candidates.add(candidate);
  }
  else if (candidate.getParameterCount() == 0) {
    defaultConstructor = candidate;
  }
}

分析:
  这是一个对所有的构造方法进行变量的for循环, 在循环中, Spring首先会去找@Autowired注解, 如果这个
  注解为null, 说明这个构造方法没有被@Autowired标注, 但是Spring又做了一次查找, beanClass表示当前
  需要被new的这个类, userClass表示父类, 有兴趣可以看看getUserClass的代码, 其实很简单, 如果这个
  beanClass是Cglib代理的, 那么Spring就会找到原来的类, 即beanClass的父类, 然后看这个原生类是否有
  @Autowired注解标注

  换句话说, if(ann == null)这个代码的作用就是Spring去查找Cglib代理情况下的类的原生类(父类)是否有
  @Autowired注解

  如果说当前构造方法没有被@Autowired注解标注, 并且参数个数为0, 则为默认构造方法, 此时会赋值给
  defaultConstructor

  如果说当前构造方法被@Autowired标注了, 即ann不为null, 那么Spring会将这个构造方法放入到candidates
  中, 并且对多个required = true的情况进行了报错处理

------------------------------------------------------------------------------------------
小小的总结:
  在for循环中, Spring会将所有被@Autowired标注的构造方法找出来放入到candidates中, 同时对默认构造
  方法进行了赋值于defaultConstructor, 并且对@Autowired( required = true )标注的多个构造方法进行
  了报错处理, 总结: candidates中存放的是所有被@Autowired标注的构造方法
------------------------------------------------------------------------------------------

代码五:
if (!candidates.isEmpty()) {
  if (requiredConstructor == null) {
    if (defaultConstructor != null) {
      candidates.add(defaultConstructor);
    }
  }
  candidateConstructors = candidates.toArray(new Constructor<?>[0]);
} else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
  candidateConstructors = new Constructor<?>[] {rawCandidates[0]};
} else {
  candidateConstructors = new Constructor<?>[0];
}

分析:
  如果cadidates存在值, 说明找到了@Autowired标注的构造方法, 在requiredConstructor为null的情况
  (比如有多个@Autowired(required = false)标注的构造方法), Spring会将默认构造方法也放入到筛选出来
  的candidates中, 但是required = true则不会, 因为这种情况表示已经确定只会使用那个构造方法来实例化
  对象了

  如果没有找到@Autowired标注的构造方法, 但是该类中只有一个有参构造方法的情况下, Spring则返回这个
  有参的构造方法

  除此之外, 返回null(candidateConstructors的大小为0的情况下会返回null)
```

### 总结
```
推断构造方法中, Spring就是把@Autowired标注的方法都找出来, 然后在满足不存在required = true的情况
下将默认构造方法也放入筛选出来的集合中

determineCandidateConstructors方法返回了构造方法的情况有以下几种:
  <1> 存在@Autowired( required = false )的情况下, 返回所有被该注解标注的以及默认构造方法
  <2> 存在@Autowired( required = true )的情况下, 仅仅返回这个构造方法
  <3> 不存在被Autowired标注的情况下, 并且只有一个有参构造方法的情况下, 返回这个构造方法
```
