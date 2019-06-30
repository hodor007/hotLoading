package com.tuniu.mob.dist.site.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @Auther: zhengpeng
 * @Date: 2019/3/21 17:46
 * @Description:
 */
public class SelfClassload extends ClassLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelfClassload.class);

    private String packageName;

    public SelfClassload() {
    }

    public SelfClassload(String packageName) {
        this.packageName = packageName;
    }

    // 注意：同一个ClassLoader是不允许多次加载一个类的
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        //将传进来的转换成class文件
        String fileName = "/" + name.replace(".", "/") + ".class";
//        InputStream is = this.getClass().getResourceAsStream(fileName);  // 获取到的是libs下的business的jar包
        FileInputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            System.out.println("读取文件路径" + this.getClass().getResource("/").getPath() + "/" + name);
            is = new FileInputStream(new File(
                    this.getClass().getResource("/").getPath() + fileName));
            baos = new ByteArrayOutputStream();
            int b = 0;
            while ((b = is.read()) != -1) {
                baos.write(b);
            }
            byte[] bytes = baos.toByteArray();
            LOGGER.info("加载:{}", fileName);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (Exception e) {
//            LOGGER.warn("加载异常", e);
            return super.findClass(name);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.equals(packageName)) {
            return findClass(name);
        }
        return super.loadClass(name, false);
    }

}
