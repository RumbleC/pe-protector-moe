package moe.protector.pe.util;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import moe.protector.pe.interceptor.HeaderInterceptor;
import moe.protector.pe.interceptor.LoggingInterceptor;

import moe.protector.pe.interceptor.RetryInterceptor;
import okhttp3.ConnectionPool;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class Requests {
    private static final String TAG = "Requests";
    // 可以获取的内容
    public String text;
    public byte[] content;
    public int  status;

    // 私有的构建器
    private Requests() {
    }

    private static List<Cookie> cookieStore = new ArrayList<>();
    private static OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .cookieJar(new CookieJar() {
                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    if (url.host().contains("login")) {
                        cookieStore = cookies;
                    }
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore;
                    String host = url.host();
                    if (host.contains("passport") || host.contains("login")) {
                        return new ArrayList<>();
                    }
                    return cookies != null ? cookies : new ArrayList<>();
                }
            })
            .readTimeout(100, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(32, 5, TimeUnit.SECONDS))
            .addInterceptor(new HeaderInterceptor())
            .addInterceptor(new LoggingInterceptor())
            .addInterceptor(new RetryInterceptor(3))
            .build();  // 公用的客户端

    private static final int GET = 0;
    private static final int POST = 1;
    private static final int STRING_DATA = 0;
    private static final int MAP_DATA = 1;
    private static final int JSON_DATA = 2;

    private int method = 0;
    private String url = "";
    private Map<String, String> header = null;
    private Map<String, String> data_map = null;
    private String data_string = null;
    private int post_method = MAP_DATA;

    private boolean isZlibDecompress = false;
    private boolean isGZipDecompress = false;

    // 工厂函数进行构建
    public static class Builder {
        private int method = 0;
        private String url = "";
        private Map<String, String> header = null;
        private Map<String, String> data_map = null;
        private String data_string = null;
        private int post_method = MAP_DATA;

        private boolean isZlibDecompress = false;
        private boolean isGZipDecompress = false;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder get() {
            this.method = GET;
            return this;
        }

        public Builder header(Map<String, String> header) {
            this.header = header;
            return this;
        }

        public Builder post(String data) {
            this.method = POST;
            this.post_method = STRING_DATA;
            this.data_string = data;
            return this;
        }

        public Builder json(String data) {
            this.method = POST;
            this.post_method = JSON_DATA;
            this.data_string = data;
            return this;
        }

        public Builder post(Map<String, String> data) {
            this.method = POST;
            this.post_method = MAP_DATA;
            this.data_map = data;
            return this;
        }

        public Builder zlib() {
            this.isZlibDecompress = true;
            this.isGZipDecompress = false;
            return this;
        }

        public Builder gzip() {
            this.isZlibDecompress = false;
            this.isGZipDecompress = true;
            return this;
        }

        public Requests build() {
            Requests requests = new Requests();
            requests.method = this.method;
            requests.url = this.url;
            requests.data_map = this.data_map;
            requests.data_string = this.data_string;
            requests.header = this.header;
            requests.post_method = this.post_method;
            requests.isGZipDecompress = this.isGZipDecompress;
            requests.isZlibDecompress = this.isZlibDecompress;
            return requests;
        }
    }

    // 执行
    public Requests execute() {
        try {
            final Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(this.url);
            // 获取请求模式
            if (this.method == GET) {
                // get模式
                requestBuilder.get();
            } else {
                // post模式
                if (this.post_method == STRING_DATA) {
                    // post模式且数据为String
                    MediaType mediaType = MediaType.parse("text/x-markdown; charset=utf-8");
                    requestBuilder.post(RequestBody.create(mediaType, this.data_string));
                } else if (this.post_method == MAP_DATA) {
                    // post模式且数据为Map
                    FormBody.Builder formBody = new FormBody.Builder();
                    for (String key : this.data_map.keySet()) {
                        formBody.add(key, this.data_map.get(key));
                    }
                    RequestBody requestBody = formBody.build();
                    requestBuilder.post(requestBody);
                } else if (this.post_method == JSON_DATA) {
                    MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                    requestBuilder.post(RequestBody.create(mediaType, this.data_string));
                }
            }
            // 添加header
            if (this.header != null) {
                for (String key : header.keySet()) {
                    requestBuilder.addHeader(key, header.get(key));
                }
            }
            // 执行
            Request request = requestBuilder.build();
            Response okResponse = okHttpClient.newCall(request).execute();
            this.status = okResponse.code();
            // 取出网络流
            InputStream io = okResponse.body().byteStream();
            // 获取byte
            this.content = Encode.ioToByteArray(io);
            // 获取字符串
            if (this.isZlibDecompress) {  // zlib解压
                this.text = new String(Encode.zlib_decompress(this.content));
            } else if (this.isGZipDecompress) {  //gzip解压
                this.text = Encode.gzipUncompress(this.content);
            } else {
                this.text = new String(this.content);  // 直接获取字符串
            }
            return this;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            this.text = e.getMessage();
            return this;
        } catch (Exception e) {
            e.printStackTrace();
            return this;
        }
    }

    public static void clearCookie() {
        cookieStore.clear();
    }
}
