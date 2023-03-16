/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.auth.HttpProtocolAuthService;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.reed.ReedService;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Unified filter to handle authentication and authorization.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class AuthFilter implements Filter {

    private static final String ADMIN_TOKEN = "c258779a6fe134a2d6d22859e547a20b43263a52";

    private final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    private final ControllerMethodsCache methodsCache;
    
    private final HttpProtocolAuthService protocolAuthService;

    
    public AuthFilter(AuthConfigs authConfigs, ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
        this.protocolAuthService = new HttpProtocolAuthService(authConfigs);
        this.protocolAuthService.initialize();
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        try {
            Method method = methodsCache.getMethod(req);

            if (method == null) {
                chain.doFilter(request, response);
                return;
            }
//            logger.info("method => " + method.getName());
            logger.info("auth start, verification => [{}], method =>  {}, request => {} {}", method.isAnnotationPresent(Secured.class), method.getName(), req.getMethod(), req.getRequestURI());
            if (method.isAnnotationPresent(Secured.class)) {
                String accessToken = req.getHeader("accessToken");
                if (StringUtils.isBlank(accessToken)) {
                    accessToken = req.getParameter("accessToken");
                }
                if (StringUtils.isBlank(accessToken)) {
                    accessToken = req.getHeader("user_token");
                }
                if (StringUtils.isBlank(accessToken)) {
                    accessToken = req.getHeader("reed_user_token");
                }
                if (StringUtils.isBlank(accessToken)) {
                    throw new AccessException("Validate Authority failed.");
                }
                boolean isAdmin = ADMIN_TOKEN.equals(accessToken);
                String loginName = "root_admin";
                //admin忽略token
                if (!isAdmin) {
                    Long userId = ReedService.getUserId(accessToken);
                    if (userId == null) {
                        throw new AccessException("Validate Authority failed.");
                    }
                    loginName = ReedService.getLoginName(userId);
                }

                Secured secured = method.getAnnotation(Secured.class);

                Resource resource = protocolAuthService.parseResource(req, secured);
                logger.info("resource => " + resource);

                String action = secured.action().toString();
                logger.info("action => " + action);
                Permission permission = new Permission(resource, action);
                logger.info("permission => " + permission);
                boolean result = protocolAuthService.validateAuthority(loginName, permission);
                if (!result) {
                    // TODO Get reason of failure
                    throw new AccessException("Validate Authority failed.");
                }

            }
            chain.doFilter(request, response);
        } catch (AccessException e) {
            if (Loggers.AUTH.isDebugEnabled()) {
                Loggers.AUTH.debug("access denied, request: {} {}, reason: {}", req.getMethod(), req.getRequestURI(),
                        e.getErrMsg());
            }
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getErrMsg());
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ExceptionUtil.getAllExceptionMsg(e));
        } catch (Exception e) {
            Loggers.AUTH.warn("[AUTH-FILTER] Server failed: ", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server failed, " + e.getMessage());
        }
    }
    
    /**
     * Set identity id to request session, make sure some actual logic can get identity information.
     *
     * <p>May be replaced with whole identityContext.
     *
     * @param request         http request
     * @param identityContext identity context
     */
    private void injectIdentityId(HttpServletRequest request, IdentityContext identityContext) {
        String identityId = identityContext
                .getParameter(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID, StringUtils.EMPTY);
        request.getSession()
                .setAttribute(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID, identityId);
    }
}
