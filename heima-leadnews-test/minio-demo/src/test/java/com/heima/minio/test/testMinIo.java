package com.heima.minio.test;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class testMinIo {

    @Test
    public void test1(){
        FileInputStream fileInputStream=null;

        try {
            fileInputStream=new FileInputStream("/Users/wangyiming/work/project/leadnews_app-web/app-web/index.html");
            //创建minio链接客户端
            MinioClient minioClient=MinioClient.builder().credentials("minio","Centos-8-163855").endpoint("http://43.140.194.170/:9090").build();
            //上传
            PutObjectArgs putObjectArgs=PutObjectArgs.builder()
                    .object("index.html")
                    .contentType("text/html")
                    .bucket("leadnews")
                    .stream(fileInputStream,fileInputStream.available(),-1)
                    .build();
          minioClient.putObject(putObjectArgs);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
