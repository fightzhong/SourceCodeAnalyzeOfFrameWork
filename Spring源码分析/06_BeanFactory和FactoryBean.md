## FactoryBean与BeanFactory
```
BeanFactory和FactoryBean都是接口, BeanFactory的实现类AnnotationConfigApplicationContext就是我们熟悉的spring上下文
对象, BeanFactory是一个bean对象存放的容器, 而FactoryBean也是一个bean, 其也存在于BeanFactory中, 但是FactoryBean与其他
的bean对象不同, 实现了FactoryBean接口的bean对象在存放到容器时会创建两个bean对象, 一个是该实现类中的beanName标识的bean
对象, 其由FactoryBean接口的getObject方法决定为哪个对象, 另一个就是该FactoryBean实现类本身, 但是这个对象的唯一id则是在
原来的beanName前面加一个与符号(&)
```

## 例子
```java
// spring配置类
@Component
@ComponentScan( "com.fightzhong.service" )
public class SpringConfig {}

// 创建一个FactoryBean实现类并交给spring容器管理
@Component( "myFactoryBean" )
public class MyFactoryBean implements FactoryBean<UserService> {
	@Override
	public UserService getObject() throws Exception {
		System.out.println( "创建对象" );
		return new UserService();
	}

	@Override
	public Class<?> getObjectType() {
		return UserService.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}

// 一个简单的类
public class UserService {
	public void print () {
		System.out.println( "UserService" );
	}
}

// 测试方法

public static void main(String[] args) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext( SpringConfig.class );
    System.out.println( context.getBean( "&myFactoryBean" ) );
    System.out.println( context.getBean( "myFactoryBean" ) );
    System.out.println( context.getBean( "myFactoryBean" ) );
}

// 输出结果
com.fightzhong.service.MyFactoryBean@2173f6d9
创建对象
com.fightzhong.service.UserService@307f6b8c
com.fightzhong.service.UserService@307f6b8c
```

## 总结
```
FactoryBean中:
    getObject: 用于指定FactoryBean生成的对象
    getObjectType: 
    isSingleton: 用于指定FactoryBean生成的对象是否是单例的, 如上面所示, 指定为单例后, getObject方法就只执行了一次

获取FactoryBean对象时唯一ID需要在原来的基础上增加一个&符号, 根据指定的唯一ID获取到的时getObject中的对象
```
 





































