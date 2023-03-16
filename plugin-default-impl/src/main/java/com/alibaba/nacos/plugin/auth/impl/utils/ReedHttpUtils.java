package com.alibaba.nacos.plugin.auth.impl.utils;


import com.alibaba.fastjson2.JSONException;
import okhttp3.*;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ReedHttpUtils {

    private static final OkHttpClient client = new OkHttpClient.Builder().build();


    public static String get(String url) {
        return get(url, new HashMap<>(), new HashMap<>());
    }

    public static String get(String url, Map<String, Object> params, Map<String, Object> headers)  {
        String paramString = "";
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        if (stringBuilder.length() > 0) {
            paramString = stringBuilder.substring(0, stringBuilder.length() - 1);
            url = url + "?" + paramString;
        }
        Request.Builder requestBuilder = new Request.Builder();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }

        requestBuilder.url(url)
                .build();
        return requestHandler(requestBuilder.build());
    }



    public static String post(String url) {
        return post(url, new HashMap<>(), new HashMap<>());
    }

    private static String requestHandler(Request request) {
        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("response error");
            }else {
                return response.body().string();
            }
        }catch (JSONException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String post(String url, Map<String, Object> params, Map<String, Object> headers) {
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            builder.add(entry.getKey(), entry.getValue().toString());
        }
        RequestBody requestBody = builder.build();

        Request.Builder requestBuilder = new Request.Builder();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }

       requestBuilder.url(url)
                .post(requestBody)
                .build();
        return requestHandler(requestBuilder.build());
    }

    public static String put(String url, Map<String, Object> params, Map<String, Object> headers) {
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            builder.add(entry.getKey(), entry.getValue().toString());
        }
        RequestBody requestBody = builder.build();
        Request.Builder requestBuilder = new Request.Builder();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }

        requestBuilder.url(url)
                .put(requestBody)
                .build();
        return requestHandler(requestBuilder.build());
    }

    public static String put(String url)  {
        return put(url, new HashMap<>(), new HashMap<>());
    }

    public static String delete(String url, Map<String, Object> params, Map<String, Object> headers) {
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            builder.add(entry.getKey(), entry.getValue().toString());
        }
        RequestBody requestBody = builder.build();
        Request.Builder requestBuilder = new Request.Builder();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }
        requestBuilder.url(url)
                .delete(requestBody)
                .build();
        return requestHandler(requestBuilder.build());
    }


    public static String uploadFile(String url, MultipartFile multipartFile, Map<String, Object> params, Map<String, Object> headers,
                                        String method, String fileKey) {
        try {
            String mediaType = ObjectUtils.isEmpty(multipartFile.getContentType()) ? "image/png" : multipartFile.getContentType();
            RequestBody fileBody  = RequestBody.create(MediaType.parse(mediaType), multipartFile.getBytes());

            MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder();
            requestBodyBuilder.addFormDataPart(fileKey, multipartFile.getOriginalFilename(), fileBody);
            requestBodyBuilder.setType(MultipartBody.FORM);
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                requestBodyBuilder.addFormDataPart(entry.getKey(), entry.getValue().toString());
            }

            Request.Builder requestBuilder = new Request.Builder();
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
            }

            requestBuilder.url(url)
                    .method(method.toUpperCase(), requestBodyBuilder.build())
                    .build();
            return requestHandler(requestBuilder.build());
        }catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String delete(String url)  {
        return delete(url, new HashMap<>(),new HashMap<>());
    }

    private ReedHttpUtils() {}
}

