package com.test;

import com.test.config.SpringConfig;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import java.util.Set;

public class MyWebInit implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) {
        System.out.println( "=====================" );
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register( SpringConfig.class );

        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        dispatcherServlet.setApplicationContext( context );
        ServletRegistration.Dynamic registration = ctx.addServlet( "dispatcherServlet", dispatcherServlet );
        registration.addMapping( "/" );
        registration.setLoadOnStartup( 1 );
    }
}
