package com.shopvi.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sinh slug URL từ chuỗi tiếng Việt: "Áo Thun Nam Đẹp" → "ao-thun-nam-dep".
 */
public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

    private SlugUtils() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        // "đ" không phải ký tự tổ hợp nên phải thay riêng trước khi bỏ dấu
        String s = input.replace('đ', 'd').replace('Đ', 'D');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-");
        return EDGE_DASHES.matcher(s).replaceAll("");
    }
}
