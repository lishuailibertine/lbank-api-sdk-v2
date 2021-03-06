package com.lbank.java.api.sdk.test.ws;

import com.alibaba.fastjson.JSONArray;
import com.lbank.java.api.sdk.enums.CharsetEnum;
import com.lbank.java.api.sdk.utils.DateUtils;
import okhttp3.*;
import okio.ByteString;
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;
import org.apache.commons.lang3.time.DateFormatUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @program: lbank-api-sdk-v2
 * @description: WebSocketClient
 * @author: steel.cheng
 * @create: 2019-12-17 17:23
 **/
public class WebSocketClient {
    private static WebSocket webSocket = null;
    private static Boolean isLogin = false;
    private static Boolean isConnect = false;

    /**
     * 解压函数
     * Decompression function
     *
     * @param bytes
     * @return
     */
    private static String uncompress(final byte[] bytes) {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
             final ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             final Deflate64CompressorInputStream zin = new Deflate64CompressorInputStream(in)) {
            final byte[] buffer = new byte[1024];
            int offset;
            while (-1 != (offset = zin.read(buffer))) {
                out.write(buffer , 0 , offset);
            }
            return out.toString();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 与服务器建立连接，参数为服务器的URL
     * connect server
     *
     * @param url
     */
    public void connection(final String url) {

        final OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(10 , TimeUnit.SECONDS)
                .build();
        final Request request = new Request.Builder()
                .url(url)
                .build();
        webSocket = client.newWebSocket(request , new WebSocketListener() {
            @Override
            public void onOpen(final WebSocket webSocket , final Response response) {
                isConnect = true;
                System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Connected to the server success!");

                //连接成功后，设置定时器，每隔25，自动向服务器发送心跳，保持与服务器连接
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        // task to run goes here
                        String ping = "{ 'action':'ping', 'ping':'0ca8f854-7ba7-4341-9d86-d3327e52804e' }";
                        sendMessage(ping);
                    }
                };
                final ScheduledExecutorService service = Executors
                        .newSingleThreadScheduledExecutor();
                // 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间
                service.scheduleAtFixedRate(runnable , 25 , 25 , TimeUnit.SECONDS);
            }

            @Override
            public void onMessage(final WebSocket webSocket , final String s) {
                System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Receive: " + s);
                if (null != s && s.contains("login")) {
                    if (s.endsWith("true}")) {
                        isLogin = true;
                    }
                }
            }

            @Override
            public void onClosing(WebSocket webSocket , final int code , final String reason) {
                System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Connection is disconnected !!！");
                webSocket.close(1000 , "Long time not to send and receive messages! ");
                webSocket = null;
                isConnect = false;
            }

            @Override
            public void onClosed(final WebSocket webSocket , final int code , final String reason) {
                System.out.println("Connection has been disconnected.");
                isConnect = false;
            }

            @Override
            public void onFailure(final WebSocket webSocket , final Throwable t , final Response response) {
                t.printStackTrace();
                System.out.println("Connection failed!");
                isConnect = false;
            }

            @Override
            public void onMessage(final WebSocket webSocket , final ByteString bytes) {
                final String s = WebSocketClient.uncompress(bytes.toByteArray());
                System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Receive: " + s);
                if (null != s && s.contains("login")) {
                    if (s.endsWith("true}")) {
                        isLogin = true;
                    }
                }
            }
        });
    }

    /**
     * 获得sign
     * sign
     *
     * @param message
     * @param secret
     * @return
     */
    private String sha256_HMAC(final String message , final String secret) {
        String hash = "";
        try {
            final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            final SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(CharsetEnum.UTF_8.charset()) , "HmacSHA256");
            sha256_HMAC.init(secret_key);
            final byte[] bytes = sha256_HMAC.doFinal(message.getBytes(CharsetEnum.UTF_8.charset()));
            hash = Base64.getEncoder().encodeToString(bytes);
        } catch (final Exception e) {
            System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + "Error HmacSHA256 ===========" + e.getMessage());
        }
        return hash;
    }


    /**
     * 订阅，参数为频道组成的集合
     * Bulk Subscription
     *
     * @param paramJson 参数的JSON字符串
     */
    public void subscribe(final String paramJson) {
        sendMessage(paramJson);
    }

    /**
     * 取消订阅，参数为频道组成的集合
     * unsubscribe
     *
     * @param paramJson 参数的JSON字符串
     */
    public void unsubscribe(final String paramJson) {
        sendMessage(paramJson);
    }

    private void sendMessage(final String str) {
        if (null != webSocket) {
            try {
                Thread.sleep(1000);
            } catch (final Exception e) {
                e.printStackTrace();
            }
            System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Send: " + str);
            if (isConnect) {
                webSocket.send(str);
                return;
            }
        }
        System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Please establish a connection before operation !!!");
    }

    /**
     * 断开连接
     * Close Connection
     */
    public void closeConnection() {
        if (null != webSocket) {
            webSocket.close(1000 , "User close connect !!!");
        } else {
            System.out.println(DateFormatUtils.format(new Date() , DateUtils.TIME_STYLE_S4) + " Please establish a connection before operation !!!");
        }
        isConnect = false;
    }

    public boolean getIsLogin() {
        return isLogin;
    }

    public boolean getIsConnect() {
        return isConnect;
    }
}
