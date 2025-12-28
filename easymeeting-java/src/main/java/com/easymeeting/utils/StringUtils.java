package com.easymeeting.utils;

import java.util.Random;

public class StringUtils {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String DIGITS = "0123456789";
    private static final Random RANDOM = new Random();

    private StringUtils() {
    }

    /**
     * 生成12位用户ID
     * @return 12位随机字符串
     */
    public static String generateUserId() {
        return generateRandomString(CHARS, 12);
    }

    /**
     * 生成10位会议号
     * @return 10位随机数字字符串
     */
    public static String generateMeetingNo() {
        return generateRandomString(DIGITS, 10);
    }

    private static String generateRandomString(String chars, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

}
