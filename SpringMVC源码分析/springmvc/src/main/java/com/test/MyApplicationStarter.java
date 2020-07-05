package com.test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.Tomcat;

public class MyApplicationStarter {
    public static void main(String[] args) throws Exception{
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(80);

        //只会去初始化一个 context的资源目录 并不会加载 web的生命周期, 所以我们要手动添加一个生命周期监听器来触发
        Context context = tomcat.addContext("/", System.getProperty("java.io.tmpdir"));
        context.addLifecycleListener((LifecycleListener) Class.forName(tomcat.getHost().getConfigClass()).newInstance());

        // 与Context相比, 提供了webapp资源文件目录, 二者可以选其一
        // tomcat.addWebapp("/","D:\\Users\\Desktop\\testProject\\springmvc\\src\\main\\webapp");

        tomcat.start();
        tomcat.getServer().await();
    }
}
