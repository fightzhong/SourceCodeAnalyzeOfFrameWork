经过前面几篇文章的分析, 此时此刻我们再来看SpringMVC执行请求的流程就基本不会遇到盲点了, 在整个SpringMVC
的源码分析中, 只分析了HandlerMapping、HandlerAdapter两个组件, 至于ViewResovler, 笔者决定不去分析了,
因为这个组件不太难, 如果将HandlerAdapter都拿下来了的话, 读ViewResolver的源码就会很轻松, 因为都是类似
的, 找到对应的视图解析器, 调用解析的方法进行解析, 这前提是我们返回的是一个视图名称, 如果用了
@ResponseBody注解, 那么视图解析也就没啥必要性了, 因为数据已经在ReturnValueResolver中利用消息转换器写
回到请求端了, 所以视图解析器其实是针对于我们返回了视图的情况, 有了前面的Model以及ModelAndViewContainer
知识的铺垫, 看这一块代码简直不要太轻松..........本篇文章则对SpringMVC的执行流程进行分析, 看看我们的请求
到达了Servlet后到底是如何被处理的

### 引入
```java
在前面的文章中, 我们对DispatcherServlet的初始化流程进行了分析, 首先要明白的一点是, 经过这次的初始化,
整个Web容器已经被创建好了, SpringMVC的九大组件也已经被放置到了DispatcherServlet中了, 请求的处理其实就
是对这些组件的应用而已, 我们假设有如下的测试用例:
@Controller
public class TestController {
    @RequestMapping( value="/test", method = RequestMethod.GET )
    @ResponseBody
    public List<String> test () {
        return Arrays.asList( "a", "b" );
    }
}

通过浏览器请求了如下域名: localhost/test
```

### DispatcherServlet请求的处理
#### 入口点: FrameworkServlet
```java
protected void service(HttpServletRequest request, HttpServletResponse response) {
    HttpMethod httpMethod = HttpMethod.resolve(request.getMethod());
    if (HttpMethod.PATCH == httpMethod || httpMethod == null) {
        processRequest(request, response);
    }
    else {
        super.service(request, response);
    }
}

分析:
    FrameworkServlet重写了HttpServlet的service方法, 请求进来的时候, 首先是进入到了这个service方法,
    通过从请求中拿到请求方法, 将其转为枚举类HttpMethod, 最后还是调用了HttpServlet的service方法, 之所
    以重写这个方法, 原因是增加了对请求方法类型为patch的处理, 在HttpServlet的service中是没有对这个类型
    的请求进行处理的, 我们先不理会processRequest方法是做啥的, 首先需要知道, 当请求是其他类型的时候, 最
    终会调用到HttpServlet的service方法的, 在这个方法中有各种doXXX方法, 然而!!FrameworkServlet重写了
    这些doXXX方法, 以doPost为例:
        protected final void doPost(HttpServletRequest request, HttpServletResponse response) {
            processRequest(request, response);
        }

    ok, 到这里为止, 大家应该就清楚了, 真正处理请求的是这个processRequest方法, 所有的请求最终都会到
    FrameworkServlet中的doXX方法, 最终用processRequest方法来调用
```

#### processRequest: 真正处理请求的方法
- processRequest引入
```java
protected final void processRequest(HttpServletRequest request, HttpServletResponse response) {
    LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
    LocaleContext localeContext = buildLocaleContext(request);

    RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
    ServletRequestAttributes requestAttributes 
                            = buildRequestAttributes(request, response, previousAttributes);

    initContextHolders(request, localeContext, requestAttributes);

    try {
        doService(request, response);
    } finally {
        resetContextHolders(request, previousLocaleContext, previousAttributes);
        if (requestAttributes != null) {
            requestAttributes.requestCompleted();
        }
        publishRequestHandledEvent(request, response, startTime, failureCause);
    }
}

分析:
    这个方法主要分为六个部分, 接下来我们对这些进行拆分, 一个个分析
```
- LocalContext&RequestAttributes
```java
首先来看前三个部分, LocalContext、RequestAttributes、initContextHolders

先来说一下什么是LocalContext吧, 翻译过来是语言环境, 比如国际化相关的功能就跟语言环境有关, 中文环境还是
英文环境这样的意思, request请求对象中有一个getLocale方法用来获取当前的语言环境, 将其保存在LocalContext
中的局部变量中, 以SimpleLocaleContext为例:
public class SimpleLocaleContext implements LocaleContext {
	private final Locale locale;
	public SimpleLocaleContext(@Nullable Locale locale) {
		this.locale = locale;
	}

	public Locale getLocale() { return this.locale; }
}

很简单的一个代码, 说明了语言环境其实是将Local对象保存了起来而已, 如果想要在整个请求任何地方都能拿到这个
语言环境, 或者说拿到这个Local对象, 大家应该很容易想到用ThreadLocal来保存就好了, LocaleContextHolder
就是一个ThreadLocal, 用来保存LocaleContext的, 接下来看看上面这个processRequest的第一部分:
LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
LocaleContext localeContext = buildLocaleContext(request);

很简单, 从TheadLocal中拿到之前的语言环境, 临时保存起来, 然后利用buildLocaleContext方法构建当前的语言
环境对象, 这个方法被DispatcherServlet类重写了:
    protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

看到上面的代码, 可以看到, 最终都是利用一个LocaleResolver来解析语言环境对象的, 即SpringMVC利用这个组件
来完成对Local的解析, 那这第一部分就很清晰了, 将之前的语言环境临时保存, 构建本次请求的语言环境, 在原生的
请求上增加了LocaleResolver语言环境解析器来进行解析

ServletRequestAttributes对象, 其实这个对象在之前我们就用到了, 之前讲解@SessionAttributes注解的时候,
将Model数据放入到session中最终就是利用这个ServletRequestAttributes对象的, 该对象的功能很简单, 保存了
当前请求的HttpServletRequest、HttpServletResponse、HttpSession, 提供了对session属性的设置和获取, 此
时再来看这第二部分的代码:
    RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
    ServletRequestAttributes requestAttributes 
                            = buildRequestAttributes(request, response, previousAttributes);

先从RequestContextHolder中获取原来的ServletRequestAttributes, 临时保存起来, 然后利用request、
response、previousAttributes构建一个ServletRequestAttributes, 在上面是构建LocaleContext

到此为止, LocaleContext和ServletRequestAttributes就已经构建好了, 我们之前只看到了从
LocaleContextHolder和RequestContextHolder中获取这两个对象, 接下来第三部分的代码就是将当前请求的这两个
对象放入到对应的ThreadLocal中, 即initContextHolders(request, localeContext, requestAttributes);
的调用, 我们来看看即initContextHolders方法:

private void initContextHolders(HttpServletRequest request, LocaleContext localeContext, 
       RequestAttributes requestAttributes) {

    if (localeContext != null) {
        LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
    }
    if (requestAttributes != null) {
        RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
    }
}

小小的总结: 
    整个processRequest方法的前三部分, 就是构建LocaleContext、ServletRequestAttributes, 然后将他们保
    存到ThreadLocal中, 这样我们在整个请求的任何地方都能获取到这两个对象了, 举个例子:
    HttpServletRequest request = ( (ServletRequestAttributes)RequestContextHolder
                                                .getRequestAttributes() ).getRequest();

    这样我们就能在请求的任意地方获取HttpServletRequest对象了
```

- resetContextHolders&publishRequestHandledEvent
```java
在processRequest方法的finally语句中, 调用了这两个方法, resetContextHolders方法的调用就是因为此时请求
结束了, 需要将当前线程上下文ThreaLocal中的LocaleContext以及ServletRequestAttributes恢复到请求之前的
状态, 而publishRequestHandledEvent方法就是发布了一个事件而已, 大家有兴趣看下, 这个对我们分析执行流程
没多大意义, 就不进行分析了
```

- doService方法
```java
一共六个部分, 前面已经分析了五个部分, 代码都比较简单, 而第四部分doService方法才是真正用来处理请求的, 这
个方法里面做的事情对执行流程的分析也没多大意义, 我们简单的过一下, 最后着重来讲该方法中触发执行流程处理的
核心方法, 在这个方法中, 先是对request attributes中满足一定规则的key-value进行了保存, 最后在finally中
进行了restore, 这里我们不用去理会, 如果大家感兴趣可以研究下是干嘛的, 笔者也没去研究, 之后往request的
Attribute中添加了一些key-value, 将WebApplicationContext、localeResolver、themeResolver、
ThemeSource放到了请求域中, 然后是对flashMapManager的处理, 这个涉及到重定向相关的应用, 大家有兴趣了解
一下, 因为重定向的时候是多个不同的请求, 请求域是不能共享的, 那么如果将一些数据从A请求带到重定向后的B请求
又不借助session呢, SpringMVC提供了一个FlashMap组件专门完成这样的功能, 大家有兴趣可以了解下, 由于用的
不多就不进行展开了, 最后, 调用了doDispatch方法开始真正的处理请求!!!同时也是处理请求中最为复杂的部分!!
```

#### doDispatch处理请求的核心
- 大致的流程分析
```java
在前面的文章中, 我们对HandlerMapping和HandlerAdapter进行了详细的分析, 由此可以知道, 日常工作中使用的
最多的@RequestMapping注解来表示一个请求的情况下, @RequestMapping注解本身由RequestMappingInfo这个类
对象来表示, 而被该注解标注的方法则是用HandlerMethod来表示的, 在AbstractHandlerMethodMapping中有一个
MappingRegistry对象, url到RequestMappingInfo, RequestMappingInfo到HandlerMethod的映射都是在这个对
象中存储的, 这也是HandlerMapping的作用, 我们能通过url找到对应的HandlerMethod

上面描述中处理请求的HandlerMethod在SpringMVC中也被称为handler, handler的类型是不确定的, 仅仅说对于
@RequestMapping这样的情况下handler的表现形式是HandlerMethod而已, 当我们不是通过@RequestMapping来完成
映射的时候, handler就不一样了, 比如说Controller接口, 注意, 不是@Controller注解, 该接口也可以被用来处
理请求, 只不过是被配置在SimpleUrlHandlerMapping里面而已, Controller接口的实现类也被称为handler

handler的种类这么多, 为了统一调用, 从而引入了适配器模式, 提供一个公共的适配器, 不同类型的handler通过实
现该接口的方法的公共方法来实现自己的调用, 从而就有了各种HandlerAdapter, 我们之前着重讲解了
RequestMappingHandlerAdapter, 所有的适配器中, 通过supports方法来判断该适配器是否能够处理当前的
handler, 如果能, 则调用handle方法来完成调用

以上的内容在前面几篇文章中笔者已经详细的讲解了其中的细节, 而doDispatch处理请求很简单, 通过当前的url以及
遍历所有的HandlerMapping, 如果在一个HandlerMapping中能够通过url找到对应的url, 则返回其中存储的Handler
对于RequestMappingHandlerMapping来说, 返回的就是一个HandlerMethod, 而对于SimpleUrlHandlerMapping来
说, 可能返回的就是Controller的实现类, 注意, 这里是可能....因为还可能有其他类型的handler也是存储在这个
HandlerMapping的

在SpringMVC中, 利用handler处理请求的时候, 同时提供了拦截器, 即HandlerInterceptor, 拦截器有三个方法,
分别可以在handler被调用前, 调用后, 调用完成的同时被调用, 伪代码如下:
    try {
        interceptor.preHandle();
        handler.handle();
        interceptor.postHandle();
    } catch (Exception) {}
    finally {
        interceptor.afterCompletion();
    }

当我们在HandlerMapping中找到了对应的handler后, 就会将handler和HandlerInterceptor封装为
HandlerExecutionChain对象, 从而方便直接获取两个对象, 再往后, 遍历所有的HandlerAdapter, 调用其
supports方法对该handler进行验证, 如果找到了对应的Adapter则返回, 之后调用这个Adapter的handle方法完成对
请求的处理, 该方法返回一个ModelAndView对象, 当我们返回一个字符串表示jsp文件的时候, 这个jsp的路径就被存
储在了这个ModelAndView中, 其实一开始是存储在之前我们分析的ModelAndViewContainer中的, 只不过最后将其放
在了ModelAndView返回了而已, 最后利用ModelAndView进行视图的渲染, 大致的流程就是这样, 接下来我们开始对
源码进行分析
```
- doDispatch源码(省略了一些异常处理以及异步请求的内容)
```java
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    HttpServletRequest processedRequest = request;
    HandlerExecutionChain mappedHandler = null;
    boolean multipartRequestParsed = false;

    try {
        ModelAndView mv = null;
        Exception dispatchException = null;

        processedRequest = checkMultipart(request);
        multipartRequestParsed = (processedRequest != request);

        mappedHandler = getHandler(processedRequest);
        if (mappedHandler == null) {
            noHandlerFound(processedRequest, response);
            return;
        }

        HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

        if (!mappedHandler.applyPreHandle(processedRequest, response)) {
            return;
        }

        mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

        applyDefaultViewName(processedRequest, mv);
        mappedHandler.applyPostHandle(processedRequest, response, mv);
        processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
    } catch (Throwable err) {
        triggerAfterCompletion(processedRequest, response, mappedHandler,
                new NestedServletException("Handler processing failed", err));
    }
}

纵观整个精简后的doDispatch方法, 结构非常的清晰, processedRequest表示真正的请求对象, mappedHandler就
是之前我们分析的, 将handler和HandlerInterceptor封装起来的对象, multipartRequestParsed表示是否对文件
上传这样的功能进行了解析, 因为SpringMVC是有提供文件上传功能的, 所以不能直接用HttpServletRequest来表示
如果是文件上传, 那么还要进行一次请求的解析, 才能够得到最终的请求对象processedRequest

mappedHandler = getHandler(processedRequest);
if (mappedHandler == null) {
    noHandlerFound(processedRequest, response);
    return;
}

这段代码就是找到对应的handler, 将handler和HandlerIntercepter进行合并, 变成一个HandlerExecutionChain
对象, 如果通过url没有找到handler, 就执行if语句块的内容, 即抛出一个异常

HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

mappedHandler.getHandler()获取对应的handler, 利用这个handler来遍历所有的HandlerAdapter, 找到合适的
HandlerAdapter并返回

if (!mappedHandler.applyPreHandle(processedRequest, response)) { return; }
mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
applyDefaultViewName(processedRequest, mv);
mappedHandler.applyPostHandle(processedRequest, response, mv);

遍历HandlerExecutionChain中的所有拦截器, 调用其preHandle方法, 如果返回true, 那么就继续执行, 返回
false则就不执行了, 调用HandlerAdapter的handle方法, 如果是@RequestMapping的情况, 则调用的是
AbstractHandlerMethodAdapter的hanlde方法, 返回一个ModelAndView对象, 这个方法的调用我们在上一篇文章已
经详细的讲解了, 这里就不再进行展开了, 最后调用HandlerExecutionChain中所有拦截器的postHandle方法, 
applyDefaultViewName是因为当我们返回的ModelAndView中没有View的时候, 比如我们@RequestMapping标注的方
法返回的是void或者有被@ResponseBody标注的时候, 就是没有视图的, 此时会赋予一个默认的视图, 里面的代码很
简单, 大家有兴趣可以看下

在上面doDispatch的代码中, 我们可以看到在异常捕获后调用了triggerAfterCompletion方法, 里面其实就是对
HandlerExecutionChain中所有拦截器的afterCompletion方法的调用, 代码也很简单
```

- processDispatchResult处理视图
```java
上面的分析中, 已经将doDispatch分析完了, 有了前面文章的铺垫, 对这个方法的分析简直是太简单了, 我们还剩下
一个事情没有做, 假设返回了视图, 怎么对这个视图进行处理呢?比如:
    @RequestMapping( "/test" )
    public String test () {
        return "index"
    }

下面我们直接利用注释对processDispatchResult方法进行描述, 因为比较简单, 就不采用之前的方式进行分析了:

private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			HandlerExecutionChain mappedHandler, ModelAndView mv, Exception exception) {
    boolean errorView = false;

    /**
     * 在doDispatch中, 会将异常进行捕获, 放入到一个Exception对象中, 对视图进行解析的时候会传进来
     * 如果存在异常, 那么就对异常进行处理, 比如说返回一个异常视图(如果配置了异常视图的话)
     */
    if (exception != null) {
        if (exception instanceof ModelAndViewDefiningException) {
            mv = ((ModelAndViewDefiningException) exception).getModelAndView();
        }
        else {
            Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
            mv = processHandlerException(request, response, handler, exception);
            errorView = (mv != null);
        }
    }

    // 如果视图不是空的, 就开始渲染视图到前端页面
    if (mv != null && !mv.wasCleared()) {
        render(mv, request, response);
    }

    // 调用所有拦截器的afterCompletion方法
    if (mappedHandler != null) {
        mappedHandler.triggerAfterCompletion(request, response, null);
    }
}

render方法就不进行分析了, 大家有兴趣看下, 其实就是利用ViewResolver解析viewName, 获取到一个视图View对
象, 然后对http的状态进行一下设置, 最后调用视图对象View的render方法完成渲染, 对于JSP文件来说, 其实就是
forward到对应的jsp文件而已
```
