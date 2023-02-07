package com.alibaba.nacos.plugin.auth.impl.users;

public class NacosUserV1 extends User{
    private String token;

    private boolean globalAdmin = false;

    private long ttl = 0L;

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isGlobalAdmin() {
        return globalAdmin;
    }

    public void setGlobalAdmin(boolean globalAdmin) {
        this.globalAdmin = globalAdmin;
    }

    @Override
    public String toString() {
        return "NacosUserV1{" +
                "token='" + token + '\'' +
                ", globalAdmin=" + globalAdmin +
                ", ttl=" + ttl +
                '}';
    }

}
