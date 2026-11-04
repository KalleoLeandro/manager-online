package br.com.manager.utils;

public final class AssetUrls {

    private AssetUrls() {}

    public static String portrait(Long id) {
        return "/api/players/" + id + "/portrait";
    }

    public static String crest(Long id) {
        return "/api/teams/" + id + "/crest";
    }
}
