## 一、引入
```
在上篇文章中, 我们对spring中事务相关的对象组件进行了分析, 了解了这些基本组件的情况下, 我们再来看spring事务处理
流程的源码就轻松多了, 在spring事务处理流程中, 深入分析的仅仅会是开启事务这一块的流程, 但是大家不用担心, 如果能把
事务是如何开启的了解清楚, 再去看事务的提交、回滚就会非常轻松
```

## 二、事务源码分析
### 2.1、事务管理器接口
```java
public interface PlatformTransactionManager {
	TransactionStatus getTransaction(TransactionDefinition definition);

	void commit(TransactionStatus status) throws TransactionException;

	void rollback(TransactionStatus status) throws TransactionException;
}

在之前的分析中, 我们知道事务对象DataSourceTransactionObject会持有一个连接以及相关的回滚点操作, 将这个事务对象
运用起来实现完整的事务功能则正是利用PlatformTransactionManager事务管理器来完成的, 接口定义了三个方法:
    getTransaction: 根据事务的定义获取事务状态, 如果没有事务存在, 则创建一个事务, 并返回状态, 这个方法主要作
                    用在Transactional注解被AOP拦截以后, 根据解析Transactional注解获得当前事务的信息, 然
                    后利用这些信息创建事务, 创建事务后, 利用事务状态对象TransactionStatus来保存当前事务的
                    情况, 后续的事务流程中, 会对事务状态进行扭转, 所以事务状态对象是贯穿整个事务处理流程的
    commit: 利用事务状态对象完成事务的提交
    rollback: 利用事务状态对象完成事务的回滚

PlatformTransactionManager事务管理器定义了事务的功能, 其抽象类AbstractPlatformTransactionManager需要对
这些接口进行实现, 并提供模板, 子类来决定事务的开启、关闭等功能, AbstractPlatformTransactionManager同时要提
供事务传播行为操作, 所以AbstractPlatformTransactionManager实现的是通用功能, 不同的事务(比如JTA分布式事务、
普通的jdbc事务)开启、关闭的操作不一样, 不同事务继承AbstractPlatformTransactionManager来提供不同的实现类,
然后对通用功能的模板进行实现
```

### 2.2、事务拦截器
```java
事务管理器提供了事务操作, 创建事务、提交事务、回滚事务, 一个@Transactional注解的使用, 需要将这些操作整合起来,
正是利用了AOP功能, 所以将事务管理器的这些功能进行调度则是利用AOP完成的, AOP主要涉及到了两个类:
TransactionInterceptor extend TransactionAspectSupport, 其实真正实现AOP操作的是后者, 即父类, 子类在父
类的功能基础上, 提供了AOP对象获取源对象的功能, 因为在基于接口代理即JDK动态代理的情况下, AOP对象仅仅是
@Transactional标注类所在接口的其他实现类了, 在代理类中的切入点对象是没法获取到Transactional注解信息的, 只有
获取到源对象才可以, 所以TransactionInterceptor正是提供了获取源Class对象的功能, 真正的AOP操作还是父类完成的:

public class TransactionInterceptor extends TransactionAspectSupport {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取源Class对象
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// 采用父类的invokeWithinTransaction完成事务拦截器的功能
		return invokeWithinTransaction(invocation.getMethod(), targetClass, invocation::proceed);
	}
}

public abstract class TransactionAspectSupport {
    protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
                final InvocationCallback invocation) throws Throwable {
        TransactionAttributeSource tas = getTransactionAttributeSource();
        final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
        final PlatformTransactionManager tm = determineTransactionManager(txAttr);
        final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

        if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
            TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
            Object retVal;
            try {
                retVal = invocation.proceedWithInvocation();
            }
            catch (Throwable ex) {
                completeTransactionAfterThrowing(txInfo, ex);
                throw ex;
            }
            finally {
                cleanupTransactionInfo(txInfo);
            }
            commitTransactionAfterReturning(txInfo);
            return retVal;
        }

        else {
            .........事务管理器为CallbackPreferringPlatformTransactionManager的情况下的操作........
            这一块大家有兴趣可以研究下, 其实就是提供了事务操作时回调的功能而已, 在了解了基本的事务管理器的功能
            情况下, 去研究这一块会非常轻松
        }
    }
}


TransactionAttributeSource我们之前描述过了, 用于获取@Transactional注解信息的类, 通过调用其
getTransactionAttribute方法, 解析@Transactional注解为TransactionAttribute对象(其实是
RuleBasedTransactionAttribute)

调用determineTransactionManager方法决定事务管理器, 参数为TransactionAttribute, 因为@Transactional注解
可以指定事务管理器, 这里就是对@Transactional注解中的value/transactionmanager属性进行操作, 获取对应的事务
管理器PlatformTransactionManager

如果txAttr为空(表示执行的是@Transactional注解标注的类的非事务方法)或者事务管理器不是
CallbackPreferringPlatformTransactionManager(见上面的代码描述)的情况下, 则调用
createTransactionIfNecessary方法创建事务, 随后利用invocation.proceedWithInvocation()执行业务代码所在
的方法, 成功后则调用commitTransactionAfterReturning方法提交事务, 如果有异常则调用
completeTransactionAfterThrowing方法回滚事务, 在这些事务方法中, 会对txAttr为空的情况进行排除(非
@Transactional注解标注的方法)

createTransactionIfNecessary方法即调用PlatformTransactionManager.getTransaction方法来创建事务(如果有
传播行为也会在这里面处理), commitTransactionAfterReturning方法则调用PlatformTransactionManager.commit
方法提交事务, completeTransactionAfterThrowing方法则调用PlatformTransactionManager.rollback方法回滚
事务, 所以, TransactionInterceptor事务AOP拦截器及其父类TransactionAspectSupport的真正功能是拦截
@Transactional注解所在的方法, 并且利用事务管理器PlatformTransactionManager完成事务的调度功能
```

### 2.3、AbstractPlatformTransactionManager事务拦截器源码分析
#### 2.3.1、getTransaction获取事务
```java
public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException {
    Object transaction = doGetTransaction();

    if (definition == null) {
        definition = new DefaultTransactionDefinition();
    }

    if (isExistingTransaction(transaction)) {
        return handleExistingTransaction(definition, transaction, debugEnabled);
    }

    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
        throw new IllegalTransactionStateException(
                "No existing transaction found for transaction marked with propagation 'mandatory'");
    } else if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
            definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
            definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        SuspendedResourcesHolder suspendedResources = suspend(null);
        boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
        doBegin(transaction, definition);
        prepareSynchronization(status, definition);
        return status;
    } else {
        boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
        return prepareTransactionStatus(definition, null, true, newSynchronization, debugEnabled, null);
    }
}

上面这一块代码简化了一些跟主线无关的代码, 现在看起来非常清晰了, 首先是调用doGetTransaction获取事务对象, 如果不
存在事务对象就创建一个, 该方法是抽象方法, 由子类实现, 不同的事务管理器提供不同的事务对象, 我们通常使用的事务管理器
是DataSourceTransactionManager, 所以其创建的事务对象为: DataSourceTransactionObject

拿到当前的事务对象后, 开始判断, 如果当前已经存在了事务, 那么走handleExistingTransaction来进行处理, 根据不同
的事务传播行为, 会有不同的处理

如果当前不存在事务, 则判断当前事务传播行为是否是MANDATORY, 则抛出一个异常, 如果传播行为是
PROPAGATION_REQUIRED、PROPAGATION_REQUIRES_NEW、PROPAGATION_NESTED这三个, 则调用suspend挂起事务方法,
通常情况下, 走到这里的逻辑在suspend方法中都是啥也没做, 直接返回null的, 之所以要调用suspend是为了防止潜在的同步
行为, 或者说是非法情况下的同步行为, 同步行为是什么后面我们的小结有专门的讲解, 其实就是一些ThreadLocal变量, 这里
不了解同步行为没关系, 等看完整篇文章就会对同步行为有所了解了, 再往下, 创建一个事务状态, 事务状态会伴随着整个事务
的生命周期, 随后调用doBegin开启事务, doBegin是抽象方法, 由子类决定事务如何开启

如果不是以上几种隔离级别, 则走else逻辑, 创建了一个空的事务, 至于这样操作的原因, 根据代码中的注释描述, 是为了防止
潜在的同步行为, 同步行为是什么后面我们会进行分析
```

#### 2.3.2、doGetTransaction创建事务对象
```java
我们来看看DataSourceTransactionManager的doGetTransaction方法:
protected Object doGetTransaction() {
    DataSourceTransactionObject txObject = new DataSourceTransactionObject();
    txObject.setSavepointAllowed(isNestedTransactionAllowed());
    ConnectionHolder conHolder =
            (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
    txObject.setConnectionHolder(conHolder, false);
    return txObject;
}

创建一个DataSourceTransactionObject对象, 然后获取ConnectionHolder, 并且设置到事务对象中, 在Spring中, 数
据库连接以ThreadLocal的形式保存在线程对象中, 通常情况下TransactionSynchronizationManager.getResource方
法是返回null, 只有在出现了事务嵌套的情况下, 即一个@Transactional标注的方法中调用了另外一个@Transactional标
注的方法, 这种情况下才能从线程本地变量中获取到连接, 所以在一开始调用doGetTransaction时获取到的事务对象, 其实是
没有保存着连接对象的, 所以该方法兼容了事务嵌套的情况

通常情况下这段代码产生的结果为:
    DataSourceTransactionObject txObject = new DataSourceTransactionObject();
    txObject.setSavepointAllowed(isNestedTransactionAllowed());
    txObject.setConnectionHolder(null, false);

我们接下来就会对TransactionSynchronizationManager进行详细的说明, 等看完后面的分析后, 就会清晰的读懂上面这段
话的意思了, 事务嵌套的情况下才会出现拿到ConnectionHolder的情况
```

#### 2.3.3、TransactionSynchronizationManager
```java
public abstract class TransactionSynchronizationManager {
	private static final ThreadLocal<Map<Object, Object>> resources =
			new NamedThreadLocal<>("Transactional resources");

	private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
			new NamedThreadLocal<>("Transaction synchronizations");

	private static final ThreadLocal<String> currentTransactionName =
			new NamedThreadLocal<>("Current transaction name");

	private static final ThreadLocal<Boolean> currentTransactionReadOnly =
			new NamedThreadLocal<>("Current transaction read-only status");

	private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
			new NamedThreadLocal<>("Current transaction isolation level");

	private static final ThreadLocal<Boolean> actualTransactionActive =
			new NamedThreadLocal<>("Actual transaction active");
}

resources: 保存了当前线程中的连接对象, 通常情况下形成的是Map<DataSource, ConnectionHolder>映射, 在2.3.2
小结中, 我们调用的TransactionSynchronizationManager.getResource方法就是从这个ThreadLocal中获取连接对象,
利用obtainDataSource获取当前事务管理器中的数据源, 然后利用这个数据源获取保存着的连接对象ConnectionHolder

synchronizations: TransactionSynchronization接口提供的是事务回调功能, 在完成事务的前后会调用
TransactionSynchronization中对应的回调方法, 其功能就像springmvc中的HandlerInterceptor, 里面有
beforeCommit、afterCommit这样的回调方法, 如果对springmvc中的拦截器有所了解的话, 就会发现两者实现的效果其实
是一样的, 这里用一个Set保存了当前事务的所有回调功能, 在事务完成前后会一个个遍历这个回调接口的实现类, 调用其中对应
的回调方法

currentTransactionName: 利用ThreadLocal保存当前线程中事务的名称

currentTransactionReadOnly: 利用ThreadLocal保存当前线程中的事务是否只读的

currentTransactionIsolationLevel: 利用ThreadLocal保存当前线程中事务的隔离级别

actualTransactionActive: 利用ThreadLocal保存当前线程是否有启动的事务
```

#### 2.3.4、doBegin开启事务
```java
我们来看看DataSourceTransactionManager的doBegin方法:
protected void doBegin(Object transaction, TransactionDefinition definition) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    Connection con = null;

    if (!txObject.hasConnectionHolder() ||
            txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
        Connection newCon = obtainDataSource().getConnection();
        txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
    }

    txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
    con = txObject.getConnectionHolder().getConnection();

    Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
    txObject.setPreviousIsolationLevel(previousIsolationLevel);

    if (con.getAutoCommit()) {
        txObject.setMustRestoreAutoCommit(true);
        con.setAutoCommit(false);
    }

    prepareTransactionalConnection(con, definition);
    txObject.getConnectionHolder().setTransactionActive(true);

    int timeout = determineTimeout(definition);
    if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
    }

    if (txObject.isNewConnectionHolder()) {
        TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
    }
}

到达doBegin的有两种情况, 一种是第一次调用@Transactional方法的情况, 这个时候事务对象
DataSourceTransactionObject中还没有连接对象ConnectionHolder, 第二种是事务嵌套的情况, 事务嵌套的时候也需要
利用doBegin来开启事务, 这个时候事务对象中就已经有连接对象了, 所以我们在看这一块的源码的时候要从两个角度来看

如果没有连接对象, 那么就调用obtainDataSource方法获取数据源并且获取连接对象, 然后创建ConnectionHolder并放入
到事务对象中

调用setPreviousIsolationLevel方法保存之前事务的隔离级别, 在嵌套的情况下, 里层的事务执行完毕后要恢复这些数据

调用con.setAutoCommit(false)开启事务(不自动提交就是开启事务), prepareTransactionalConnection方法是对
数据库连接进行一定的配置, protected修饰的方法, 有默认实现, 子类也可以自定义实现, 随后是一些初始化操作

如果当前事务是一个新的ConnectionHolder, 即嵌套事务的最外层, 或者压根就没有嵌套事务的情况下, 调用bindResource
方法, 利用key为DataSource, value为ConnectionHolder将数据绑定到当前线程的本地变量中, 即2.3.3小结我们分析的
TransactionSynchronizationManager.resources这个ThreadLocal

到这里你会发现, 开启事务后, 利用ThreadLocal维护了连接对象, 后面的操作就可以利用这个ThreadLocal来获取连接对象
了, 所以, 现在大家应该能够想到, spring整合mybatis的时候, 只需要在mybatis获取连接对象的那一步提供一个自定义的
实现类, 然后在这个实现类中利用TransactionSynchronizationManager来获取连接对象, 这样就完成了整合了
```

## 3、总结
```
到目前为止, 我们对开启事务的源码进行了分析, 在分析的过程中, 我们了解到了事务管理器提供了事务的功能, 开启事务、提
交事务、回滚事务等操作, 整个事务的生命周期中会利用TransactionStatus来变更事务的状态, 每一个事务管理器有自己独立
的数据源Datasource对象, 事务的开启, 连接的创建都是基于这个数据源对象的

事务管理器提供了事务管理的功能, 将这些功能调度起来, 使得@Transactional注解生效正是利用了AOP, 利用
TransactionInterceptor来拦截注解, 并利用事务管理器来真正完成事务的操作, 即其中的try {} catch{}操作, 调用
PlatformTransactionManager.getTransaction方法来创建事务, 调用PlatformTransactionManager.commit方法
提交事务, 调用PlatformTransactionManager.rollback方法回滚事务

getTransaction方法是AbstractPlatformTransactionManager实现的, 所以提供了公共的功能即事务传播行为的功能,
然后提供了模板给子类实现, 子类决定事务对象的获取(不同的子类事务对象可以不一样, 上层利用Object接收), 同时子类决定
如何开启事务, 与此同时, 还提供了事务回调的功能, 利用TransactionSynchronization接口来定义回调功能, 里面提供了
beforeCommit、afterCommit、beforeCompletion、afterCompletion等回调方法, 在事务提交的时候会进行调用

整个事务的流程中, 利用TransactionSynchronizationManager来实现事务相关信息的上下文保存, 其里面就是利用线程本
地变量ThreadLocal来完成保存操作的, 这样做的好处是可以在当前线程的任何地方获取到连接对象, spring整合mybatis中
连接的获取就是基于这个功能来完成的

当然, 事务还有其他方法, 但是基于本文提供的思路下, 对这些组件有了清晰的认识, 去研究其他方法的逻辑会非常的轻松, 这
里就不再进行详细的展开了
```
