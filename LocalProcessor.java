package com.tuniu.mob.dist.site.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @Auther: zhengpeng
 * @Date: 2019/3/28 11:39
 * @Description:
 */
@Component
public class LocalProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalProcessor.class);

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private DefaultListableBeanFactory defaultListableBeanFactory;

    public void removeBean(String beanName) {
        defaultListableBeanFactory.removeBeanDefinition(beanName);
        LOGGER.info("移除{}成功", beanName);
    }

    public void addBean(String beanName, Class beanClass) {
        BeanDefinitionBuilder userBeanDefinitionBuilder = BeanDefinitionBuilder
                .genericBeanDefinition(beanClass);
        defaultListableBeanFactory.registerBeanDefinition(beanName,
                userBeanDefinitionBuilder.getRawBeanDefinition());
        LOGGER.info("注入{}成功", beanName);
    }

//    public void registerController(String beanName, Class beanClass) {
//        // 这里通过builder直接生成了mycontrooler的definition，然后注册进去
//        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
//        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
//        Method method;
//        try {
//            method = requestMappingHandlerMapping.getClass().getSuperclass().getSuperclass().getDeclaredMethod("detectHandlerMethods", Object.class);
//            method.setAccessible(true);
//            method.invoke(requestMappingHandlerMapping, beanName);
//        } catch (Exception e) {
//            LOGGER.warn("注册{}失败", beanName);
//        }
//    }


}
