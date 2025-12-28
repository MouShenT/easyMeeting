package com.easymeeting.utils;

import org.apache.commons.codec.digest.DigestUtils;

public class MD5Utils {

    private MD5Utils() {
    }

    /**
     * MD5加密
     * @param text 明文
     * @return MD5密文（32位小写）
     */
    public static String encrypt(String text) {
        if (text == null) {
            return null;
        }
        return DigestUtils.md5Hex(text);
    }
}
