## @ComponentScan
- basePackages, value
```
定义组件被扫描的包, 该包下以及所有子包下的被@Component等注解标识的都会被扫描进去
```

- basePackageClasses
```
作用跟basePackages类似, 扫描该属性定义的类所在的包及其所有子包下的被@Component等注解标识的类
```

- nameGenerator
```
beanName的生成计划, 一个bean对象的默认beanName为该类对象的名字, 并将该名字开头小写, 如果我们要自定义beanName的生成计划
的话, 可以创建一个BeanNameGenerator接口的实现类, 然后重写其generateBeanName方法, 在spring注解中, 默认的生成计划类为
AnnotationBeanNameGenerator
``` 

- scopeResolver
```
组件的作用范围解析器, 就是利用这个解析器来解析该bean的作用范围的, 如果我们需要自己定义解析器, 需要实现ScopeMetadataResolver
接口, 并重写resolveScopeMetadata方法, 在默认的解析器中, 会对bean对象的查看是否有Scope注解, 如果有则采用该注解中规定的
值,如果没有就返回一个默认的ScopeMetadata对象, 即默认为单例
```
    
- scopedProxy
```
表示是否生成该bean对象的代理类对象, 默认是不生成的, 如果想要生成可以选择基于接口的代理还是基于类的代理(cglib)
```
    

- resourcePattern
```
用于指定符合组件检测条件的类文件, 在basePackages, value, basePackageClasses中已经定义了扫描的根路径, 默认是扫描该根路
径及其子包中的所有类, 因为resourcePattern默认为"**/*.class", 我们可以定义只扫描当前包的类对象, 即可以定义为"*.class"
```

- lazyInit
```
对于一个bean对象是否延迟加载
```

- useDefaultFilters
```
过滤器可以有多个, 但是有一个默认的过滤器, 其类型是FilterType.ANNOTATION, 即表示通过注解的方式来判断一个类是否被加载进
IOC容器, 默认情况下只有@Component注解及其衍生注解会被加载进来
```
    
- includeFilters
```java
需要被包含的bean, 举个例子, 我们设置了扫描路径后:
includeFilters = @ComponentScan.Filter( type = FilterType.ASSIGNABLE_TYPE, value = UserService1.class )

如上述表示, 过滤器的类型是ASSIGNABLE_TYPE, 表示根据类型来确定一个类是否被包含进来, 比如UserService1就会被加载进IOC容器
```

- excludeFilters
```java
需要被排除的bean, 比如需要排除Service注解标注的类型, 则可以将该值设置为
@ComponentScan.Filter( type = FilterType.ANNOTATION, value = Service.class )
```

- @ComponentScan.Filter
```
ComponentScan中的内部注解:
    type: 表示过滤的类型, 默认是注解类型, 所以该注解的value我们可以传入Service.class, 如果需要自定义过滤类型, 那么这里
          的值应该是FilterType.CUSTOMER, ASSIGNABLE_TYPE表示通过给定的类class对象来过滤, ASPECTJ表示通过切入点表达式
          的方式来进行过滤, REGEX表示对一个类通过正则表达式的方式来进行过滤, CUSTOM表示自定义过滤的类型, 则需要自己实现
          TypeFilter接口, 并重写match方法

    value/classes: 在默认的过滤类型中, 即注解类型, 该属性的值为需要被过滤的注解类, 如果是自定义注解, 那么这里传入的就应该
                    是自定义的过滤规则了, 自定义过滤规需要实现TypeFilter接口, 一般采用实现其抽象类
                    AbstractTypeHierarchyTraversingFilter来定义, 而不是直接实现TypeFilter, 自定义规则中, 我们可以
                    利用一个自定义注解来标识该类是否被扫描, 如果有该注解则不被扫描, 这些逻辑都需要写入match方法
```

## @Bean
```
作用域:
    <1> ElementType.METHOD
    <2> ElementType.ANNOTATION_TYPE

ElementType.METHOD：
    作用在方法上的时候, 方法的返回值对象会被存入IOC容器中, 该对象的唯一标识是该方法的名字, 如果有方法重载, 则只有最后一个
    方法才能生效

ElementType.ANNOTATION_TYPE:
    作用在注解上的时候, 被@Bean注解标识的注解则会拥有@Bean的功能, 但是局限性是不能通过value来命名对象的唯一标识了

autowireCandidate：
    标识该bean对象是否允许@Autowired自动注入, 默认为true即允许, 如果设置为false, 则可以通过@Resource来进行引入

initMethod:
    bean对象被创建时调用, 由于是用于@Bean注解中, 所以在@Bean注解标识的方法上可以实现这些初始化工作, 而不必要用该属性来
    指定调用方法的, 所以可用可不用

destroyMethod：
    容器被销毁的时候, 该bean对象的destroyMethod方法被调用
```


## @Import
```
作用域:
    ElementType.TYPE

作用一: 导入其他的配置类, 同时有扫描一个类, 并放入IOC容器功能
    对于该注解来说, value属性是一个Class数组, 放入该数组的对象同样会被放入ioc容器中, 需要注意的是, 该对象在IOC容器中的
    标识符是该对象对应的类的全限定类名, 比如com.fightzhong.Test, 如果用@Import注解导入该对象, 则在IOC容器中该对象的标
    识符为com.fightzhong.Test, 总的来说, 该注解的作用就是扫描一个类, 如果这个类中有@Bean注解标识的方法, 则该方法的返回
    值对象也会被扫描进IOC容器

作用二: 自定义扫描器(实现批量导入, 可以不用再写大量的注解)
```

## @PropertySource
```
name, value:
    指定需要被导入的配置文件, 可以是properties和xml文件, 格式: [classpath:/xx/xxx]或者[file:/xx/xxx]

ignoreResourceNotFound:
    默认为false, 即当配置文件没有被找到的时候, 不应该忽视它, 即会报错, 如果为true, 则配置文件不存在时不会报错

encoding:
    指定该这些配置文件的编码格式, 默认是utf-8, 但是在windows中, 文件默认编码格式为gbk的, 所以说这里需要指定为gbk才能获
    取中文信息

factory:
    用于读取配置文件的工厂类, 如果想要自定义读取配置的方式, 则需要创建一个工厂类实现PropertySourceFactory接口的
    createPropertySource方法, 比如在@PropertySource注解中不能读取yaml文件的, 但是我们可以自定义一个工厂类对象来实现
    yaml文件的读取, 然后将该类的字节码传入factory属性里面, 该值的默认值为DefaultPropertySourceFactory, 只能读取xml和
    properties文件
```

## @DependsOn
```
依赖管理, 在Spring的bean初始化的过程中, 如果多个类之间没有引用关系, 那么Spring是会按照该类在文件夹的文件名排序来进行加载
的, 假设我们期望B类的加载在A类的加载之前, 那么就可以利用这个注解, 只需要A类中依赖于B类, 那么就会使得B类先加载进IOC容器

例子1:
    @Component
    public A {
        public A () {
            System.out.println( "A类被加载了" );
        }
    }

    @Component
    public B {
        public B () {
            System.out.println( "B类被加载了" );
        }
    }

将这上面的两个类放在同一个文件夹, 则初始化IOC容器后的执行结果为:
    A类被加载了
    B类被加载了

例子2:
    @Component
    @DependsOn( "b" ) // 输入B类在IOC容器中的标识符名称
    public A {
        public A () {
            System.out.println( "A类被加载了" );
        }
    }

    @Component
    public B {
        public B () {
            System.out.println( "B类被加载了" );
        }
    }

在A类上面增加了一个@DependsOn注解后, 初始化IOC容器后的执行结果为:、
    B类被加载了
    A类被加载了
```

## @Lazy
```
该注解用于bean对象为单例时的延迟加载, Spring对bean对象的加载是在IOC容器被创建时进行加载的, 但是有些类我们可能暂时用不到,
而在启动时如果加载过多的Bean对象则会导致应用程序的启动过于缓慢, 此时可以在该类上面利用该注解来使得其在使用时才会被加载,
需要注意的是, 该注解只能作用于单例bean
```

## @Conditional
```java
源码:
    public @interface Conditional {
        Class<? extends Condition>[] value();
    }

分析:
    Conditional是条件的意思, 当我们用该注解来标注一个bean对象的时候, 只有在满足value指定的条件下才会将该bean对象创建并
    放入IOC容器中, 如果没有满足则不会, 所以我们需要自己定义这个条件是什么, 通过实现Condition类并重写其matches方法来实现,
    如果该方法返回true则满足条件
```

## @Profile
```java
@Profile注解里面用到了@Conditional注解, 该注解的参数中的值意义是为在该参数表示的环境下, 被该注解标识的类才会被注入IOC容
器中, 例如:
    @Profile( "dev" )
    @Component
    public DevObject {}

则当前环境为dev的情况下DevObject才会被注入IOC容器

如何使得环境生效:
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles( "dev" );
    context.register( SpringConfig.class );
    context.refresh();
```

## @Inject&&@Named
```
@Inject == @Autowired
@Named == @Qualifier

@Autowired可以和@Named, @Qualifier搭配使用, @Inject同样也是, @Named和@Qualifier不能单独使用, 但是@Inject需要导入相
关的依赖:
    <dependency>
        <groupId>javax.inject</groupId>
        <artifactId>javax.inject</artifactId>
        <version>1</version>
    </dependency>
```

## @Primary
```
对于@Autowired注解来说, 在自动注入的时候, 必须保证在IOC容器中只存在一个指定类型的对象, 如果存在多个, 则不能自动注入了,
此时只能通过@Autowired配合@Qualifier来实现名称注入或者通过@Resource来实现名称注入, 那么对于多个相同类型的对象来说, 如果
想要实现@Autowired自动类型注入的话, 我们可以对这些对象指定优先注入的bean, 比如两个A接口的实现类A1, A2, 我们可以在A1的上
面增加一个@Primary注解, 那么在注入的时候就会优先注入A1了, 而不会出现之前@Autowired报错的情况
```

## @PostConstruct && @PreDestroy
```
@PostConstruct: 用于在构造完一个对象后执行被该注解标识的方法, 对于bean的单例来说, 只会执行一次, 在构造函数执行后执行该
                注解标注的方法
            
@PreDestroy: 用于在一个对象被销毁的时候调用被该注解标识的方法, 当bean对象为单例的时候, 只会在容器关闭时调用, 多例时则会
            在JVM垃圾回收时调用
```

## @ImportResource
```
用于导入Spring的xml的配置文件
```

## bean的生命周期
```
PostConstructor => afterPropertiesSet(InitializingBean) => InitMethod
```