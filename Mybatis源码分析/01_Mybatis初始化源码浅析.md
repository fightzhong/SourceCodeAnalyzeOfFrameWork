### 引入
```
本系列文章最终的目标是为了分析spring动态数据源的原理, 相信大家在公司的开发中应该都会遇到一个项目中出现多个数据库连
接的情况, 以我遇到的一种情况来说, 用户根据id分布在不同的数据库, 每个数据库中的表结构一模一样, 这就会导致, 在执行
业务代码的时候, 不同的用户需求在不同的数据库中执行对应的sql, 即在mybatis作为持久层框架的情况下, 一个mapper可能
会在不同的情况下, 对不同的数据库进行操作, 这就需要我们有能够执行动态数据源的功能, 而在这其中, 最重要的一部分就是
事务了(不是分布式事务, 应该属于动态事务), @Transactional中标注的业务逻辑, 在不同的数据源下需要执行不同数据库的
事务, 为了能够清晰的了解到spring-mybatis是如何联动实现动态数据源, 以及实现动态事务的原理, 我们从mybatis的源码
开始分析, 到spring整合mybatis的原理, 到spring事务原理, 最终来展示动态数据源情况下动态事务的原理

注意事项, mybatis源码相对于之前我分析的spring、springmvc的源码来说, 会更加的简单, 但是我不会把每一个细节都分
析的非常底层, 而是从一个整体的流程出发进行分析, 在对mybatis有一个整体的认知之后, 如果想深入的了解某一块的内容, 
那么会变得非常轻松, 举个例子, 对于如何将一个<select>这样的标签解析并得到对应的实体类对象这样的内容不会进行分析
(这里面其实就是一些xml的解析以及对嵌套查询的处理而已)
```

### 配置文件整体概览
#### 简单的描述
```
我们以xml格式下的mybatis文件进行分析, 这是相对比较传统的, 而目前大家都是基于springboot进行开发, 在这种情况下,
只不过是将对mybatis配置文件的解析变成了对application.yml文件的解析, 进而得到对应的mybatis配置对象而已, 之后
我们在分析spring整合mybatis的时候, 也会分析这一块, 从xml文件入手, 我们之后会能够更加的清晰springboot是如何
剔除了mybatis-config这样的配置文件的
```

#### 配置文件
```xml
------ mybatis-con.xml ------
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
		  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
		  "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
	<environments default="development">
		<environment id="development">
			<transactionManager type="JDBC"/>
			<dataSource type="POOLED">
				<property name="driver" value="com.mysql.cj.jdbc.Driver"/>
				<property name="url" value="jdbc:mysql://dev1-linux.pospal.cn:3306/pospal?serverTimezone=GMT%2B8&amp;useUnicode=true&amp;characterEncoding=UTF-8&amp;useSSL=true&amp;allowMultiQueries=true"/>
				<property name="username" value="xxx"/>
				<property name="password" value="xxx"/>
			</dataSource>
		</environment>
	</environments>
	<mappers>
		<mapper resource="mapper/CustomerMapper.xml"/>
	</mappers>
</configuration>


------ CustomerMapper.xml ------
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.fightzhong.mapper.CustomerMapper">
	<select id="selectRemarksById" parameterType="int" resultType="String" >
		select remarks
		from customer where id = #{id}
	</select>
</mapper>
```

#### config文件分析
```
其实mybatis在初始化的时候就是对上面这些配置文件进行xml解析而已, 将它们解析成一个个的java对象存储起来, 首先我们
来看看mybatis-con.xml这个文件, configuration对象, 故名思义, 就是mybatis的配置, 在mybatis初始化的时候, 会
将其解析成一个Configuration对象, 而environment对象也是一样, 会将其解析成一个Environment对象, environments
标签中可以配置多个environment标签, 通过default来表示默认生效哪一个, 在environment中, 则表示的是当前环境下的
数据库配置信息, 其中包括事务工厂(用于创建事务的factory)、数据源等, 所以以上配置映射成java对象即如下:
class Configuration {
    private Environment environment;

    private Map<String, MappedStatement> mappedStatements;
}

class Environment {
    private TransactionFactory transactionFactory;

    private DataSource dataSource;
}

非常清晰的映射关系, 一个环境里面包含了数据源以及事务工厂, 如果不是很理解事务工厂的用处, 可以先不用着急, 我们后面
的文章会进行详细的分析, 在本小节, 只是为了告诉大家, 一个mybatis的配置文件, 最终其实是以java对象保存的, 而xml
中的标签层级, 其实就是对应了对象中的属性层级
```

#### mapper文件分析
```
对于xxxMapper.xml这些文件, mybatis中通过mapper标签的namespace + select / update等标签中的id构成一个唯一
标识(假设为statementId), 每一个sql标签以MappedStatement的对象的形式保存在Configuration对象中, 在上面的
Configuration中, 以statementId -> MappedStatement 形成一个映射关系, 而MappedStatement中则存储了一个
select等类型的标签中的所有内容, 比如parameterType, resultType, resultMap, sql等, 当我们在触发sql执行的时
候, 即通过statementId找到对应的MappedStatement, 取出里面的sql来执行, 然后利用resulthandler以及
resultType或者resultMap等信息对结果集进行封装映射, 最后返回

在日常的开发中, 我们面向接口编程, 会定义一个个的Mapper接口类, 当执行这些接口方法的时候, 代理对象会利用接口的全路
径类名 + 接口方法名构成一个statementId, 进而获取到对应的MappedStatement, 然后从中提取sql, 执行sql, 最后将
结果集利用MappedStatement中的保存的resultMap等信息映射成对应的java对象
```

#### 总结
```
mybatis-con.xml这样的配置文件, 以Configuration对象存储, 表示整个mybatis的配置文件, 里面包含了一个环境(当前
mybatis中对应的数据源以及事务工厂)以及所有的Mapper文件中一个个sql标签解析出来后的MappedStatement对象, xml文
件的层次对应了java对象中的属性层次, MappedStatement中包含了一个sql标签需要执行的所有信息, 包括sql、动态sql解
析需要的数据以及通过jdbc查询到的结果集如果处理的信息
```

### 配置文件初始化源码分析
#### 代码引入
```java
public static void main(String[] args) {
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsStream( "mybatis-con.xml" ) );
    SqlSession sqlSession = sqlSessionFactory.openSession();
    List<Object> objectList = sqlSession.selectList("com.fightzhong.mapper.CustomerMapper.selectRemarksById", 12602611);
}

通过上面三行代码, 我们完成了mybatis的初始化, 以及执行了上面mapper中的一个select语句, 可以看到, 通过将
mybatis-con.xml这个配置文件以流的形式读取, 然后利用这个文件流开始读取mybatis的配置, 我们可以先不用理会什么是
SqlSession以及SqlSessionFactory, 在mybatis源码分析的最后, 我才会跟大家说明这两个东西是什么, 如果没有对底层
依赖的组件有一个清晰的了解, 那么我们也不会深刻的了解到这两个类的真正作用! 不过SqlSessionFactoryBuilder.build
方法却是我们需要进行分析的, 注意, 不要因为不知道SqlSessionFactoryBuilder和SqlSession是什么而感到烦恼!
```

#### build方法开始构建Configuration对象
```java
public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
    return build(parser.parse());
}

可以看到, build方法调用到最后, 就是创建了一个XMLConfigBuilder, 然后调用它的parse方法创建了Configuration对
象而已, build方法的参数inputStream就是配置文件的文件流, environment字段就是表示我们期望生效<environments>
标签中的哪个环境
```

#### parse方法开始构建Configuration对象 
```java
public Configuration parse() {
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
}

private void parseConfiguration(XNode root) {
    propertiesElement(root.evalNode("properties"));
    Properties settings = settingsAsProperties(root.evalNode("settings"));
    loadCustomVfs(settings);
    loadCustomLogImpl(settings);
    typeAliasesElement(root.evalNode("typeAliases"));
    pluginElement(root.evalNode("plugins"));
    objectFactoryElement(root.evalNode("objectFactory"));
    objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
    reflectorFactoryElement(root.evalNode("reflectorFactory"));
    settingsElement(settings);
    environmentsElement(root.evalNode("environments"));
    databaseIdProviderElement(root.evalNode("databaseIdProvider"));
    typeHandlerElement(root.evalNode("typeHandlers"));
    mapperElement(root.evalNode("mappers"));
}

非常清晰的xml解析过程, 最顶层解析configuration标签, 得到一个root(XNode), 如果有接触过xml解析, 或者有学过前
端dom编程的同学就不会感到陌生, 就像前端的document.getxxx()方法的调用, 得到根节点后, 开始解析里面的一个个标签

properties: 解析properties标签, 将里面的一个个键值对取出来以Properties对象的形式保存到Configuration中

settings: 解析settings标签, 将一个个的mybatis配置设置到Configuration中, loadCustomVfs、
          loadCustomLogImpl都是对settings标签的解析, settingsElement方法也是, 这个方法就是读取settings
          标签中的一个个配置, 然后调用configuration.setXXXX()方法进行设置

typeAliases: 别名的解析

plugins: mybatis插件的解析, 如果有使用过mybatis插件, 那么对于这里面的代码就会比较清晰, 其实就是一个个的拦截器
        而已, 大家有兴趣可以做一下mybatis插件的实践, 从而能够更加清楚的认识插件的作用

objectFactory: 在mybatis对sql执行完毕之后, 会对结果集进行处理, 创建一个个的对象并返回, 创建对象就是利用反射
                完成的, objectFactory即对象工厂, 完成对象反射的创建, 接口方法非常简单, 就是提供一个Class对
                象, 然后根据这个Class对象创建对应的实体类对象, 默认是DefaultObjectFactory, mybatis提供了
                扩展, 允许开发者自己定义对象创建的工厂类

objectWrapperFactory: 创建ObjectWrapper的工厂类, ObjectWrapper即对一个对象进行了包装, 然后利用该类中提供
                        的反射方法对该对象进行反射属性的操作, 比如setXXX, getXXX (如果有了解过spring源码
                        的话, 就会发现, 在spring创建bean对象的时候也有一个类似的功能类), 默认的实现类为
                        DefaultObjectWrapperFactory, 里面没有任何功能, 该类是mybatis提供给开发者的扩展
                        功能

reflectorFactory: ReflectorFactory工厂的解析, 用于创建Relector对象, 每个Relector对象中包含了一个class对
                    象, 以及这个类中所有的set、get方法, set方法参数集合、get方法参数集合, 这个类其实就是反
                    射工具类, 只需要提供一个对应的对象, 然后就能利用Relector完成反射方法的调用, 同样有默认的
                    实现DefaultReflectorFactory, 其实ObjectWrapper最还是通过Reflector来完成反射方法的
                    调用的

ObjectFactory、objectWrapper、Reflector是mybatis底层反射的核心功能类, 提供了完整的反射的功能, 我们这里仅仅
是引入这些接口的功能而已, 因为在mybatis中可以通过配置文件提供自定义的实现类, 所以我们简单的提及一下, 大家有兴趣
可以深入了解下这些类的功能

environments: 该标签就是解析当前mybatis中的数据源环境了, 完成了Environment对象的创建(同时完成了
              TransactionFactory和DataSource的创建, 前者是事务工厂, 用来创建事务的, 后面我们会详细分析,
              前面我们也有简单提到)

databaseIdProvider: 跳过, 不太清楚是干嘛的.....

typeHandlers: 自定义typeHandlers的的解析, typeHandler是用于将jdbc类型和java类型进行映射的关键功能类, 
              mybatis为目前java这边的绝大部分内置类型以及与对应的jdbc类型映射关系提供了默认的typeHandler,
              存放在typeHandlerRegistry中, 该对象在初始化的同时会创建默认的typeHandler,  typehandler作
              用在将java类型转成jdbc类型(即参数映射)或者将jdbc类型转成java类型(即结果集映射)中, 之前在项目中
              刚好有需要将数据库中以逗号分隔的字符串解析成一个List<String>, 为了同时处理insert和select, 当
              时我采用了typeHandler完成了这个功能

mappers: 解析一个个的mapper文件, 将mapper文件中的一个个sql标签解析成MappedStatement对象并存储在
        Configuration中, 为了不影响主线, 解析mapper文件的代码我们不进行深入分析, 之后如果有机会再用文章来描
        述, 里面涉及到了ResultMap标签等的解析, 相对比较复杂
```


### 总结
```java
我们从Mybatis配置文件、mapper文件出发, 引出了这些xml文件映射到java对象的整体情况, 随后对mybatis初始化代码进行
了简单的分析, 其实就是将一个个的dom树解析成java对象来表示, 至此, 我们知道了Mybatis配置的整体模型:
    class Configuration {
        // 环境配置, 表示当前mybatis中生效的数据源对象以及事务创建的工具类
        private Environment environment;

        // 每一个sql标签的映射关系, namespace + sql标签id构成key, sql标签中的详细信息构成MappedStatement对象
        private Map<String, MappedStatement> mappedStatements;
    }

    class Environment {
        // 事务创建工厂工具类
        private TransactionFactory transactionFactory;

        // 生效的数据源
        private DataSource dataSource;
    }

到此为止, 我们对mybatis的整体结构有了一些认识, 过程中我们接触到了SqlSessionFactory、SqlSession对象, 如果不
清楚这两个类的功能, 一定不要烦恼, 这个在mybatis源码分析的最后再来引出这两个对象的真正作用~!
```
