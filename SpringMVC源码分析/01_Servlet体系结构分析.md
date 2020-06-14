### 引入
```
Servlet是一个JavaWeb规范, 对应了Java中的Servlet接口, 回想很早之前直接利用Servlet开发的时候, 我们需要
继承Servlet接口,重写其service方法, 然后在web.xml中配置该servlet与域名的映射关系, 当利用Tomcat来启动的
时候, 请求到来时自动会映射到对应的Servlet中, 调用我们重写的service方法执行请求, Java中Servlet的主要继
承关系是HttpServlet->GenericServlet->Servlet, 接下来我们分析下这三个类
```

### web.xml配置一个Servlet
```xml
<!DOCTYPE web-app PUBLIC
 "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
 "http://java.sun.com/dtd/web-app_2_3.dtd" >

<web-app>
  <display-name>Archetype Created Web Application</display-name>
  
  <servlet>
    <servlet-name>dispatchServlet</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
      <param-name>contextConfigLocation</param-name>
      <param-value>classpath:spring-mvc.xml</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
  </servlet>

  <servlet-mapping>
    <servlet-name>dispatchServlet</servlet-name>
    <url-pattern>/</url-pattern>
  </servlet-mapping>
</web-app>

分析:
    如果要让一个Servlet生效, 则我们在web.xml需要配置一个servlet和servlet-mapping标签, 如果该Servlet
    配置的load-on-startup大于0的话, 则该Servlet的init方法就会被调用, 否则仅仅在该Servlet第一次被访问
    的时候才会调用, 而在init-param标签配置的内容, 会被放入到一个ServletConfig中进行保存, 当调用
    Servlet的init方法的时候, 该ServletConfig对象就会被作为参数传入
```

### Servlet接口
```java
public interface Servlet {
    public void init(ServletConfig config) throws ServletException;
    
    public ServletConfig getServletConfig();
    
    public void service(ServletRequest req, ServletResponse res) 
	
    public String getServletInfo();

    public void destroy();
}

分析:
    Servlet接口一共定义了五个接口方法, 在容器启动时, 如果该Servlet配置的load-on-startup大于0的话, 则
    该Servlet的init方法就会被调用, 否则仅仅在该Servlet第一次被访问的时候才会调用, 可以看到, 
    ServletConfig被作为参数传入了, 而getServletConfig仅仅是一个抽象方法, 需要由子类来实现, 在
    GenericServlet中, 就是利用一个ServletConfig属性来保存init方法传入的ServletConfig, 然后实现
    getServletConfig方法, 即返回的就是属性ServletConfig, service方法是请求过来时被触发的, 当Servlet
    拦截到对应的请求的时候, 就会触发其service方法, 该方法有两个参数, 分别是req, res, 利用这两个参数,
    我们能获取整个的请求信息, 并设置返回的数据等, getServletInfo方法返回的是一个字符串, 通常我们实现的
    时候就是返回当前Servlet的类名, 或者其他描述信息也可以, destroy方法则在容器被关闭的时候会被触发调用,
    即tomcat被关闭时会被调用
```

### GenericServlet抽象类
```java
public abstract class GenericServlet  implements Servlet, ServletConfig, java.io.Serializable {
    private transient ServletConfig config;

    public void destroy() {}

    public ServletConfig getServletConfig() {
	    return config;
    }

    public String getServletInfo() { return ""; }

    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        this.init();
    }

    public void init() throws ServletException {}

    public abstract void service(ServletRequest req, ServletResponse res)
}

分析:
    GenericServlet抽象类实现了Servlet接口, 实现了Servlet接口中除了service方法外的所有方法, 对于init
    方法, 其利用一个成员变量来将容器调用时传入的ServletConfig保存了下来, 其次getServletConfig方法就是
    将这个保存下来的ServletConfig返回而已, 与此同时, 提供了一个空参的init方法, 在有参的init方法中调用
    了该空参的init方法, 这样做的好处就是, 子类仅仅可以利用空参init方法来完成初始化的功能, 并且利用
    getServletConfig方法来获得ServletConfig, getServletInfo仅仅返回了一个空字符串, destroy方法也仅
    仅做了空的实现, 从而只有service方法需要子类进行实现, 其他方法子类可以选择性实现
```

### ServletConfig接口
```java
 public interface ServletConfig {
    public String getServletName();

    public ServletContext getServletContext();

    public String getInitParameter(String name);

    public Enumeration<String> getInitParameterNames();
}

分析：
    GenericServlet实现了这个ServletConfig接口, 目的是提供给开发者对ServletConfig的访问而已, 因为
    GenericServlet中已经保存了init方法中容器传入的ServletConfig了, GenericServlet实现这些接口其实很
    简单, 就是调用了保存下来的属性servletConfig的这些方法而已
```

### HttpServlet类
```java
public abstract class HttpServlet extends GenericServlet {
    public void service(ServletRequest req, ServletResponse res) {
        HttpServletRequest  request;
        HttpServletResponse response;
        
        if (!(req instanceof HttpServletRequest &&
                res instanceof HttpServletResponse)) {
            throw new ServletException("non-HTTP request or response");
        }

        request = (HttpServletRequest) req;
        response = (HttpServletResponse) res;

        service(request, response);
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        String method = req.getMethod();

        if (method.equals(METHOD_GET)) {
            doGet(req, resp);
        } else if (method.equals(METHOD_HEAD)) {
            doHead(req, resp);
        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);
        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);
        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);
        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req,resp);
        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req,resp);
        } else {
            String errMsg = lStrings.getString("http.method_not_implemented");
            Object[] errArgs = new Object[1];
            errArgs[0] = method;
            errMsg = MessageFormat.format(errMsg, errArgs);
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }
}

分析:
    HttpServlet的实现很简单, 继承GenericServlet, 并实现其抽象方法service方法, 可以清晰的看到, 先将
    ServletRequest以及ServletResponse转为HttpServletRequest和HttpServletResponse, 然后利用
    getMethod方法判断当前请求是什么类型的, 从而调用对应的doXXX方法, 在HttpServlet中, 这些doXXX方法都
    是返回一个错误的, 因为这是要子类去实现的
```

### ServletContext接口
```java
SerlvetContext, 表示的是整个Servlet的上下文, 即当前项目下所有的Servlet都能共享该ServletContext, 上面
所聊到的ServletConfig是单个Servlet独有的, 由此可见, 如果我们期望多个Servlet共享某一块内容, 可以利用这
个ServletContext来进行传递, 在之后我们实现无xml完成SpringMVC环境搭建的时候也可以清晰的看到, 我们会利用
这个ServletContext来添加Servlet, 而不是在web.xml中进行添加了, 在ServletContext中, 有setAttribute、
getAttribute、removeAttribute等等方法, 例如:
    public void setAttribute(String name, Object object);

可以看到, 其实在ServletContext中可能维护了一个Map, setAttribute方法就是往这个Map中添加一个键值对而已,
并且这个值还是Object类型的
```
