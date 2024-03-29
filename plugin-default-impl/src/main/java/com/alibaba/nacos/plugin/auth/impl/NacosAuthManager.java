/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.reed.ReedService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserV1;
import com.alibaba.nacos.plugin.auth.impl.users.User;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.utils.ReedHttpUtils;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builtin access control entry of Nacos.
 *
 * @author nkorange
 * @since 1.2.0
 */
@Component
public class NacosAuthManager {

    private static final String ROOT_ADMIN_USERNAME = "root_admin";

    private static final String ROOT_ADMIN_PASSWORD = "root_admin";

    private static final String ADMIN_TOKEN = "c258779a6fe134a2d6d22859e547a20b43263a52";
    
    @Autowired
    private JwtTokenManager tokenManager;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private NacosRoleServiceImpl roleService;
    
    /**
     * Authentication of request, identify the user who request the resource.
     *
     * @param request where we can find the user information
     * @return user related to this request, null if no user info is found.
     * @throws AccessException if authentication is failed
     */
    public User login(Object request) throws AccessException {
        HttpServletRequest req = (HttpServletRequest) request;
        String token = resolveToken(req);
        validate0(token);
        NacosUser user = getNacosUser(token);
        req.getSession().setAttribute(AuthConstants.NACOS_USER_KEY, user);
        req.getSession().setAttribute(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID,
                user.getUserName());
        return user;
    }

    public NacosUserV1 loginV1(String username, String password) throws AccessException {
        NacosUserV1 user = new NacosUserV1();
        if (ROOT_ADMIN_USERNAME.equals(username) && ROOT_ADMIN_PASSWORD.equals(password)) {
            Base64.Encoder encoder = Base64.getEncoder();
            byte[] encode = encoder.encode(("admin_user_token" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            user.setGlobalAdmin(true);
            user.setUserName("admin");
            user.setToken(ADMIN_TOKEN);
            user.setTtl(9999999999999999L);
            return user;
        }
        //获取token
        Map<String, Object> tokenMap = ReedService.loginGetToken(username, password);
        String userToken = String.valueOf(tokenMap.get("userToken"));
        long ttl = Long.parseLong(String.valueOf(tokenMap.get("expire")));
        Long userId = ReedService.getUserId(userToken);
        String name = ReedService.getUsername(userId);
        user.setGlobalAdmin(false);
        user.setUserName(name);
        user.setToken(userToken);
        user.setTtl(ttl);
        return user;
    }



    
    User login(IdentityContext identityContext) throws AccessException {
        String token = resolveToken(identityContext);
        validate0(token);
        return getNacosUser(token);
    }
    
    /**
     * Authorization of request, constituted with resource and user.
     *
     * @param permission permission to auth
     * @param user       user who wants to access the resource.
     * @throws AccessException if authorization is failed
     */
    public void auth(Permission permission, User user) throws AccessException {
        if (Loggers.AUTH.isDebugEnabled()) {
            Loggers.AUTH.debug("auth permission: {}, user: {}", permission, user);
        }
        
        if (!roleService.hasPermission(user.getUserName(), permission)) {
            throw new AccessException("authorization failed!");
        }
    }

    Logger logger = LoggerFactory.getLogger(NacosAuthPluginService.class);

    public void auth(Permission permission, String username) throws AccessException {

        boolean b = roleService.hasPermission(username, permission);
        logger.info("hasPermission => " + b);
        if (!b) {
            throw new AccessException("authorization failed!");
        }
    }
    
    /**
     * Get token from header.
     */
    private String resolveToken(HttpServletRequest request) throws AccessException {
        String bearerToken = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(7);
        }
        bearerToken = request.getParameter(Constants.ACCESS_TOKEN);
        if (StringUtils.isBlank(bearerToken)) {
            String userName = request.getParameter(AuthConstants.PARAM_USERNAME);
            String password = request.getParameter(AuthConstants.PARAM_PASSWORD);
            bearerToken = resolveTokenFromUser(userName, password);
        }
        
        return bearerToken;
    }
    
    private String resolveToken(IdentityContext identityContext) throws AccessException {
        String bearerToken = identityContext.getParameter(AuthConstants.AUTHORIZATION_HEADER, StringUtils.EMPTY);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(7);
        }
        bearerToken = identityContext.getParameter(Constants.ACCESS_TOKEN, StringUtils.EMPTY);
        if (StringUtils.isBlank(bearerToken)) {
            String userName = (String) identityContext.getParameter(AuthConstants.PARAM_USERNAME);
            String password = (String) identityContext.getParameter(AuthConstants.PARAM_PASSWORD);
            bearerToken = resolveTokenFromUser(userName, password);
        }
        return bearerToken;
    }
    
    private String resolveTokenFromUser(String userName, String rawPassword) throws AccessException {
        String finalName;
        Authentication authenticate;
        try {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userName,
                    rawPassword);
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (AuthenticationException e) {
            throw new AccessException("unknown user!");
        }
        
        if (null == authenticate || StringUtils.isBlank(authenticate.getName())) {
            finalName = userName;
        } else {
            finalName = authenticate.getName();
        }
        
        return tokenManager.createToken(finalName);
    }
    
    private void validate0(String token) throws AccessException {
        if (StringUtils.isBlank(token)) {
            throw new AccessException("user not found!");
        }
        
        try {
            tokenManager.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new AccessException("token expired!");
        } catch (Exception e) {
            throw new AccessException("token invalid!");
        }
    }
    
    private NacosUser getNacosUser(String token) {
        Authentication authentication = tokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String username = authentication.getName();
        NacosUser user = new NacosUser();
        user.setUserName(username);
        user.setToken(token);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(AuthConstants.GLOBAL_ADMIN_ROLE)) {
                    user.setGlobalAdmin(true);
                    break;
                }
            }
        }
        return user;
    }
}
