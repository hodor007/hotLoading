package com.tuniu.mob.dist.site.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @Auther: zhengpeng
 * @Date: 2019/3/21 19:43
 * @Description:
 */
@Component
public class FileWatcher implements ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileWatcher.class);

    public static ApplicationContext applicationContext;

    private WatchService watchService;

    @Value("${howLoading.directory}")
    private String watchFile;

    @Value("${howLoading.enabled}")
    private boolean hotLoading;

    @Autowired
    private LocalProcessor localProcessor;

    @PostConstruct
    public void init() {
        if (!hotLoading) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(watchFile);
            // 单层文件夹下
//            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
//                    StandardWatchEventKinds.ENTRY_MODIFY,
//                    StandardWatchEventKinds.ENTRY_DELETE);
            // 多层文件夹下
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("读取修改的文件失败", e);
        }
        new Thread(new WatchThread()).start();
        /**注册关闭钩子*/
        Thread hook = new Thread(() -> {
            try {
                watchService.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public class WatchThread implements Runnable {

        @Override
        public void run() {
            while (true) {
                final WatchKey key;
                try {
                    key = watchService.take();
                    for (WatchEvent<?> watchEvent : key.pollEvents()) {

                        final WatchEvent.Kind<?> kind = watchEvent.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        //创建事件
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            LOGGER.info("[新建]");
                        }
                        //修改事件
                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            // 热加载进jvm 并且加入spring
                            Path filePath = (Path) watchEvent.context();
                            if (!filePath.toString().endsWith(".java")) {
                                continue;
                            }
                            Path path = (Path) key.watchable();
                            LOGGER.info("[修改]:" + filePath.toString());
                            // 动态编译
                            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
                            LOGGER.info("加载文件到" + this.getClass().getResource("/").getPath());
                            String realFilePath = String.format("%s/%s", key.watchable().toString(), filePath.toString());
                            int status = javac.run(null, null, null, "-d", this.getClass().getResource("/").getPath(), realFilePath);
                            if (status != 0) {
                                LOGGER.info("没有编译成功！");
                            } else {
                                // 动态加载 在JVM中，一个实例是通过本身的类名+加载它的ClassLoader识别的，也就是说 不同的ClassLoader 加载同一个类在JVM是不同的。
                                String className = filePath.getFileName().toString().split("\\.")[0];
                                String beanName = className.substring(0, 1).toLowerCase() + className.substring(1);
//                                String classPathName = applicationContext.getType(beanName).getTypeName();
//                                String classPathName = applicationContext.getType(beanName).getGenericSuperclass().getTypeName();
                                String classPathName = realFilePath.split("java/")[1].replace("/",".").replace(".java","");
                                Class<?> cla = new SelfClassload(classPathName).loadClass(classPathName); // 需要重新new，不然一个加载器多次加载同一个类会报错 // 创建实例,判断这个对象是不是@Service等注解修饰，如果是则替换spring容器中的对象  对象中注入的不用管
                                if (cla.isAnnotationPresent(Component.class) || cla.isAnnotationPresent(Service.class)
                                        || cla.isAnnotationPresent(Repository.class) && cla.getInterfaces().length > 0) {
//                                  CouponServiceImpl couponService1 = () cla.newInstance(); // 会报错 相同的类，不同的ClassLoader，将导致ClassCastException异常
                                    //先移除spring中的bean
                                    localProcessor.removeBean(beanName);
                                    //注入spring中
                                    localProcessor.addBean(beanName, cla);
//                                    if (cla.isAnnotationPresent(Component.class) || cla.isAnnotationPresent(Service.class) || cla.isAnnotationPresent(Repository.class)) {
                                    // 利用反射给使用到上面属性的对象重新赋值
                                    Object springObj = applicationContext.getBean(beanName);
                                    String fieldName = beanName.endsWith("Impl") ? beanName.substring(0, beanName.length() - 4) : beanName;
                                    String[] beans = applicationContext.getBeanDefinitionNames();
                                    for (String bean : beans) {
                                        try {
                                            Object temp = applicationContext.getBean(bean);
                                            Field f;
                                            f = temp.getClass().getDeclaredField(fieldName);
                                            ReflectionUtils.makeAccessible(f);
                                            f.set(temp, springObj);
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    }
                                }
                                LOGGER.info("{}热加载完毕", beanName);
//                                }
//                                if (cla.isAnnotationPresent(Controller.class) || cla.isAnnotationPresent(RestController.class)) {
//                                    localProcessor.registerController(beanName, cla);
//                                }

                            }
                        }
                        //删除事件
                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            LOGGER.info("[删gh除]");
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                } catch (Exception e) {
                    LOGGER.warn("热加载出错", e);
//                    重新开启线程
                    new Thread(new WatchThread()).start();
                }
            }
        }
    }


    public static void main(String[] args) {
//        System.out.println(System.getProperty("user.dir"));
    }
}
