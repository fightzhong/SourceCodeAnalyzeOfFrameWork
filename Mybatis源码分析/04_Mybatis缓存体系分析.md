## 一、引入
```
在Mybatis中, 有两层缓存, 我们不区分这两层缓存哪个叫一级缓存, 哪个叫二级....我把他们分为两种缓存, 一种是Mapper
级别的缓存, 一种是session级别的缓存, 接下来我们细说这两种缓存
```

## 二、Session级别缓存
### 2.1、缓存对象的初始化过程
```java
在上篇文章中, 我们分析了SqlSession的构建过程, 首先需要明白的一点是, 一个SqlSession有一个唯一对应的Connection
数据库连接对象, 其次, 一个SqlSession有自己唯一的Executor, 即在上篇文章中我们有分析到, 会先创建一个Executor,
然后利用这个Executor创建SqlSession, 创建Executor的代码如下:
private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
    final Environment environment = configuration.getEnvironment();
    final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
    Transaction tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
    final Executor executor = configuration.newExecutor(tx, execType);
    return new DefaultSqlSession(configuration, executor, autoCommit);
}

public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
        executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
        executor = new ReuseExecutor(this, transaction);
    } else {
        executor = new SimpleExecutor(this, transaction);
    }
    if (cacheEnabled) {
        executor = new CachingExecutor(executor);
    }
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
}

通常情况下, 我们会创建SimpleExecutor(创建SqlSession的时候可以修改ExecutorType来实现创建其他的Executor),
cacheEnabled默认为true, 则利用装饰器模式创建了CachingExecutor, 而CachingExecutor是Mapper级别的缓存, 后
面我们会详细分析, 而Session级别的缓存就藏在了SimpleExecutor的创建中:
public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
}

public abstract class BaseExecutor implements Executor {
    protected PerpetualCache localCache;
    protected BaseExecutor(Configuration configuration, Transaction transaction) {
        this.transaction = transaction;
        this.localCache = new PerpetualCache("LocalCache");
    }
}

如果有看过第二篇文章的同学, 对localCache自然就不会陌生, 在BaseExecutor执行sql之前会进行缓存操作, 这就是所谓
的Session级别缓存, 即创建了一个普通的Map缓存而已
```

### 2.2、缓存的使用分析
```java
到此为止, 我们知道了, 一个SqlSession有唯一的Executor, 一个Executor(通用的BaseExecutor)有唯一的Cache对象,
Session级别缓存的由来因为Session关闭(等于数据库连接关闭)的时候, 缓存也就失效了, 我们再来看看缓存的使用, 下面的
代码其实在第二篇文章已经分析过了:
# BaseExecutor中的代码
public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
    if (list != null) {
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
    } else {
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }
    return list;
}

private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
        list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
        localCache.removeObject(key);
    }
    localCache.putObject(key, list);
    return list;
}

直接利用localCache.getObject方法获取缓存数据, 如果不为空, 则表示拿到了缓存, 这个时候调用
handleLocallyCachedOutputParameters方法针对存储过程的情况进行一定的处理, 如果没有拿到缓存, 则调用
queryFromDatabase方法

queryFromDatabase方法先是往缓存中放入了一个占位对象, 当数据库操作完成后, 再去更新这个缓存, 放入的占位对象与我
们期望接收的对象是类型不匹配的, 如果在并发情况下, 一个SqlSession同时被多个线程调用, 对于同一个Sql, 那么在上面
获取缓存的时候就可能获取到占位对象, 这个时候强转为我们期望的java实体类的时候, 就会报类型转换异常了, 在我的理解基
础上, Mybatis之所以利用占位对象, 就是用来保证一个SqlSession被多个线程调用时缓存的线程安全性的, 如果大家有其他
的理解, 可以在评论中一起交流一下

在上面的代码中, 我省略了queryStack的内容(第二篇文章有分析)以及一些跟主线没有关联的代码
```

### 2.3、总结
```
SqlSession作为接口层, 有唯一的Executor, 一个Executor有唯一的localCache缓存, 所以Session级别的缓存指的就是
BaseExecutor中的缓存, 当session关闭的时候(即数据库连接关闭时), session缓存就会失效了, 在此大家也可以联想到,
spring在非事务情况下, 一个线程内多次执行Mapper的同一个方法时, session缓存会无效, 因为每次执行Mapper方法的时候
session都是重新创建的, 只有在事务的情况下, 整个事务中才是同一个SqlSession, 这种情况下session缓存才是有效的
```

## 三、Mapper级别缓存
### 3.1、缓存对象的初始化
```java
在上面的分析中, 我们知道Mapper级别的缓存其实就是CacheExecutor, 通过装饰者模式来完成对SimpleExecutor的增强,
所以我们来看看CacheExecutor的源码:

public class CachingExecutor implements Executor {
  private final Executor delegate;
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }
}

CacheExecutor中所有的数据库操作都是通过delegate来完成的, delegate在上面的分析中其实就是SimpleExecutor, 而
CachingExecutor利用TransactionalCacheManager来完成Mapper级别缓存的处理

Mapper级别的缓存会涉及到多个SqlSession的访问, 即使一个Session关闭了, 缓存仍然是可以用的, 这个是跟Mapper绑定
的, 所以Mapper级别的缓存必须要考虑到的一个点就是事务, 只有当事务提交的时候, 这个缓存才能真正的生效, 后面其他的
Session访问时才能拿到缓存数据, 而CachingExecutor则是利用TransactionalCacheManager来完成缓存功能的

每一个MappedStatement的创建都是对Mapper解析的结果, 大家有兴趣可以去研究下Mapper文件的解析过程, 在这里我简单的
提及一下, 每一个Mapper文件的解析都会创建一个对应的解析器对象, 在这个解析器对象中会初始化一个Cache对象, 之后创建
MappedStatement的时候, 就会将这个与Mapper文件唯一对应的Cache对象传入MappedStatement中, 所以称为Mapper级别
的缓存, 即这个Mapper下所有解析的MappedStatement都会共享一个Cache对象, 下面我简单的贴出相关代码, 解析Mapper
文件的具体细节大家有兴趣可以去研究下:
private void mapperElement(XNode parent) throws Exception {
    InputStream inputStream = Resources.getUrlAsStream(url);
    XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
    mapperParser.parse();
}

public class XMLMapperBuilder {
    private final MapperBuilderAssistant builderAssistant; // 保存了一个Mapper文件下公共的缓存对象

    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    }
}

public class MapperBuilderAssistant {
    private Cache currentCache;
}

public class MappedStatement {
    // 来源于MapperBuilderAssistant.currentCache
    private Cache cache;
}
```

### 3.2、缓存原理分析
```java
现在我们清楚了, 一个Mapper文件中解析出来的所有MappedStatement对象会共享一个Cache对象, 所以需要保证在事务的情
况下缓存是正常的, 做法其实也比较简单, 就是在事务提交的时候再将缓存设置到MappedStatement中的Cache对象中:

public class CachingExecutor implements Executor {
    private final TransactionalCacheManager tcm = new TransactionalCacheManager();

    public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, 
                                    ResultHandler resultHandler, CacheKey key, BoundSql boundSql) {
        Cache cache = ms.getCache();
        if (cache != null) {
            List<E> list = (List<E>) tcm.getObject(cache, key);
            if (list == null) {
                list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
                tcm.putObject(cache, key, list); 
            }
            return list;
        }
        return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
    }
}

可以看到, 真正的数据库操作是利用delegate来完成的, 而在数据库操作前利用tcm获取缓存, 在数据库操作后利用tcm将数据
库查出来的数据缓存起来, 但是这个putObject不是直接将缓存放入到MappedStatement.cache中的, 而是先放在
TransactionalCacheManager中, 当调用commit方法的时候再将缓存刷新到Cache对象中, 在query方法的开始, 则利用
MappedStatement来获取当前Mapper文件共享的缓存对象, 然后利用这个缓存对象操作TransactionalCacheManager,
接下来我们来看看TransactionalCacheManager的结构

public class TransactionalCacheManager {
    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

    public Object getObject(Cache cache, CacheKey key) {
        return getTransactionalCache(cache).getObject(key);
    }

    public void putObject(Cache cache, CacheKey key, Object value) {
        getTransactionalCache(cache).putObject(key, value);
    }

    private TransactionalCache getTransactionalCache(Cache cache) {
        return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
    }
}

维护了一个Cache对象到TransactionalCache对象的Map映射, 在事务提交之前操作的缓存对象都是TransactionalCache,
而不是Cache, 在事务提交的时候才会将TransactionalCache中的数据刷新到Cache中, 我们先来看看提交事务和回滚事务时
CacheExecutor和TransactionalCacheManager的操作:
public class CachingExecutor implements Executor {
    public void commit(boolean required) throws SQLException {
        delegate.commit(required);
        tcm.commit();
    }

  public void rollback(boolean required) throws SQLException {
    try {
      delegate.rollback(required);
    } finally {
      if (required) {
        tcm.rollback();
      }
    }
  }
}

先是利用delegate对象完成对应的commit、rollback操作, 然后才是利用tcm完成缓存的放入和清除:
public class TransactionalCacheManager {
    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
        txCache.commit();
        }
    }

    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
        txCache.rollback();
        }
    }
}

所以我们来看看TransactionalCache的getObject、putObject、commit操作:
public class TransactionalCache implements Cache {
    private final Cache delegate;
    private final Map<Object, Object> entriesToAddOnCommit;

    public Object getObject(Object key) {
        Object object = delegate.getObject(key);
        if (clearOnCommit) {
            return null;
        } else {
            return object;
        }
    }

    public void putObject(Object key, Object object) {
        entriesToAddOnCommit.put(key, object);
    }

    public void commit() {
        flushPendingEntries();
    }
}

delegate就是Mapper文件中的共享缓存对象了, getObject其实就是从里面拿缓存数据, putObject则不是往delegate中
放缓存数据, 而是往entriesToAddOnCommit中放入缓存数据, 只有当commit方法调用的时候, 才会调用
flushPendingEntries方法将entriesToAddOnCommit中的缓存数据刷新到Cache对象中:
private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
        delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
        if (!entriesToAddOnCommit.containsKey(entry)) {
            delegate.putObject(entry, null);
        }
    }
}
```

### 3.3、总结
```
Mapper级别的缓存指的是所有该Mapper文件解析出来的MappedStatement对象共享一个Cache对象, Mapper级别的缓存需要
考虑事务的情况, 所以利用了一个TransactionCache来保存在事务提交之前产生的缓存数据, 当事务提交的时候才会将临时保
存的数据真正的刷新到Cache对象中, 为了将Cache对象和TransactionCache之间进行关联, 引入了
TransactionalCacheManager对象, 里面其实就是利用一个Map进行了关联而已
```
