package com.alibaba.nacos.core.reed;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.nacos.plugin.auth.exception.AccessException;

import java.util.HashMap;
import java.util.Map;

public class ReedService {

    public static Long getUserId(String userToken) throws AccessException {
        //check token
        String url = "http://localhost:9090/auth-center/auth/user/token/verify";
        Map<String, Object> headers = new HashMap<>();
        headers.put("device_id", "nacos");
        headers.put("client_type", "WEB");
        headers.put("user_token", userToken);
        String post = ReedHttpUtils.post(url, new HashMap<>(), headers);
        JSONObject parse = JSONObject.parse(post);
        int code = parse.getIntValue("code");
        if (code != 0) {
            String message = parse.getString("message");
            throw new AccessException(message);
        }
        return parse.getLong("data");
    }

    public static String getUsername(Long userId) throws AccessException {
        String url = "http://localhost:8083/v1/user/" + userId;
        String get = ReedHttpUtils.get(url, new HashMap<>(), new HashMap<>());
        JSONObject parse = JSONObject.parse(get);
        int code = parse.getIntValue("code");
        if (code != 0) {
            String message = parse.getString("message");
            throw new AccessException(message);
        }
        JSONObject data = parse.getJSONObject("data");
        return data.getString("name");
    }

    public static String getLoginName(Long userId) throws AccessException {
        String url = "http://localhost:8083/v1/user/" + userId;
        String get = ReedHttpUtils.get(url, new HashMap<>(), new HashMap<>());
        JSONObject parse = JSONObject.parse(get);
        int code = parse.getIntValue("code");
        if (code != 0) {
            String message = parse.getString("message");
            throw new AccessException(message);
        }
        JSONObject data = parse.getJSONObject("data");
        return data.getString("login_name");
    }

    public static Map<String, Object> loginGetToken(String username, String password) throws AccessException {
        String url = "http://localhost:9090/auth-center/auth/user/" + username + "/token";
        Map<String, Object> headers = new HashMap<>();
        headers.put("device_id", "nacos");
        headers.put("client_type", "WEB");
        Map<String, Object> param = new HashMap<>();
        param.put("password", password);

        String post = ReedHttpUtils.post(url, param, headers);
        JSONObject parse = JSONObject.parse(post);
        int code = parse.getIntValue("code");
        if (code == 0 || code == 0x1011) {
            JSONObject data = parse.getJSONObject("data");
            String userToken = data.getString("reed_user_token");
            Long expire = data.getLong("expire");
            HashMap<String, Object> result =  new HashMap<>();
            result.put("userToken", userToken);
            result.put("expire", expire);
            return result;
        }else {
            String message = parse.getString("message");
            throw new AccessException(message);
        }

    }
}
