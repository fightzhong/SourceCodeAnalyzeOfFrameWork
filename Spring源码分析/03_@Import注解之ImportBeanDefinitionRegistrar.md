## ImportBeanDefinitionRegistrar
- 作用
```
其作用跟ImportSelector是一模一样的, 都是用来自定义bean扫描器, 实现ImportBeanDefinitionRegistrar接口则需要重写其
registerBeanDefinitions方法, 在该方法中定义需要被扫描的包, 然后利用ClassPathBeanDefinitionScanner类来进行扫描, 这里
与ImportSelector不一样, ImportSelector中扫描bean用的是ClassPathScanningCandidateComponentProvider, 因为ImportSelector
中重写的方法需要返回一个个的beanName, 而ClassPathScanningCandidateComponentProvider扫描后可以返回被扫描的bean的定义,
ClassPathBeanDefinitionScanner则只会返回被扫描的个数, 总结一句话: 该接口与ImportSelector不同的是, ImportSelector返回
一个字符串数组, 之后Spring会自动将该数组中的类扫描到IOC容器, 但是ImportBeanDefinitionRegistrar不用返回任何值, 因为其里
面就可以自己注册这个bean到IOC容器
```

- 分析
```
我们自定义一个扫描器, 要求从配置文件中扫描指定的包, 并且利用aspect表达式来定义这些包中哪些类可以被扫描进IOC容器

配置文件内容(applicationContext.properties):
    spring.basepackage=com.fightzhong.service
    spring.class.include=com.fightzhong..*
```

- ImportSelector与ImportBeanDefinitionRegistrar的区别
```
扫描器不一样:
    ImportSelector的扫描器为ClassPathBeanDefinitionScanner
    ImportBeanDefinitionRegistrar的扫描器为ClassPathBeanDefinitionScanner

返回结果不一样:
    ImportSelector的返回结果为被扫描到的bean的全限定类名
    ImportBeanDefinitionRegistrar的返回结果为void

扫描到IOC容器中的标识不一样:
    ImportSelector的标识为类的全限定类名
    ImportBeanDefinitionRegistrar的标识为类名(第一个字母小写), 并且可以自定义BeanNameGenerator
```

- 实现自定义扫描器
```java
public class CommonImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
	private String basePackage; // 从配置文件中获取的包的扫描路径
	private String expression;  // aspect表达式

	// 在默认构造函数中读取配置文件内容
	public CommonImportBeanDefinitionRegistrar () {
		try {
			InputStream in = CommonImportSelector.class.getClassLoader().getResourceAsStream( "applicationContext.properties" );
			Properties properties = new Properties();
			properties.load( in );

			basePackage = properties.getProperty( "spring.basepackage" );
			expression= properties.getProperty( "spring.class.include" );
		} catch ( Exception e ) {
			throw new RuntimeException();
		}
	}

	@Override
	public void registerBeanDefinitions (AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		// Spring的配置启动类
		String springConfigBeanName = importingClassMetadata.getClassName();
		List<String> basePackages = new ArrayList<>();

		// 有ComponentScan注解
		if ( importingClassMetadata.hasAnnotation( ComponentScan.class.getName() ) ) {
			// 获取ComponentScan注解的所有属性
			Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes( ComponentScan.class.getName() );

			// 获取value属性值, 并放入basePackages中
			String[] packages = (String[]) attributes.get( "basePackages" );

			basePackages.addAll( Arrays.asList( packages ) );
		}

		// 不存在ComponentScan注解 || 存在ComponentScan注解但是value为空, 则添加Import注解所在的包
		if ( basePackages.size() == 0 ) {
			try {
				basePackages.add( Class.forName( springConfigBeanName ).getPackage().getName() );
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		// 添加配置文件中spring.basepackage指定的包
		if ( this.basePackage != null ) {
			basePackages.add( this.basePackage );
		}

		// 获取扫描器, 不使用默认的过滤器(默认的过滤器是注解过滤器)
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner( registry, false );

		// 创建一个表达式过滤器
		AspectJTypeFilter filter = new AspectJTypeFilter( expression, this.getClass().getClassLoader() );

		// 将该过滤器放入扫描器中, 表示当满足该表达式的情况下才被扫描
		scanner.addIncludeFilter( filter );

		// 开始扫描
		scanner.scan( basePackages.toArray( new String[ basePackages.size() ] ) );
	}
}
```














