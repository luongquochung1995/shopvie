package com.shopvi.shipping.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tên tỉnh/xã theo mã (địa giới 2 cấp từ 01/07/2025, nguồn provinces.open-api.vn v2 — cùng file với frontend).
 * Hãng vận chuyển nhận tên địa danh chuẩn thay vì mã của ShopVi.
 */
@Component
public class Divisions {

    private static final String RESOURCE = "/data/vn-divisions.json";

    private final Map<String, String> provinces = new HashMap<>();
    /** "mãTỉnh/mãXã" → tên xã. */
    private final Map<String, String> wards = new HashMap<>();

    public Divisions(ObjectMapper objectMapper) {
        try (InputStream in = Divisions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            List<Province> list = objectMapper.readValue(in, new TypeReference<>() {
            });
            for (Province p : list) {
                provinces.put(p.code(), p.name());
                p.wards().forEach(w -> wards.put(p.code() + "/" + w.code(), w.name()));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Optional<String> provinceName(String provinceCode) {
        return Optional.ofNullable(provinces.get(provinceCode));
    }

    public Optional<String> wardName(String provinceCode, String wardCode) {
        return Optional.ofNullable(wards.get(provinceCode + "/" + wardCode));
    }

    /** Vùng miền theo mã tỉnh: 0 = Bắc (&lt; 38), 1 = Trung &amp; Tây Nguyên (38–74), 2 = Nam (≥ 75). */
    public static int region(String provinceCode) {
        int code;
        try {
            code = Integer.parseInt(provinceCode);
        } catch (NumberFormatException | NullPointerException ex) {
            return -1;
        }
        return code < 38 ? 0 : code < 75 ? 1 : 2;
    }

    record Province(String code, String name, List<Ward> wards) {
    }

    record Ward(String code, String name) {
    }
}
