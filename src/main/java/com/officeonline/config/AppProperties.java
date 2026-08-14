package com.officeonline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String publicUrl = "http://localhost:8081";
    private String callbackUrl = "";
    private Storage storage = new Storage();
    private OnlyOffice onlyOffice = new OnlyOffice();

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public OnlyOffice getOnlyOffice() {
        return onlyOffice;
    }

    public void setOnlyOffice(OnlyOffice onlyOffice) {
        this.onlyOffice = onlyOffice;
    }

    public static class Storage {
        private String root = "/data/files";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class OnlyOffice {
        private String url = "http://localhost:8080";
        private String publicUrl = "";
        private String jwtSecret = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }
}
