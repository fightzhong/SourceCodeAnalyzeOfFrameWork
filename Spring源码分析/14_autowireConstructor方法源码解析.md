### 引入
```java
简单的回顾以下之前Spring推断构造方法的文章, Spring通过调用后置处理器的determineCandidateConstructors
方法将所有被@Autowired标注的构造方法给查找了出来, 如果存在的话, Spring就会调用autowireConstructor
方法来完成对象的创建, 故名思意, @Autowired注解可以理解为自动注入的, 所以autowireConstructor方法
来创建对象也是很好理解的, 然而利用这个方法来创建对象还有三种情况, 第一种是装配模型为通过构造方法来
注入, 即AutowireMode为AUTOWIRE_CONSTRUCTOR, 第二种是程序员提供了ConstructorArgumentValues, 这
种情况Spring也会利用该方法来创建对象, 第三种是getBean时提供了参数的情况, 以上三种情况下, Spring会
利用autowireConstructor方法来创建对象, 在该方法中, Spring还会去推断一次构造方法, 大家可以想象下,
第一种情况会出现多个构造器, 剩下的情况Spring同样是无法立刻得到用哪个构造器的, 所以还要去推断一次也
是很好理解的, 下面我们一步步来解析这个方法
```

### autowireConstructor方法步骤解析
- 代码一
```java
BeanWrapperImpl bw = new BeanWrapperImpl();
this.beanFactory.initBeanWrapper(bw);

Constructor<?> constructorToUse = null;
ArgumentsHolder argsHolderToUse = null;
Object[] argsToUse = null;

分析:
  Spring创建完bean对象后, 会将bean对象放入到BeanWrapper中, 之前的文章中也引入了这个说明, 然后可以
  通过get方法来获取到bean对象和该对象所对应的Class对象

  由于进入autowireConstructor方法的时候, 可能会出现多个构造器的情况, 此时Spring就要去推断到底使用
  哪个构造器, constructorToUse这个变量就是用来存储真正用来创建bean的构造器的, Spring会通过一个for
  循环来遍历构造器, 每找到一个更加合适的构造器时, 都会覆盖这个变量的值

  argsToUse用来存储真正用来创建对象的参数, 它有可能是我们传入的参数, 也有可能是我们解析出来的参数,
  当Spring推断出来一个构造器的时候, 会在ConstructorArgumentValues, args中去查找是否程序员提供了
  参数, 如果没有提供, 那么就会去bean工厂中查找, 没错, 就是通过getBean方法来获取对应的bean

  argsHolderToUse也是用来存储真正用来创建对象的参数的, argsToUse是由argsHolderToUse中得到的, 在
  之后的源码分析时, 可以很清晰的看到这样一段代码: argsToUse = argsHolder.arguments; 与argsToUse
  不同的是, holder中会存储其它形式的参数, 举个简单的例子, 如果对Mybatis源码有所了解的话, Mybatis
  在对MapperScan进行处理的时候, 会将扫描出来的接口对应的BeanDefinition中的BeanClass设置为
  mapperFactoryBean的class对象, 并且利用getConstructorArgumentValues().addGenericArgumentValue
  方法添加了一个构造参数: definition.getBeanClassName(), 这个参数的类型是字符串的, 表示扫描出来
  的接口的全限定类名, 需要注意的是, mapperFactoryBean中构造器却是一个Class对象, 换句话说, Spring
  在创建对象解析构造参数的时候, 会通过一个转换器在一定的情况下将一个全限定类名转为一个Class对象,
  那么argsHolderToUse的作用就很明显了, 存储原始的参数以及转换后的参数!!!
```

- 代码二
```java
if (explicitArgs != null) {
  argsToUse = explicitArgs;
}
else {
  Object[] argsToResolve = null;
  synchronized (mbd.constructorArgumentLock) {
    constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
    if (constructorToUse != null && mbd.constructorArgumentsResolved) {
      // Found a cached constructor...
      argsToUse = mbd.resolvedConstructorArguments;
      if (argsToUse == null) {
        argsToResolve = mbd.preparedConstructorArguments;
      }
    }
  }
  if (argsToResolve != null) {
    argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
  }
}

分析:
  explicitArgs是在getBean中传入的参数, Spring认为, 如果我们主动调用getBean并且又传入了参数的话,
  那么表示程序员就是想利用该参数来作为构造器的参数, 所以Spring就将该参数赋值给argsToUse了, 对于这种
  情况下, Spring不会想着从缓存中读取之前创建对象时推断出来的参数和构造器, 因为谁也不能保证下一次程
  序员还是用这些参数

  在else语句中, Spring就是去缓存中读取之前保存的构造器和参数(前提时之前创建过该对象), 这里的代码
  我们可以不用去看, 因为将后面的代码看懂了, 这里就懂了, 后面的代码会看到Spring将哪些数据放在缓存的
  哪个地方

总结一下: 这一步的作用是在允许读缓存的情况下去读缓存中的数据
```
- 代码三
```java
if (constructorToUse == null || argsToUse == null) {
  ...............
}

bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
return bw;

分析:
  经过之前的流程, Spring已经把基本的参数定义出来了, 同时也没有从缓存中读取到构造方法和构造参数的相
  关信息, 此时Spring就要开始推断构造器和参数了, 可以看到只要constructorToUse和argsToUse有一个为
  null, 就会开始推断构造方法, 即if判断里面的内容就是用来推断构造方法和参数的, 当这个if走完之后,
  Spring已经拿到了需要的数据, 此时就调用instantiate通过反射的方式完成对象创建, 并将创建后的对象
  设置到BeanWrapper中, 最后将这个BeanWrapper返回了
```

> **接下来是对if判断里面的代码进行分析**

- 代码四
```java
Constructor<?>[] candidates = chosenCtors;
if (candidates == null) {
  Class<?> beanClass = mbd.getBeanClass();
  candidates = (mbd.isNonPublicAccessAllowed() ?
            beanClass.getDeclaredConstructors() : beanClass.getConstructors());
}

分析:
  如果在autowiredConstructor方法的调用时, Spring传入了构造方法数组, 即找到了@Autowired标注的构造
  方法, 那么Spring肯定是从这个数组中选择构造方法, 如果没有, 则说明是四种情况的后三种情况了, 这个
  时候如果构造方法允许被访问非public的, 那么Spring就会拿到所有的构造方法, 否则仅仅拿到public方法,
  isNonPublicAccessAllowed默认为true, 表示允许访问非public方法, 这个属性可以通过
  beanDefinition.setNonPublicAccessAllowed方法来手动设置
```

- 代码五
```java
经过上面的步骤, Spring已经拿到了构造方法了, 此时要开始判断到底使用哪个方法, 即for循环所有构造方法,
但是在此之前, Spring做了一个判断, 这个判断可以在一定程度上加快autowiredContructor方法的执行:

if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
  Constructor<?> uniqueCandidate = candidates[0];
  if (uniqueCandidate.getParameterCount() == 0) {
    synchronized (mbd.constructorArgumentLock) {
      mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
      mbd.constructorArgumentsResolved = true;
      mbd.resolvedConstructorArguments = EMPTY_ARGS;
    }
    bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
    return bw;
  }
}

分析:
  如果只有一个构造器, 并且程序员没有主动提供参数的情况下, 并且这个构造器的参数是0个的话, 说明之后
  一定是调用默认构造器的, 此时Spring就不再去判断到底使用哪个构造方法了, 而是直接采用这个默认构造方
  法完成对象的创建, 同时将这个构造器和参数信息存入缓存, 以便之后再次调用的时候, 可以优先从缓存中读
  取, 需要注意的是, Spring仅仅会在explicitArgs为null的时候才去缓存构造器和参数信息, 原因之前也解
  释过了...
```

- 代码六
```java
boolean autowiring = (chosenCtors != null ||
    mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
ConstructorArgumentValues resolvedValues = null;

int minNrOfArgs;
if (explicitArgs != null) {
  minNrOfArgs = explicitArgs.length;
}
else {
  ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
  resolvedValues = new ConstructorArgumentValues();
  minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
}

分析:
  autowiring表示是否是自动注入, 当我们存在@Autowired注解或者装配模型为AUTOWIRE_CONSTRUCTOR则表示
  是自动装配的, 这个参数在autowiredConstructor方法里面是看不出有什么作用的, 而是在拿参数方法中才
  能看出有什么作用, 这里我们先记住, 如果找到了@Autowired注解标注的构造器或者仅仅只有一个有参构造器
  即chosenCtors不为null的情况下, 以及注入模型为AUTOWIRE_CONSTRUCTOR的情况下, Spring认为是自动注
  入的, 它的作用其实就是在拿参数的时候, 仅仅为自动注入的情况下, 才会调用getBean方法去从容器中拿参数,
  之后涉及到拿参数时我们再更深入聊一下这个参数

  如果程序员在调用getBean方法时, 主动传入了参数, 即explicitArgs不为空的话, 那么Spring就会用这些
  参数去查找对应的构造器, 一个类可能有很多个构造器, 如果传入了三个参数, 那么对于少于三个参数的构造
  器其实就可以直接略过了, 而minNrOfArgs的作用就是这样, 故名思意min number of args, 如果程序员没
  有显示的提供参数, 那么Spring就会从ConstructorArgumentValues中取得参数的个数, 从而将这个个数赋
  值给minNrOfArgs

  resolveConstructorArguments方法做了两件事情, 第一件事是将beanDefinition中的参数放到新创建的
  resolvedValues中来, 这两个是一模一样的类实例, Spring之所以新创建一个, 然后将旧的对象中存储的参
  数放到新的对象中来存储, 是因为beanDefinition中的ConstructorArgumentValues没有对参数进行解析,
  可以在resolveConstructorArguments方法中看到, Spring对于参数为RuntimeBeanReference、
  BeanDefinitionHolder、BeanDefinition...等等类型的情况下, 会进行一定的解析, 而在beanDefinition
  中的ConstructorArgumentValues是没有做该动作的, 第二件事就是上面提到的, 根据ConstructorArgumentValues
  的参数个数来确定minNrOfArgs的值

总结一下:
  上面的代码做的事情是, 确定minNrOfArgs的值, 如果程序员通过两种方式提供了参数, 则该值为程序提供的
  参数的个数, 如果没有, 则为0, 其次对于ConstructorArgumentValues中的参数进行了解析
```

- 代码七
```java
AutowireUtils.sortConstructors(candidates);
int minTypeDiffWeight = Integer.MAX_VALUE;
Set<Constructor<?>> ambiguousConstructors = null;

分析:
  Spring对构造器进行一定规则的排序, 比如public - protected - default - private权限排序, 同权限
  的情况下再对参数个数进行排序, 参数多的排前面, 之所以排序是因为Spring认为public方法优先, 如果找到
  了public方法来创建对象, 那么就不再考虑其它的方法了, 并且Spring认为参数多的优先, 或许Spring觉得
  应该充分利用参数来完成对象的创建吗?比如程序员提供了四个参数, 总不能直接用默认构造器来创建吧。。。

  minTypeDiffWeight参数很有意思, 表示构造器需要的参数和查找出来的参数的差异大小, Spring会取得差异
  值最小的构造方法来创建对象, 在对构造器进行排序后, Spring会拿出一个个的构造器, 同时去查找参数, 根
  据构造器和查找出来的参数计算出一个差异值, 举个例子, 有两个构造器, 这两个构造器都是只有一个参数的,
  第一个构造器的参数为A类实例, 第二个构造器的参数为B类实例, 在遍历的时候, 查找到的参数为A类实例,
  这时候第一个构造器得到的差异值自然就小于第二个构造器了, Spring也会利用第一个构造器来创建对象

  ambiguousConstructors表示有歧义的构造器, 当两个构造器计算出来的差异大小一模一样的时候, Spring
  就认为此时是歧义的, 不知道采用哪个构造器来创建对象了, 这两个构造器会放入到ambiguousConstructors
  这个Set中, 如果是在宽松模式下, Spring就会采用第一个找出来的构造器, 如果在严谨的模式下, SPring就
  抛出了一个错误, 一个bean处于宽松还是严谨可以通过beanDefinition.setLenientConstructorResolution
  方法来设置, 对这两个模式的判断之后我们也会讲到
```

- 简单的总结
```
在上述的操作中, Spring完成了以下几件事情:
  <1> 从缓存中拿取构造器和参数, 如果拿到了则走缓存创建, 没拿到则开始推断构造器
  <2> Spring根据之前推断构造方法的文章中描述到的方法中获得的构造方法来确定此时参与筛选的构造方法,
      如果之前没有推断出来, 则根据权限获取类中的构造器
  <3> 如果只有一个默认构造方法, 则会直接调用这个方法来完成对象的创建(一个创建对象的捷径)
  <4> 根据程序员提供的参数确定构造器需要的最少的参数, 即筛选出参数个数大于等于minNrOfArgs值的构造
      方法, 如果在一定的情况下, Spring还会对ConstructorArgumentValues中的参数进行解析, 如果程序
      员没有提供参数, 则minNrOfArgs值为0
  <5> Spring对构造器按照一定的规则进行排序, 初始化差异值变量的值为Integer的最大值, 定义
      ambiguousConstructors这个Set用来存储有歧义的构造器

到了这个时候, Spring已经完成了推断构造方法时的准备工作, 接下来Spring会开始遍历一个个构造器以及拿取
参数, 根据差异值确定最合适的构造器

```

- 代码八
```java
for (Constructor<?> candidate : candidates) {
  .......确定最合适的构造器, 拿取构造器参数.......
}

if (constructorToUse == null) {
  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
      "Could not resolve matching constructor " +
      "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
}
else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
      "Ambiguous constructor matches found in bean '" + beanName + "' " +
      "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
      ambiguousConstructors);
}

if (explicitArgs == null) {
  argsHolderToUse.storeCache(mbd, constructorToUse);
}

分析:
  通过一个for循环确定constructorToUse和argsToUse, 如果没有找到合适的构造器, 则报错, 如果找到了
  歧义的构造器, 但是此时这个bean处于一个严谨状态, 那么也抛出一个错误

  到此, Spring已经找到了合适的构造器和参数, 如果程序员没有显示的提供参数, 而是通过Spring自己确定
  的参数, 那么Spring就会将此时的构造器和参数缓存下来, 之后在一定条件下可以走缓存拿到对应的数据, 接
  下来便是对for循环进行分析了
```

> **for循环内容分析开始**

- 代码九
```java
Class<?>[] paramTypes = candidate.getParameterTypes();

if (constructorToUse != null && argsToUse.length > paramTypes.length) {
  // Already found greedy constructor that can be satisfied ->
  // do not look any further, there are only less greedy constructors left.
  break;
}
if (paramTypes.length < minNrOfArgs) {
  continue;
}

分析:
  Spring拿到构造器中的参数类型, 如果已经推断出了构造器和参数, 并且推断出来的构造器中参数个数是大于
  本次循环的构造器的, 则Spring认为已经找到合适的了, 之后的都是不合适的, 直接break掉循环, 如果本次
  循环的构造器的参数个数小于最少参数个数, 则进入下一次循环
```

- 代码十
```java
ArgumentsHolder argsHolder;
if (resolvedValues != null) {
  String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);
  if (paramNames == null) {
    ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
    if (pnd != null) {
      paramNames = pnd.getParameterNames(candidate);
    }
  }
  argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
      getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
}
else {
  // Explicit arguments given -> arguments length must match exactly.
  if (paramTypes.length != explicitArgs.length) {
    continue;
  }
  argsHolder = new ArgumentsHolder(explicitArgs);
}

分析:
  resolvedValues不为null, 说明explicitArgs为null, 在之前的代码可以看到, 如果explicitArgs不为
  null, Spring不会对resolvedValues赋值的, 换句话说, 这个if-else判断就是用来区分程序员是否传入了
  args情况的, 如果传入了, 那么采用程序员提供的args来创建holder对象, 如果没传入则Spring会调用
  createArgumentArray方法来创建参数对象数组, 这也就是上面我们所谓的拿参数了, 在这个方法中, Spring
  会先从ConstructorArgumentValues拿, 如果没拿到, 则表示是自动注入, 则Spring会利用getBean方法从
  容器中拿, 这里面就不细讲了, 如果之后有时间, 笔者可以单独开一篇文章来写这个方法, 这个方法里面还是
  有点东西的, 比如有这么一段代码会用到之前的autowiring方法:
  valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
  if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
    valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
  }
  大家有兴趣可以去看看里面的代码, 为了能够让大家看的轻松一点, 我简单的简单解释下上面这几行代码,
  Spring会先通过resolvedValues来获取index和generic的参数(一个是有索引定位一个是没有索引定位), 如
  果没拿到, 则Spring在非自动注入的情况下(仅仅只有在程序员手动提供参数时才会出现这种情况), 又会再拿
  一次, 这个原因是, 我们可能传入的是字符串, 但是却可以解析成一个Class对象(MapperScan源码中有该场景),
  所以这一步是为了拿到这些没有转换过的参数
```

- 代码十一
```java
int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
    argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
// Choose this constructor if it represents the closest match.
if (typeDiffWeight < minTypeDiffWeight) {
  constructorToUse = candidate;
  argsHolderToUse = argsHolder;
  argsToUse = argsHolder.arguments;
  minTypeDiffWeight = typeDiffWeight;
  ambiguousConstructors = null;
}
else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
  if (ambiguousConstructors == null) {
    ambiguousConstructors = new LinkedHashSet<>();
    ambiguousConstructors.add(constructorToUse);
  }
  ambiguousConstructors.add(candidate);
}

分析:
  通过之前的分析, Spring通过for循环拿到了构造器, 通过代码十拿到了参数, 此时Spring就会去计算此时参
  数和构造器的差异值, 如果小于之前的差异值, 则本次循环的构造器就是更合适的构造器, 此时赋值
  constructorToUse、argsHolderToUse、argsToUse, 同时更新最小差异值minTypeDiffWeight, 这里之所
  以还将ambiguousConstructors置为null, 是因为假设有5个构造器, 前两个构造器计算出来的差异值相同,
  那么就会放入到ambiguousConstructors中, 即else if中的代码, 循环到了第三个构造器时, 计算出来的差
  异值更小了, 说明第三个更加合适, 那么前面有歧义的构造器就没有存在的必要了, 因为Spring不会在前两个
  构造器中找了, 同时也是为了后面出现歧义构造器而重新赋值
```

> **到此为止, 整个autowiredContructor方法就已经分析完毕了**
