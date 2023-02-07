package com.alibaba.nacos.config.server.controller.reed;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.*;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.*;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.alibaba.nacos.config.server.utils.LogUtil.PULL_LOG;

@RestController
@RequestMapping("/reed/cs/config")
public class ConfigControllerReed {

    private static final int TRY_GET_LOCK_TIMES = 9;


    private final Logger LOGGER = LoggerFactory.getLogger(ConfigControllerReed.class);

    private ConfigServletInner inner;

    private ConfigInfoTagPersistService configInfoTagPersistService;

    private ConfigInfoBetaPersistService configInfoBetaPersistService;

    private ConfigInfoPersistService configInfoPersistService;


    public ConfigControllerReed(ConfigServletInner inner, ConfigInfoTagPersistService configInfoTagPersistService,
                            ConfigInfoBetaPersistService configInfoBetaPersistService,
                            ConfigInfoPersistService configInfoPersistService) {
        this.inner = inner;
        this.configInfoTagPersistService = configInfoTagPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoPersistService = configInfoPersistService;
    }

    /**
     * Get configure board information fail.
     *
     * @throws ServletException  ServletException.
     * @throws IOException       IOException.
     * @throws NacosApiException NacosApiException.
     */
    @GetMapping
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam("dataId") String dataId, @RequestParam("group") String group,
                          @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
                          @RequestParam(value = "tag", required = false) String tag)
            throws NacosException, IOException, ServletException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParamV2(tag);
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        this.doGetConfig(request, response, dataId, group, namespaceId, tag, isNotify, clientIp);
    }

     String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
                              String tenant, String tag, String isNotify, String clientIp)
            throws IOException, ServletException {

        boolean notify = false;
        if (StringUtils.isNotBlank(isNotify)) {
            notify = Boolean.parseBoolean(isNotify);
        }

        response.setHeader(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String autoTag = request.getHeader("Vipserver-Tag");

        String requestIpApp = RequestUtil.getAppName(request);
        int lockResult = tryConfigReadLock(groupKey);

        final String requestIp = RequestUtil.getRemoteIp(request);
        boolean isBeta = false;
        boolean isSli = false;
        if (lockResult > 0) {
            // LockResult > 0 means cacheItem is not null and other thread can`t delete this cacheItem
            FileInputStream fis = null;
            try {
                String md5 = Constants.NULL;
                long lastModified = 0L;
                CacheItem cacheItem = ConfigCacheService.getContentCache(groupKey);
                if (cacheItem.isBeta() && cacheItem.getIps4Beta().contains(clientIp)) {
                    isBeta = true;
                }

                final String configType =
                        (null != cacheItem.getType()) ? cacheItem.getType() : FileTypeEnum.TEXT.getFileType();
                response.setHeader("Config-Type", configType);
                FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(configType);
                String contentTypeHeader = fileTypeEnum.getContentType();
                response.setHeader(HttpHeaderConsts.CONTENT_TYPE, contentTypeHeader);
                response.setHeader(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_JSON);

                File file = null;
                ConfigInfoBase configInfoBase = null;
                PrintWriter out;
                if (isBeta) {
                    md5 = cacheItem.getMd54Beta();
                    lastModified = cacheItem.getLastModifiedTs4Beta();
                    if (PropertyUtil.isDirectRead()) {
                        configInfoBase = configInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
                    } else {
                        file = DiskUtil.targetBetaFile(dataId, group, tenant);
                    }
                    response.setHeader("isBeta", "true");
                } else {
                    if (StringUtils.isBlank(tag)) {
                        if (isUseTag(cacheItem, autoTag)) {
                            if (cacheItem.tagMd5 != null) {
                                md5 = cacheItem.tagMd5.get(autoTag);
                            }
                            if (cacheItem.tagLastModifiedTs != null) {
                                lastModified = cacheItem.tagLastModifiedTs.get(autoTag);
                            }
                            if (PropertyUtil.isDirectRead()) {
                                configInfoBase = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, autoTag);
                            } else {
                                file = DiskUtil.targetTagFile(dataId, group, tenant, autoTag);
                            }

                            response.setHeader(com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG,
                                    URLEncoder.encode(autoTag, StandardCharsets.UTF_8.displayName()));
                        } else {
                            md5 = cacheItem.getMd5();
                            lastModified = cacheItem.getLastModifiedTs();
                            if (PropertyUtil.isDirectRead()) {
                                configInfoBase = configInfoPersistService.findConfigInfo(dataId, group, tenant);
                            } else {
                                file = DiskUtil.targetFile(dataId, group, tenant);
                            }
                            if (configInfoBase == null && fileNotExist(file)) {
                                // FIXME CacheItem
                                // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                        ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp, notify);

                                // pullLog.info("[client-get] clientIp={}, {},
                                // no data",
                                // new Object[]{clientIp, groupKey});

                                return get404Result(response, true);
                            }
                            isSli = true;
                        }
                    } else {
                        if (cacheItem.tagMd5 != null) {
                            md5 = cacheItem.tagMd5.get(tag);
                        }
                        if (cacheItem.tagLastModifiedTs != null) {
                            Long lm = cacheItem.tagLastModifiedTs.get(tag);
                            if (lm != null) {
                                lastModified = lm;
                            }
                        }
                        if (PropertyUtil.isDirectRead()) {
                            configInfoBase = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                        } else {
                            file = DiskUtil.targetTagFile(dataId, group, tenant, tag);
                        }
                        if (configInfoBase == null && fileNotExist(file)) {
                            // FIXME CacheItem
                            // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
                            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                    ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp, notify && isSli);

                            // pullLog.info("[client-get] clientIp={}, {},
                            // no data",
                            // new Object[]{clientIp, groupKey});

                            return get404Result(response, true);
                        }
                    }
                }

                response.setHeader(Constants.CONTENT_MD5, md5);

                // Disable cache.
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                response.setHeader("Cache-Control", "no-cache,no-store");
                if (PropertyUtil.isDirectRead()) {
                    response.setDateHeader("Last-Modified", lastModified);
                } else {
                    fis = new FileInputStream(file);
                    response.setDateHeader("Last-Modified", file.lastModified());
                }
                LOGGER.info("PropertyUtil.isDirectRead() => " + PropertyUtil.isDirectRead());
                if (PropertyUtil.isDirectRead()) {
                    Pair<String, String> pair = EncryptionHandler
                            .decryptHandler(dataId, configInfoBase.getEncryptedDataKey(), configInfoBase.getContent());
                    out = response.getWriter();
                    out.print(JacksonUtils.toJson(Result.success(DESUtil.encrypt(pair.getSecond()))));
                    out.flush();
                    out.close();
                } else {
                    String fileContent = IoUtils.toString(fis, StandardCharsets.UTF_8.name());
                    String encryptedDataKey = cacheItem.getEncryptedDataKey();
                    Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey, fileContent);
                    String decryptContent = pair.getSecond();
                    out = response.getWriter();
                    out.print(JacksonUtils.toJson(Result.success(DESUtil.encrypt(decryptContent))));
                    out.flush();
                    out.close();
                }

                LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, requestIp, md5, TimeUtils.getCurrentTimeStr());

                final long delayed = System.currentTimeMillis() - lastModified;

                // TODO distinguish pull-get && push-get
                /*
                 Otherwise, delayed cannot be used as the basis of push delay directly,
                 because the delayed value of active get requests is very large.
                 */
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified,
                        ConfigTraceService.PULL_EVENT_OK, delayed, requestIp, notify && isSli);

            } finally {
                releaseConfigReadLock(groupKey);
                IoUtils.closeQuietly(fis);
            }
        } else if (lockResult == 0) {

            // FIXME CacheItem No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
            ConfigTraceService
                    .logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT_NOTFOUND, -1,
                            requestIp, notify && isSli);

            return get404Result(response, true);

        } else {

            PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
            return get409Result(response, true);

        }

        return HttpServletResponse.SC_OK + "";
    }

    private static boolean isUseTag(CacheItem cacheItem, String tag) {
        if (cacheItem != null && cacheItem.tagMd5 != null && cacheItem.tagMd5.size() > 0) {
            return StringUtils.isNotBlank(tag) && cacheItem.tagMd5.containsKey(tag);
        }
        return false;
    }

    private static boolean fileNotExist(File file) {
        return file == null || !file.exists();
    }

    private static int tryConfigReadLock(String groupKey) {

        // Lock failed by default.
        int lockResult = -1;

        // Try to get lock times, max value: 10;
        for (int i = TRY_GET_LOCK_TIMES; i >= 0; --i) {
            lockResult = ConfigCacheService.tryReadLock(groupKey);

            // The data is non-existent.
            if (0 == lockResult) {
                break;
            }

            // Success
            if (lockResult > 0) {
                break;
            }

            // Retry.
            if (i > 0) {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    LogUtil.PULL_CHECK_LOG.error("An Exception occurred while thread sleep", e);
                }
            }
        }

        return lockResult;
    }


    private static void releaseConfigReadLock(String groupKey) {
        ConfigCacheService.releaseReadLock(groupKey);
    }

    private String get404Result(HttpServletResponse response, boolean isV2) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        PrintWriter writer = response.getWriter();
        if (isV2) {
            writer.println(JacksonUtils.toJson(Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist")));
        } else {
            writer.println("config data not exist");
        }
        return HttpServletResponse.SC_NOT_FOUND + "";
    }

    private String get409Result(HttpServletResponse response, boolean isV2) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        PrintWriter writer = response.getWriter();
        if (isV2) {
            writer.println(JacksonUtils.toJson(Result
                    .failure(ErrorCode.RESOURCE_CONFLICT, "requested file is being modified, please try later.")));
        } else {
            writer.println("requested file is being modified, please try later.");
        }
        return HttpServletResponse.SC_CONFLICT + "";
    }

}
