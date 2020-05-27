## @EnableAspectJAutoProxy
```
开启AOP的支持, 里面有两个属性:
    proxyTargetClass: 是否代理类, 默认为false, 对于AOP来说, 默认是代理接口的, 即JDK默认的动态代理, 但是如果切入点表达
                      式中涵盖的类没有实现接口的话, 那么该类就会采用CGLIB来进行代理, 如果将该属性设置为true, 则表示代
                      理类, 此时会采用CGLIB的方式来进行代理

    exposeProxy：是否将代理类暴露出去, 默认为false, 如果设置为true, 那么在被代理类中就能通过AopContext.currentProxy
                 方法来获取该代理类对象, 需要注意的是, 只能在当前被代理类中获取其代理类对象
```

## @Aspect && @Order
```
用于指定一个类为切面类, 其属性value表示该@Aspect标注的类是一个多例的, 同时需要将该类设置@Scope为prototype, 对于多个切面
类同时作用在一个类上时, 比如AccountDao持久层对象, 如果有两个切面类(如A类和B类)同时指定了前置通知的切入点表达式在
AccountDao上的话, 那么默认是会采用两个类的类名顺序来决定两个前置通知的执行顺序的, 即A类的前置通知会优先执行, 但是我们可以
通过@Order注解来规定两个切面类的执行顺序, 该注解的value属性是一个整型, 数值越小优先级越高
```

## 通知类型
- 描述
```java
@Before: 前置通知
@AfterReturning: 后置通知
@AfterThrowing: 异常通知
@After: 最终通知

注意: 在这四种通知中, 以注解方式开发时, 最终通知会优先于后置通知和异常通知先执行

@PointCut: 可以将多个通知中相同的切入点表达式进行抽离, 即可以用该注解
    @Pointcut( "execution(* com.fightzhong.service.impl.UserServiceImpl.*(..))" )
	private void pointCut() {}

使用:
    @Before( "pointCut()" )
	public void before () {
		System.out.println( "前置通知" );
	}

注意:
    可以将这个切入点表达式抽离到一个专门的类中, 之后在其他地方就可以用该类的全限定类名加上方法名来引用到它了, 但是我们需
    要注意的是, 描述这个切入点表达式的方法的权限也会同时生效

// 抽离
public class PointCutConfig {
	@Pointcut( "execution(* com.fightzhong.service.impl.UserServiceImpl.*(..))" )
	public void pointCut() {}
}

// 使用
@Before( "com.fightzhong.config.PointCutConfig.pointCut()" )
public void before () {
    System.out.println( "前置通知" );
}
```

- 通知中需要参数
```java
例子:
    @Before( "execution(* com.fightzhong.service.impl.UserServiceImpl.*(..)) && args(user)" )
	public void before (User user) {
		System.out.println( "前置通知" + user );
	}

描述:
    以上述为例, 我们的切入点表达式指定了UserServiceImpl类下的任意方法任意返回值, 但是在后面用与运算符进行了拼接, 上述的
    代码表示, 该前置通知只会作用于UserServiceImpl类中的任意方法, 但是该方法必须有一个参数, 参数的类型是User, 在前置通知
    指定的方法中可以获取到该方法的参数
```

- 通知中获取返回值或者异常信息
```java
@AfterReturning注解中, returning属性可以指定返回值的名字, 然后在该注解标注的方法上作为参数传入, 则该方法就能获取到返回
值了, 我们能够对返回值对象进行内部操作, 比如设置该对象的属性为其他值, 但是我们不能改变返回值为其他值
例如:
    @AfterReturning( value = "execution(* com.fightzhong.service.impl.UserServiceImpl.*(..))", returning = "result" )
    public void afterReturning (List<Integer> result) {
        result.set( 2, 5 );
    }

@AfterThrowing注解中, throwing可以指定异常对象的名称, 这样就可以在被该注解修饰的方法中引用到该异常对象了
例如:
    @AfterThrowing( value = "execution(* com.fightzhong.service.impl.UserServiceImpl.*(..))", throwing = "ex" )
	public void afterThrowing (Throwable ex) {
		ex.printStackTrace();
	}
```


## 环绕通知
```java
@Around: 利用该注解来标注一个方法, 自定义通知执行时的通知方法执行时机
例子:
    @Around( "com.fightzhong.config.PointCutConfig.common()" )
    public Object log (ProceedingJoinPoint joinPoint) {
        Object result = null;
        try {
            System.out.println( "前置通知" );
            // 获取参数
            Object[] args = joinPoint.getArgs();
            // 执行方法, 并获取结果
            result = joinPoint.proceed( args );

            System.out.println( "后置通知" );
        } catch ( Throwable t ) {
            System.out.println( "异常通知" );
        } finally {
            System.out.println( "最终通知" );
            return result;
        }
    }

获取方法的方法信息: 
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method targetMethod = signature.getMethod();

分析:
    ProceedingJoinPoint只是一个接口, 他有一个实现类MethodInvocationProceedingJoinPoint, 在真正的环绕通知中, 传入的
    是MethodInvocationProceedingJoinPoint实现类对象, 所以我们可以通过向下转型的方式来获取该对象的方法, 在该实现类中有
    一个内部类MethodSignatureImpl, 其实现了MethodSignature接口, 而该接口最终会继承于Signature, 所以通过getSignature
    方法, 我们能获取到Signature对象, 然后向下转型为MethodSignature, 从而可以调用getMethod方法来获取被增强的方法
```

## @DeclareParent
```java
A接口的实现类B, 当我们通过@Aspect注解对其进行代理的时候, 放入Spring单例池以及Spring的工厂中的均是生成的代理对象C, 默认是
根据JDK的动态代理生成的, 根据动态代理可知, 在C对象中的各个方法下都会调用B对象的方法, 但是C对象转为B对象是不可以的, 因为
JDK的动态代理是基于接口的动态代理, 如果我们希望代理对象能够有更多的方法, 而不仅仅只有A接口的方法, 我们就可以利用@DeclareParent
注解来实现:

下面是一个普通的切面案例:
interface A {
    void save();
}

class B implements A {
    @Override
    public void save () {}
}

@Component
@Aspect
public class MyAspect {
    @Before( "execution(* com.B.*(..))" ) // 表示代理B类的所有方法
    public before () {
        System.out.println( "前置通知" );
    }
}

@ComponentScan( {"com"} )
@EnableAspectJAutoProxy
public class SpringConfiguration {
}

// 正常调用
AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext( SpringConfiguration.class );
IAccountService service = ac.getBean( IAccountService.class );
service.save(); // 由于@Aspect存在, 在调用该save方法之前会调用MyAspect中的before方法

目标: 希望能扩展B类, 比如增加一个delete方法, 此时我们正常来说是在A类上加一个delete方法的接口表示, 然后在B类中实现该方法,
     这样我们的代理类就有该方法了, 但是我们不希望去改变其源代码, 或者说, 我们希望扩展的是别人jar包中的类(无法修改其源代码)
     利用@DeclareParents方法, 我们可以使得B类在生成代理类的时候, 实现更多的接口, 从而达到扩展的方式, 如下：

interface B_Extension {
    void delete();
}

class B_Extension_Impl implements B_Extension {
    public void delete () {}
}


// 修改我们的切面类
@Component
@Aspect
public class MyAspect {
    /**
     * 类名+表示该类及其子类, 需要这个加号, 如下, 表示B类及其子类在生成代理的时候, 多实现一个接口, 该接口是B_Extension,
     * 这样生成的代理类对象就有该接口的方法了, defaultImpl指定的是真正调用的方法
     */

    @DeclareParents( value = "com.B+", defaultImpl = B_Extension_Impl.class )
	private B_Extension B_Extension;

    @Before( "execution(* com.B.*(..))" ) // 表示代理B类的所有方法
    public before () {
        System.out.println( "前置通知" );
    }
}
```
