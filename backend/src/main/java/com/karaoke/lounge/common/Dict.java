package com.karaoke.lounge.common;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * KTV 包厢运营 · 词典与业务规则参数表。
 *
 * <p>包厢 / 预订 / 消费 / 会员 / 歌曲 的状态枚举、营业时段与核心校验规则集中在此，
 * 便于出题时被要求调整或扩展。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 包厢状态 ----------------
    // 闭环流转：空闲 → 预订 → 使用中 →【结束使用】→ 待清场 →【验收放行】→ 空闲；
    // 停用为旁路（设备异常停用结论 / 人工停用），恢复后回到空闲。
    //
    // 取舍说明：在「及时释放包厢供下一场使用」与「未验收不得对外显示可用」之间，
    // 本系统明确选择后者 —— 待清场（清场任务未闭环）与停用的包厢一律不可预订、
    // 不对外显示空闲；宁可让下一场等验收，也不让未验收的包厢被排出去。
    public static final List<String> ROOM_STATUSES = List.of("空闲", "预订", "使用中", "待清场", "停用");

    private static final Map<String, String> ROOM_STATUS_NAME = Map.of(
            "空闲", "空闲可订",
            "预订", "已被预订",
            "使用中", "正在使用",
            "待清场", "清场未验收",
            "停用", "停用维修");

    // ---------------- 清场任务状态 ----------------
    // 待领取 → 清洁中 → 待验收 →（验收放行）已放行 /（退回整改）整改中 → 再提交回待验收；
    // 设备异常未处置时不得放行，须先异常处置或由经理作停用结论（→ 已停用）。
    public static final List<String> CLEANING_STATUSES =
            List.of("待领取", "清洁中", "待验收", "整改中", "已放行", "已停用");

    /** 未闭环（仍占用包厢可用性）的清场状态 */
    public static final List<String> CLEANING_OPEN_STATUSES =
            List.of("待领取", "清洁中", "待验收", "整改中");

    private static final Map<String, String> CLEANING_STATUS_NAME = Map.of(
            "待领取", "待保洁领取",
            "清洁中", "保洁清场中",
            "待验收", "待经理验收",
            "整改中", "退回整改中",
            "已放行", "验收合格已放行",
            "已停用", "设备异常停用");

    // ---------------- 卫生结果 ----------------
    public static final List<String> HYGIENE_RESULTS = List.of("合格", "不合格");

    // ---------------- 预订状态 ----------------
    public static final List<String> BOOKING_STATUSES = List.of("已预订", "已完成", "已取消");

    private static final Map<String, String> BOOKING_STATUS_NAME = Map.of(
            "已预订", "已预订待到店",
            "已完成", "已消费结账",
            "已取消", "已取消");

    // ---------------- 会员等级 ----------------
    public static final List<String> MEMBER_LEVELS = List.of("普通", "银卡", "金卡", "钻石");

    private static final Map<String, String> MEMBER_LEVEL_NAME = Map.of(
            "普通", "普通会员",
            "银卡", "银卡会员",
            "金卡", "金卡会员",
            "钻石", "钻石会员");

    // ---------------- 歌曲语种 ----------------
    public static final List<String> SONG_LANGUAGES = List.of("国语", "粤语", "英文", "日文");

    /** 语种筛选的显式“全部”取值：与具体语种一样是合法入参，区别于空白、缺省等模糊口径 */
    public static final String SONG_LANGUAGE_ALL = "全部";

    private static final Map<String, String> SONG_LANGUAGE_NAME = Map.of(
            "国语", "国语",
            "粤语", "粤语",
            "英文", "English",
            "日文", "日本語");

    // ---------------- 营业时段 ----------------
    /** 营业开始：11:00 */
    public static final int OPEN_HOUR = 11;

    /** 闭店：次日 02:00 */
    public static final int CLOSE_HOUR = 2;

    /** 单营业窗口最长时长（小时）：11:00 + 15h = 次日 02:00 */
    public static final int WINDOW_HOURS = 15;

    // ---------------- 取值方法 ----------------

    public static boolean isValidRoomStatus(String status) {
        return status != null && ROOM_STATUS_NAME.containsKey(status);
    }

    public static String roomStatusName(String status) {
        return status == null ? "" : ROOM_STATUS_NAME.getOrDefault(status, status);
    }

    public static boolean isValidBookingStatus(String status) {
        return status != null && BOOKING_STATUS_NAME.containsKey(status);
    }

    public static String bookingStatusName(String status) {
        return status == null ? "" : BOOKING_STATUS_NAME.getOrDefault(status, status);
    }

    public static boolean isValidCleaningStatus(String status) {
        return status != null && CLEANING_STATUS_NAME.containsKey(status);
    }

    public static String cleaningStatusName(String status) {
        return status == null ? "" : CLEANING_STATUS_NAME.getOrDefault(status, status);
    }

    public static boolean isValidHygieneResult(String result) {
        return result != null && HYGIENE_RESULTS.contains(result);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVEL_NAME.containsKey(level);
    }

    public static String memberLevelName(String level) {
        return level == null ? "" : MEMBER_LEVEL_NAME.getOrDefault(level, level);
    }

    public static boolean isValidSongLanguage(String language) {
        return language != null && SONG_LANGUAGE_NAME.containsKey(language);
    }

    /**
     * 是否为合法的语种筛选取值：具体语种或显式的“全部”。
     * 空白字符串不是合法筛选口径，调用方必须显式表达“全部”或干脆不传参。
     */
    public static boolean isValidSongLanguageFilter(String language) {
        return SONG_LANGUAGE_ALL.equals(language) || isValidSongLanguage(language);
    }

    public static String songLanguageName(String language) {
        return language == null ? "" : SONG_LANGUAGE_NAME.getOrDefault(language, language);
    }

    /** 预订人数不得为空且不得超过包厢容量 */
    public static boolean isPartySizeValid(Integer partySize, Integer capacity) {
        if (partySize == null || partySize < 1) {
            return false;
        }
        if (capacity == null) {
            return false;
        }
        return partySize <= capacity;
    }

    /**
     * 预订时段必须整体落在某个营业窗口 [当日 11:00, 次日 02:00] 之内：
     * 不允许早于 11:00 开场，不允许晚于次日 02:00 收场，也不允许跨越闭店时段。
     */
    public static boolean isBookingWindowValid(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return false;
        }
        if (!end.isAfter(start)) {
            return false;
        }
        LocalDateTime openStart = start.toLocalDate().atTime(OPEN_HOUR, 0);
        LocalDateTime openEnd = openStart.plusHours(WINDOW_HOURS);
        return !start.isBefore(openStart) && !end.isAfter(openEnd);
    }

    /** 两个时段是否重叠（用于同一包厢的预订冲突检测） */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                                   LocalDateTime bStart, LocalDateTime bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
