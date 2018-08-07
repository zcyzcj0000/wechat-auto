package xyz.ipurple.wechat.base.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.List;

/**
 * @ClassName: HttpClientHelper
 * @Description: //TODO
 * @Author: zcy
 * @Date: 2018/8/6 10:34
 * @Version: 1.0
 */
public class HttpClientHelper {
    public static HttpResponse doPost(String url, List<NameValuePair> params) {
        return doPost(url, params, null);
    }

    public static HttpResponse doPost(String url, List<NameValuePair> params, String cookie) {
        HttpResponse httpResponse = null;
        String responseContent = "";
        try {
            CookieStore cookieStore = new BasicCookieStore();
            CloseableHttpClient httpClient = HttpClients.custom().setDefaultCookieStore(cookieStore).build();
            HttpPost httpPost = new HttpPost(url);

            if (StringUtils.isNotBlank(cookie)) {
                httpPost.setHeader("cookie", cookie);
            }
            if (params != null) {
                httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
            }
            CloseableHttpResponse response = httpClient.execute(httpPost);

            //获取响应内容
            HttpEntity httpEntity = response.getEntity();
            if (httpEntity != null) {
                responseContent = EntityUtils.toString(httpEntity, "utf-8");
            }

            httpResponse = new HttpResponse();
            httpResponse.setContent(responseContent);
            httpResponse.setCookie(getCookie(cookieStore));
            //释放资源
            EntityUtils.consume(httpEntity);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return httpResponse;
    }

    public static void doPostFile(String url, List<NameValuePair> params, String path, String fileName) {
        InputStream in = null;
        try {
            HttpEntity httpEntity = executePost(url, params);
            in = httpEntity.getContent();
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
            file = new File(path + File.separator + fileName);
            FileOutputStream fos = new FileOutputStream(file);
            int l = -1;
            byte[] tmp = new byte[1024];
            while((l = in.read(tmp)) != -1) {
                fos.write(tmp,0, l);
            }
            fos.flush();
            fos.close();
            //释放资源
            EntityUtils.consume(httpEntity);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            // 关闭低层流。
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static HttpEntity executePost(String url, List<NameValuePair> params) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        if (params != null) {
            httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
        }
        CloseableHttpResponse response = httpClient.execute(httpPost);
        return response.getEntity();
    }

    private static String getCookie(CookieStore cookieStore) {
        StringBuffer cookie = new StringBuffer();
        List<Cookie> cookies = cookieStore.getCookies();
        for (int i = 0; i < cookies.size(); i++) {
            cookie.append(cookies.get(i).getName())
                    .append(":")
                    .append(cookies.get(i).getValue())
                    .append(";");
        }
        return cookie.toString();
    }

}