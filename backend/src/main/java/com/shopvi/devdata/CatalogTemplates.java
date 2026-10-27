package com.shopvi.devdata;

import com.shopvi.devdata.DevDataSeeder.OptionGroup;
import com.shopvi.devdata.DevDataSeeder.Template;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dữ liệu mẫu cho seed dev. Shop index: 0 = thời trang, 1 = công nghệ, 2 = nhà cửa & làm đẹp. */
final class CatalogTemplates {

    static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Thời Trang Nam", List.of("Áo Thun Nam", "Quần Jeans Nam", "Giày Nam"));
        CATEGORIES.put("Thời Trang Nữ", List.of("Váy Đầm", "Áo Kiểu Nữ", "Túi Xách Nữ"));
        CATEGORIES.put("Điện Thoại & Phụ Kiện", List.of("Điện Thoại", "Ốp Lưng", "Tai Nghe"));
        CATEGORIES.put("Nhà Cửa & Đời Sống", List.of("Dụng Cụ Nhà Bếp", "Chăn Ga Gối", "Đèn Trang Trí"));
        CATEGORIES.put("Sắc Đẹp", List.of("Chăm Sóc Da", "Trang Điểm", "Nước Hoa"));
    }

    static final List<String> SUFFIXES = List.of("Cao Cấp", "Chính Hãng", "Phong Cách Hàn Quốc", "Basic", "2026",
            "Bản Giới Hạn", "Hot Trend", "");

    static final List<String> ORIGINS = List.of("Việt Nam", "Hàn Quốc", "Trung Quốc", "Nhật Bản", "Thái Lan");

    private static final OptionGroup COLORS = new OptionGroup("Màu sắc", List.of("Đen", "Trắng", "Xám", "Xanh navy", "Be"));
    private static final OptionGroup SIZES = new OptionGroup("Kích cỡ", List.of("S", "M", "L", "XL"));

    static final List<Template> ALL = List.of(
            new Template("ao-thun-nam", 0, List.of("Áo Thun Nam Cổ Tròn", "Áo Polo Nam", "Áo Thun Oversize"),
                    List.of("Coolmate", "Routine", "Yody"), List.of(COLORS, SIZES), 99_000, 299_000, 10_000,
                    Map.of("Chất liệu", "Cotton 100%")),
            new Template("quan-jeans-nam", 0, List.of("Quần Jeans Nam Ống Đứng", "Quần Jeans Slim Fit"),
                    List.of("Levi's", "Routine", "Owen"),
                    List.of(new OptionGroup("Màu sắc", List.of("Xanh nhạt", "Xanh đậm", "Đen")),
                            new OptionGroup("Size", List.of("29", "30", "31", "32", "33"))),
                    249_000, 699_000, 0, Map.of("Chất liệu", "Denim co giãn")),
            new Template("giay-nam", 0, List.of("Giày Sneaker Nam", "Giày Tây Nam Da", "Giày Chạy Bộ"),
                    List.of("Biti's", "Ananas", "Nike"),
                    List.of(new OptionGroup("Màu sắc", List.of("Đen", "Trắng", "Nâu")),
                            new OptionGroup("Size", List.of("39", "40", "41", "42", "43"))),
                    350_000, 1_500_000, 0, Map.of("Chất liệu đế", "Cao su")),
            new Template("vay-dam", 0, List.of("Váy Đầm Hoa Nhí", "Đầm Suông Công Sở", "Váy Maxi Đi Biển"),
                    List.of("Elise", "Ivy Moda", "Canifa"),
                    List.of(new OptionGroup("Màu sắc", List.of("Đỏ", "Hồng", "Trắng", "Đen")),
                            new OptionGroup("Kích cỡ", List.of("S", "M", "L"))),
                    199_000, 899_000, 0, Map.of("Chất liệu", "Voan lụa")),
            new Template("ao-kieu-nu", 0, List.of("Áo Sơ Mi Nữ", "Áo Croptop", "Áo Kiểu Tay Phồng"),
                    List.of("Canifa", "Elise", "Yody"), List.of(COLORS, new OptionGroup("Kích cỡ", List.of("S", "M", "L"))),
                    129_000, 459_000, 0, Map.of("Chất liệu", "Lụa")),
            new Template("tui-xach-nu", 0, List.of("Túi Xách Nữ Đeo Vai", "Túi Tote Vải", "Túi Mini Đeo Chéo"),
                    List.of("Vascara", "Juno", "Charles & Keith"),
                    List.of(new OptionGroup("Màu sắc", List.of("Đen", "Kem", "Nâu", "Hồng"))),
                    189_000, 1_290_000, 0, Map.of("Chất liệu", "Da tổng hợp")),
            new Template("dien-thoai", 1, List.of("Điện Thoại", "Smartphone"),
                    List.of("iPhone 16", "Samsung Galaxy S25", "Xiaomi 15", "OPPO Reno 13"),
                    List.of(new OptionGroup("Màu sắc", List.of("Đen", "Trắng", "Xanh")),
                            new OptionGroup("Dung lượng", List.of("128GB", "256GB", "512GB"))),
                    6_990_000, 24_990_000, 3_000_000, Map.of("Bảo hành", "12 tháng")),
            new Template("op-lung", 1, List.of("Ốp Lưng Trong Suốt", "Ốp Lưng Chống Sốc", "Ốp Lưng MagSafe"),
                    List.of("Spigen", "UAG", "Baseus"),
                    List.of(new OptionGroup("Mẫu máy", List.of("iPhone 15", "iPhone 16", "iPhone 16 Pro", "Galaxy S25")),
                            new OptionGroup("Màu sắc", List.of("Trong suốt", "Đen", "Xanh"))),
                    49_000, 390_000, 0, Map.of("Chất liệu", "TPU + PC")),
            new Template("tai-nghe", 1, List.of("Tai Nghe Bluetooth", "Tai Nghe Chống Ồn", "Tai Nghe Gaming"),
                    List.of("Sony", "JBL", "Soundpeats", "Apple AirPods"),
                    List.of(new OptionGroup("Màu sắc", List.of("Đen", "Trắng", "Xanh dương"))),
                    290_000, 5_990_000, 0, Map.of("Kết nối", "Bluetooth 5.3")),
            new Template("dung-cu-nha-bep", 2, List.of("Chảo Chống Dính", "Nồi Inox 3 Đáy", "Bộ Dao Nhà Bếp"),
                    List.of("Sunhouse", "Lock&Lock", "Elmich"),
                    List.of(new OptionGroup("Kích thước", List.of("20cm", "24cm", "28cm"))),
                    149_000, 890_000, 50_000, Map.of("Dùng cho bếp từ", "Có")),
            new Template("chan-ga-goi", 2, List.of("Bộ Chăn Ga Gối Cotton", "Chăn Hè Thu", "Bộ Ga Gối Lụa"),
                    List.of("Everon", "Hanvico", "Sông Hồng"),
                    List.of(new OptionGroup("Màu sắc", List.of("Xám", "Kem", "Xanh mint")),
                            new OptionGroup("Kích thước", List.of("1m6 x 2m", "1m8 x 2m", "2m x 2m2"))),
                    390_000, 2_490_000, 100_000, Map.of("Chất liệu", "Cotton Hàn Quốc")),
            new Template("den-trang-tri", 2, List.of("Đèn Ngủ Để Bàn", "Đèn LED Dây", "Đèn Cây Đứng"),
                    List.of("Rạng Đông", "Philips", "Xiaomi"),
                    List.of(new OptionGroup("Ánh sáng", List.of("Vàng ấm", "Trắng", "Đổi màu RGB"))),
                    99_000, 1_190_000, 20_000, Map.of("Công suất", "10W")),
            new Template("cham-soc-da", 2, List.of("Sữa Rửa Mặt", "Serum Vitamin C", "Kem Chống Nắng"),
                    List.of("La Roche-Posay", "Cocoon", "Some By Mi", "Anessa"),
                    List.of(new OptionGroup("Dung tích", List.of("50ml", "100ml", "200ml"))),
                    129_000, 790_000, 80_000, Map.of("Loại da", "Mọi loại da")),
            new Template("trang-diem", 2, List.of("Son Kem Lì", "Phấn Nước Cushion", "Mascara Chống Trôi"),
                    List.of("3CE", "Maybelline", "Black Rouge", "Romand"),
                    List.of(new OptionGroup("Màu", List.of("01 Đỏ cam", "02 Hồng đất", "03 Đỏ gạch", "04 Nude"))),
                    99_000, 590_000, 0, Map.of("Hạn sử dụng", "36 tháng")),
            new Template("nuoc-hoa", 2, List.of("Nước Hoa Nữ", "Nước Hoa Nam", "Body Mist"),
                    List.of("Charme", "Dior", "Chanel", "Victoria's Secret"),
                    List.of(new OptionGroup("Dung tích", List.of("30ml", "50ml", "100ml"))),
                    250_000, 4_500_000, 400_000, Map.of("Nhóm hương", "Hương hoa cỏ")));

    private CatalogTemplates() {
    }
}
