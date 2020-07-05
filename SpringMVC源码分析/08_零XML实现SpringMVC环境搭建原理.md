在SpringBoot的项目中, 我们可以看到, web.xml、springmvc.xml、applicationContext.xml这样的配置文件已
经不见了, 取而代之的是各种的注解, 注解开发给我们带来了很多的便利, 利用JavaConfig完成项目的配置会显得更
加的"高端", 本篇文章主要分析的是无XML实现SpringMVC环境搭建的原理, 如果理解了这些原理, 那么SpringBoot的
也就不会太神秘了

### Servlet3.0规范的引入
```java
众所周知, Tomcat等web容器是用来放置Servlet的, Tomcat完成对底层网络的处理, 最后才将请求打到Servlet中,
在Servlet3.0规范出来后, 引入了许多的新特性, 其中一个特性是这样的, 我们可以在/META-INF/services文件夹
下创建一个名为javax.servlet.ServletContainerInitializer的文件, 这个文件中可以放置任何的
ServletContainerInitializer接口的实现类, ServletContainerInitializer接口是一个函数式接口, 有一个
onStartup方法, Servlet3.0规范中指定, 在web容器中启动的过程中, 会扫描整个项目中/META-INF/services文件
夹下名为javax.servlet.ServletContainerInitializer的文件, 调用这个文件中所有
ServletContainerInitializer实现类的onStartup方法, 下面我们来演示一下:

目录结构:
    src
        main
            java
            resources
                META-INF
                    services
                        javax.servlet.ServletContainerInitializer

文件javax.servlet.ServletContainerInitializer内容:
    com.test.MyWebInit

MyWebInit类代码:
    public class MyWebInit implements ServletContainerInitializer {
        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
            System.out.println( "startUp......" );
        }
    }

分析:
    启动tomcat, 会发现控制台输出了: startUp......

我们再来看看这个onStartup方法, 有一个Set<Class<?>>的变量, 以及一个ServletContext, 后者大家应该很熟悉
了, Servlet上下文, 我们来介绍一下前者, 有这么一个注解@HandlesTypes, Servlet3.0规范中指定, 当该注解标
注在ServletContainerInitializer上的时候, web容器启动的过程中就会将整个项目中该注解中指定的接口的所有
实现类的Class对象放入一个set中并传入onStrartup方法中, 下面我们来修改下上述的例子:

package com.test.startup;
public interface StartUpInterface {}
public class StartUpInterfaceImpl implements StartUpInterface {}

@HandlesTypes( StartUpInterface.class )
public class MyWebInit implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        System.out.println( c );
    }
}

分析：
    启动tomcat, 发现控制台输出了: [class com.test.startup.StartUpInterfaceImpl]

由上面的特性, 我们就可以了解到, web容器提供了扩展点, 在容器启动的过程中, 允许我们做一些事情, 利用这个扩
展点, 我们就能实现无xml对SpringMVC的环境进行配置了
```

### 实现无xml完成SpringMVC环境搭建
```java
结合之前的DispatcherServlet初始化流程源码分析, 我们需要知道一个点, 之前我们配置SpringMVC的环境的时候,
是在web.xml中配置了DispatcherServlet的映射关系, 并且利用监听器注册了一个Spring容器, 在xml中配置
DispatcherServlet的映射关系, 其实就是将DispatcherServlet这个Servlet加入到了Servlet上下文中而已, 于是
我们联想上面的例子, 貌似我们可以手动的创建DispatcherServlet对象, 然后加到ServletContext中???

其次, 没有了springmvc.xml文件, 那么xml文件中配置的annotation-driven以及component-scan这些功能就没法
启用, 没法开启注解配置以及配置扫描的包了

再一次回顾前面的DispatcherServlet初始化流程源码分析的内容, DispatcherServlet继承于FrameworkServlet,
FrameworkServlet重写了GenericServlet的空参init方法, 在这个重写的方法中完成了容器的初始化, 于是乎, 我
们再来回顾一下, 容器的获取方式, 通常情况下, 是在这个初始化的过程中会自动创建web容器, 但是这个创建的web
容器xmlApplicationContext, 并且不方便我们去控制其创建的过程, 但是在原来的源码分析中, 笔者也提到了, 我
们可以手动的往DispatcherServlet中set一个容器, 那就好办了, 我们往里面set一个注解配置的容器, 而不是xml
配置的容器, 于是, 无xml配置的原理就出来了, 我们先看一下代码:
public class MyWebInit implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) {
        AnnotationConfigWebApplicationContext context 
                                                = new AnnotationConfigWebApplicationContext();
        context.register( SpringConfig.class );

        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        dispatcherServlet.setApplicationContext( context );
        ServletRegistration.Dynamic registration 
                                = ctx.addServlet( "dispatcherServlet", dispatcherServlet );
        registration.addMapping( "/" );
        registration.setLoadOnStartup( 1 );
        // registration.setInitParameter();
    }
}

分析:
    <1> 手动创建一个注解配置的Web容器, 如果对Spring源码有所了解, 那么register方法应该不会陌生, 就是
        注册一个配置类, 这个配置类的内容很简单:
            @Configuration
            @ComponentScan( "com.test" )
            @EnableWebMvc
            public class SpringConfig {}

    <2> 我们在独立的Spring环境下, 创建Spring环境是这样的:
            AnnotationConfigApplicationContext context 
                                = new AnnotationConfigApplicationContext(SpringConfig.class);
        可以在构造器中放入一个配置类, 构造器被执行的时候, 会调用register方法将SpringConfig配置类注册
        到容器中, 同时调用refresh方法开始完成容器的初始化, 在web环境下的容器, 没有提供一个构造器可以
        传递配置类的, 于是我们才手动调用register方法来注册一个配置类, 注意了, 我们没有调用refresh方法
        之后我们再来分析下原因
    <3> 手动创建一个DispatcherServlet, 同时将创建好的web容器设置到DispatcherServlet中, 之后
        DispatcherServlet在初始化的时候就不会自己创建了, 而是采用我们传入的这个
    <4> 将DispatcherServlet添加到Servlet上下文中, 就等价于在web.xml中配置了一个DispatcherServlet,
        然后利用返回值registration(ServletRegistration.Dynamic类型的)添加这个Servlet需要拦截的请求,
        最后两行就等价于在web.xml中的配置:
            <init-param>
                <param-name>contextConfigLocation</param-name>
                <param-value>classpath:spring-mvc.xml</param-value>
            </init-param>
            <load-on-startup>1</load-on-startup>

到此为止, 我们利用Java代码完成了在web.xml中配置的DispatcherServlet的功能, 与此同时, 整合了注解配置的
web容器与DispatcherServlet, 再来说下为什么我们不主动调用refresh方法刷新容器, 回顾一下原来的
DispatcherServlet初始化流程源码分析, 原来的web容器是在FrameworkServlet的空参init方法被触发创建的, 由
于我们手动放入了web容器, 所以就不会手动创建, 大家可以回顾下之前的文章内容, 笔者着重的讲解了在容器创建后
或者从DisatcherServlet中取到了设置进去的容器后, 会调用一个configureAndRefreshWebApplicationContext
方法, 在这个方法中的最后有这么一行代码:
    wac.refresh();

于是乎, 我们知道了, 在FrameworkServlet中就会帮我们主动调用refresh方法, 如果在此时我们先调用的话, 那么
可能会有些问题, 因为在configureAndRefreshWebApplicationContext方法中, 调用refresh方法之前, 会对容器
进行一定的设置, 比如说环境、监听器(用于触发SpringMVC的九大策略的初始化)等, 所以我们不能在这个时候去调用
```

### 小小的总结
```
实现无xml完成的SpringMVC环境搭建的原理其实很简单, 就是利用了Servlet3.0规范提供的
ServletContainerInitializer接口来完成的, 因为这个接口会传入一个ServletContext, 所以我们可以直接对
ServletContext进行操作, 放入DispatcherServlet, 同时利用注解配置的上下文完成Spring环境的引入, 由此我
们已经可以直接将各种的xml配置文件删除了, 如果是Spring或者SpringMVC的配置, 可以利用上面的SpringConfig
这个类来完成, 而如果是web.xml的配置, 可以利用MyWebInit类中onStartUp方法来完成
```

### 内嵌tomcat容器
```java
在我们之前的分析中, 已经完成了无xml配置SpringMVC的环境了, 这里做一个扩展, 我们知道SpringBoot是内嵌
tomcat的容器的, 我们也可以完成这样的壮举!!

引入tomcat的jar包:
    <dependency>
      <groupId>org.apache.tomcat.embed</groupId>
      <artifactId>tomcat-embed-core</artifactId>
      <version>8.5.31</version>
    </dependency>

    <!-- 引入JSP支持 -->
    <dependency>
      <groupId>org.apache.tomcat.embed</groupId>
      <artifactId>tomcat-embed-jasper</artifactId>
      <version>8.5.31</version>
    </dependency>

创建启动类:
    public class MyApplicationStarter {
        public static void main(String[] args) throws Exception{
            Tomcat tomcat = new Tomcat();
            tomcat.setPort(80);
            tomcat.addWebapp("/","D:\Users\Desktop\testProject\springmvc\src\main\webapp");
            context.addLifecycleListener(
                (LifecycleListener) Class.forName(tomcat.getHost().getConfigClass())
                .newInstance());

            tomcat.start();
            tomcat.getServer().await();
        }
    }

再结合之前的代码, 我们就实现了内嵌的tomcat应用, 从而实现了类似于SpringBoot的效果!!!
```

### 扩展: WebApplicationInitializer
```
最开始分析的Servlet3.0规范的时候, 我们发现利用3.0规范, 我们能够在tomcat容器启动的过程中进行插手, 只需
要在resources目录下建立/META-INF/services/javax.servlet.ServletContainerInitializer的文件就好了,
tomcat容器启动的时候会反射加载这个文件中的所有类, 并引入了@HandlesTypes注解, 在该注解标注的接口, 其所
有的子类会被传入onStartup方法中, 然而, 上面是我们自己实现的, 其实在SpringMVC中早就已经有了实现

SpringServletContainerInitializer是Spring对Servlet3.0规范的实现, 同时在spring-web这个包中, 就有一个
/META-INF/services/javax.servlet.ServletContainerInitializer文件, 在这个文件中就定义了这个
SpringServletContainerInitializer类, 这个类被@HandlesTypes注解标注了, 标注的接口是
WebApplicationInitializer接口, SpringServletContainerInitializer类的onStartUp方法完成的功能就是
从set中取出一个个WebApplicationInitializer接口的实现类, 创建对象, 并调用这些这些实现类的方法, 所以我
们只需要在程序中创建一个WebApplicationInitializer实现类就可以完成之前我们的所有功能了....
```


Hello, 本期给大家带来了两篇文章, 【DispatcherServlet执行流程源码分析: https://juejin.im/post/5f013d6fe51d4534b44606c4】,【零XML配置SpringMVC环境原理: https://juejin.im/post/5f018ca46fb9a07ec172d04f】, 有了前面文章的铺垫, 再来看DispatcherServlet的执行流程简直是太简单了, 到此为止, 我的SpringMVC源码分析系列的文章就告一段落了, 大家有兴趣可以看看哈, 希望能够给大家在阅读SpringMVC源码的时候提供帮助0.0