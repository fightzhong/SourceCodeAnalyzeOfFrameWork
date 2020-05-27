
> 该接口的意义不太大, 可以通过ImportSelector和ImportBeanDefinitionRegistrar接口来替换该接口的功能, 该接口的作用时间点在bean的前置处理中, 通过ConfigurationClassPostProcessor这个beanFactory的
后置处理器类中的静态类ImportAwareBeanPostProcessor(是一个bean的后置处理器)来实现

## ImportAware
```java
ImportAware接口, 当一个类实现了这个接口后, 如果其被其他类给Import, 那么就能通过这个接口获取到Import它的那个类的注解元
信息, 需要注意的是, 实现了ImportAware接口的类必须用@Configuration标注

// MyImportAware类实现了ImportAware接口
@Configuration
public class MyImportAware implements ImportAware {
	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		System.out.println( "aaaa" );
	}
}

// MyImportAware类被其他类给导入, 则MyImportAware类的setImportMetadata就能获取到SpringConfig类的注解元信息
@Import( {MyImportAware.class} )
public class SpringConfig {

}
```



