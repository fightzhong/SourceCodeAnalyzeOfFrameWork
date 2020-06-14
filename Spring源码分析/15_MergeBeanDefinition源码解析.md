### 引入
```java
在之前的文章中, 我们对BeanDefinition的体系进行了较为深入的介绍, 并且提出了一个父子bean的概念, 一
个BeanDefinition是可以设置父类BeanDefinition的, 仅仅需要调用其setParentName即可, 之所以出现父子
bean是因为Spring允许将相同bean的定义给抽出来, 成为一个父BeanDefinition, 这样其它的BeanDefinition
就能共用这些公共的数据了, 并且提出了RootBeanDefinition和ChildBeanDefinition之间的关系, 以及他们
的缺点, 从而Spring在之后的版本中引入了GenericBeanDefinition, 在Spring的生命周期中, 如果要实例化
一个bean则需要先将bean进行合并, 这样拿到的BeanDefinition才是信息完整的, 本篇文章就来聊聊Spring是
如何合并BeanDefinition的, 从而为后面的内容打下基础
```

### 源码步骤分析
#### getMergedLocalBeanDefinition入口
```java
protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) {
  // Quick check on the concurrent map first, with minimal locking.
  RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
  if (mbd != null) {
    return mbd;
  }
  return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
}

分析:
  在获取一个合并后的BeanDifinition的时候, Spring都会调用这个getMergedLocalBeanDifinition方法,
  可以看到, Spring会先从mergedBeanDefinitions中获取, 从而可以很号的联想到, 在对一个BeanDifinition
  进行合并完成后, Spring都会将合并后的BeanDifinition放入到这个Map中, 如果在缓存中没有拿到, 则先
  调用getBeanDefinition方法从beanDefintionsMap中获取原始的BeanDefinition, 如果对Spring扫描bean
  的代码有所了解的话, 就会明白当一个bean被扫描出来变成一个beanDefintion后就会放入到beanDefintionsMap
  中
```

#### getMergedBeanDefinition[没有父类BeanDefinition的情况]
```java
if (bd.getParentName() == null) {
  // Use copy of given root bean definition.
  if (bd instanceof RootBeanDefinition) {
    mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
  }
  else {
    mbd = new RootBeanDefinition(bd);
  }
}

分析:
  如果一个BeanDefinition没有设置parentName, 表示该beanDefinition是没有父BeanDefinition的, 此时
  如果这个BeanDefinition是RootBeanDefinition的实例或子类实例, Spring就会直接克隆一个一模一样的
  BeanDefinition, cloneBeanDefinition的代码很简单, 即new RootBeanDefinition(this), 如果不是
  RootBeanDefinition类型, 则创建一个RootBeanDefinition并将当前bd合并进去, 这时候肯定会有疑问,
  两个合并的代码不是一模一样吗, 其实不是, 如果一个BeanDefinition是RootBeanDefinition的子类, 那么
  调用克隆方法的时候, 就会new一个该子类, 然后开始克隆, 比如new ConfigurationClassBeanDefinition(this)
```

#### getMergedBeanDefinition(有父类BeanDefinition的情况)
```java
else {
  // Child bean definition: needs to be merged with parent.
  BeanDefinition pbd;
    String parentBeanName = transformedBeanName(bd.getParentName());
    if (!beanName.equals(parentBeanName)) {
      pbd = getMergedBeanDefinition(parentBeanName);
    }
    else {
      BeanFactory parent = getParentBeanFactory();
      if (parent instanceof ConfigurableBeanFactory) {
        pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
      }
  }

  // Deep copy with overridden values.
  mbd = new RootBeanDefinition(pbd);
  mbd.overrideFrom(bd);
}

分析:
  如果一个BeanDefinition有父类, 那么Spring就会获取到父类的beanName(因为可能存在别名, 所以调用了
  transformedBeanName方法), 如果父类的beanName和当前BeanName不一样, 说明是存在真正的父类的, 这
  个判断是用来防止由于程序员将当前beanName设置为当前BeanDefinition的父类而导致死循环的, 如果真正
  存在父类, Spring就会先将父类合并了, 因为父类可能还有父类, 该递归方法结束后就能获取到一个完整的父
  BeanDefinition了, 然后new了一个RootBeanDefinition, 将完整的父BeanDefinition放入进去, 从而初步
  完成了合并

  上面有一个else判断, 是对存在有父容器情况的处理, 对于Spring的父容器, 笔者也不太清楚, 就不展开讲解

  当完成了初步的合并后, Spring调用overrideFrom进行了深入的复制, 这里面的代码也很简单, 大家有兴趣
  可以看看, 就是大量的mbd.setXXX(bd.getXXX)
```

#### getMergedBeanDefinition[结尾]
```java
// Set default singleton scope, if not configured before.
if (!StringUtils.hasLength(mbd.getScope())) {
  mbd.setScope(RootBeanDefinition.SCOPE_SINGLETON);
}

// Cache the merged bean definition for the time being
// (it might still get re-merged later on in order to pick up metadata changes)
if (containingBd == null && isCacheBeanMetadata()) {
  this.mergedBeanDefinitions.put(beanName, mbd);
}

分析:
  如果这个新创建的RootBeanDefinition没有被设置Scope的话, 那么就是在合并的时候, 被合并的bd本身没有
  设置Scope属性, 此时Spring会默认设置其为单例, 再之后, 如果bean工厂允许缓存Bean的元数据的情况下,
  Spring就会将合并后的BeanDefinition存到mergedBeanDefinitions这个map中, 之后便可以从缓存中取得
  了, 需要注意的是, 再分析这个getMergedBeanDefinition方法的时候, 笔者跳过了containingBd这个变量
  的分析, 因为我也搞不懂是干嘛的, 但是通常情况下这个值都是null的
```

### 总结
```
对于BeanDefinition的合并, Spring都会创建一个新的RootBeanDefinition来进行接收, 而不是用原来的
BeanDefinition, 如果原始BeanDefinition没有父BeanDefinition了, 那么就直接创建一个RootBeanDefinition,
并将原始BeanDefinition作为参数传入构造方法中, 如果原始BeanDefinition存在BeanDefinition, Spring
除了会做上述的操作外, 还会调用overrideFrom方法进行深入的合并, 其实就是一系列的setXXX方法的调用而已,
在合并完成后, 对于合并后的BeanDefinition如果没有作用域, 则设置为单例, 并且将合并的BeanDefinition
放入到mergedBeanDefinitions这个map中缓存起来
```
