package com.sky.test;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

// @SpringBootTest
public class HttpClientTest {
    @Test
    public void testGet() throws IOException {
        // 1.创建HttpClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();

        // 2.创建请求对象
        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");

        // 3.发送Get请求, 接收响应结果
        CloseableHttpResponse response = httpClient.execute(httpGet);

        // 4.解析结果
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务端返回的响应状态码为: " + statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("服务端返回的响应数据为: " + body);

        // 5.关闭资源
        response.close();
        httpClient.close();
    }

    @Test
    public void testPost() throws IOException {
        // 1.创建HttpClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();

        // 2.创建请求对象, 封装请求数据
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", "admin");
        jsonObject.put("password", "123456");

        // 指定请求体
        StringEntity entity = new StringEntity(jsonObject.toString());
        // 指定请求编码格式
        entity.setContentEncoding("utf-8");
        // 指定请求数据格式
        entity.setContentType("application/json");
        httpPost.setEntity(entity);

        // 3.发送Post请求, 接收响应结果
        CloseableHttpResponse response = httpClient.execute(httpPost);

        // 4.解析结果
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务端返回的状态码为: " + statusCode);

        HttpEntity responseEntity = response.getEntity();
        String body = EntityUtils.toString(responseEntity);
        System.out.println("服务端返回的数据为: " + body);

        // 5.关闭资源
        response.close();
        httpClient.close();
    }
}
