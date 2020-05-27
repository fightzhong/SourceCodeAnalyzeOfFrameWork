## ImportSelector
- 作用
```
该接口是用于定义自定义扫描器的, 我们正常使用spring容器的时候, 通过@Component这些注解来标识一个bean对象, 并且用
ComponentScan来标识扫描包的范围, 利用ComponentScan中的filter来定义过滤的范围, 但是有时候我们需要自己定义扫描器, 这样就
可以让spring主动去扫描对应的包的类, 而这些类可以不用写@Component这些注解, 通过实现ImportSelector接口, 重写selectImports
方法即可, 然后在配置类中利用Import注解来导入这个自定义扫描器, 总结一句话: 实现了ImportSelector接口后的类, 通过Import注
解将其引入, 该接口的selectImports方法返回的是一个字符串数组, 这个字符串数组中包含的是一个个类的全类名, 这些类会被扫描到
Spring的IOC容器中
```

- 分析
```
我们自定义一个扫描器, 要求从配置文件中扫描指定的包, 并且利用aspect表达式来定义这些包中哪些类可以被扫描进IOC容器

配置文件内容(applicationContext.properties):
    spring.basepackage=com.fightzhong.service
    spring.class.include=com.fightzhong..*

注意:
    当我们利用ImportSelector自定义扫描器的时候, 在自定义扫描器中扫描的类中selectImports方法的返回值一定不能包含Spring
    的配置类以及这个自定义扫描器类, 因为在配置文件中它已经被扫描过一次了, 如果包含则会抛出一个异常
```

- 实现自定义扫描器
```java
public class CommonImportSelector implements ImportSelector {
	private String basePackage; // 从配置文件中获取的包的扫描路径
	private String expression;  // aspect表达式
	
    // 在默认构造函数中读取配置文件内容
	public CommonImportSelector () {
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
	public String[] selectImports (AnnotationMetadata importingClassMetadata) {
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
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider( false );

		// 创建一个表达式过滤器
		AspectJTypeFilter aspectJTypeFilter = new AspectJTypeFilter( this.expression, CommonImportSelector.class.getClassLoader() );

		// 将该过滤器放入扫描器中, 表示当满足该表达式的情况下才被扫描
		scanner.addIncludeFilter( aspectJTypeFilter );

		Set<String> beanNames = new HashSet<>();
		// 利用扫描器扫描这些包
		for ( String pk: basePackages ) {
			// 扫描pk包, 结果是获取到一个个的bean对象
			Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents( pk );
			for ( BeanDefinition bean: beanDefinitions ) {
				String beanName = bean.getBeanClassName();
				// 添加到Set中的bean不能是已经被Spring扫描过的bean, 比如配置类, 以及在配置类中Import标注的类
				if ( !beanName.equalsIgnoreCase( this.getClass().getName() )
				  && !beanName.equalsIgnoreCase( springConfigBeanName ) ) {
					beanNames.add( bean.getBeanClassName() );
				}
			}
		}

		return beanNames.toArray( new String[ beanNames.size() ] );
	}
}
```

