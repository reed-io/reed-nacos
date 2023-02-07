package com.alibaba.nacos.config.server.controller.reed;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class DESUtil {
    private static final String DEFAULT_SECURITY_CODE = "123456781234567812345678123456781234567812345678";

    private static final char[] DIGITS_LOWER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f' };

    /**
     * Used to build output as Hex
     */
    private static final char[] DIGITS_UPPER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
            'E', 'F' };

    /**
     * 加密
     *
     * @param source
     *            String
     * @return String
     */
    public static String encrypt(String source) {
        try {
            SecureRandom random = new SecureRandom();
            DESKeySpec desKey = new DESKeySpec(DEFAULT_SECURITY_CODE.getBytes());
            // 创建一个密匙工厂，然后用它把DESKeySpec转换成
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey securekey = keyFactory.generateSecret(desKey);
            // Cipher对象实际完成加密操作
            Cipher cipher = Cipher.getInstance("DES");
            // 用密匙初始化Cipher对象
            cipher.init(Cipher.ENCRYPT_MODE, securekey, random);
            // 现在，获取数据并加密
            // 正式执行加密操作
            byte[] bytes = cipher.doFinal(source.getBytes(StandardCharsets.UTF_8));
            return encodeHexString(bytes);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }


    private static String encodeHexString(final byte[] data) {
        return new String(encodeHex(data));
    }

    private static char[] encodeHex(final byte[] data) {
        return encodeHex(data, true);
    }


    private static char[] encodeHex(final byte[] data, final boolean toLowerCase) {
        return encodeHex(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }

    private static char[] encodeHex(final byte[] data, final char[] toDigits) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return out;
    }
}
