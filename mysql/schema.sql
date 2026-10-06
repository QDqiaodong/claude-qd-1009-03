SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- KTV 包厢运营 · 包厢 / 预订 / 消费 / 会员 / 歌曲
-- 库名 karaoke_lounge；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，种子数据里的时间一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR；
--      预订/消费等具体时段用北京时间字面量直接写入（serverTimezone=Asia/Shanghai）。

CREATE DATABASE IF NOT EXISTS karaoke_lounge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE karaoke_lounge;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS cleaning_record;
DROP TABLE IF EXISTS cleaning_task;
DROP TABLE IF EXISTS member_transaction;
DROP TABLE IF EXISTS consumption;
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS song;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS room;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 包厢 ----------------
-- 状态：空闲/预订/使用中/待清场/停用。
-- 闭环取舍：待清场（清场任务未验收放行）与停用期间，包厢不可预订、不对外显示空闲。
CREATE TABLE room (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code  VARCHAR(16)  NOT NULL UNIQUE,
    room_name  VARCHAR(64)  NOT NULL,
    capacity   INT          NOT NULL COMMENT '可容纳人数',
    min_spend  DECIMAL(10,2) NOT NULL COMMENT '最低消费（元）',
    status     VARCHAR(16)  NOT NULL COMMENT '空闲/预订/使用中/待清场/停用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 预订 ----------------
CREATE TABLE booking (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id    BIGINT       NOT NULL,
    member_id  BIGINT       NOT NULL,
    start_at   DATETIME     NOT NULL COMMENT '开始时间（北京时间）',
    end_at     DATETIME     NOT NULL COMMENT '结束时间（北京时间）',
    party_size INT          NOT NULL COMMENT '预订人数',
    status     VARCHAR(16)  NOT NULL COMMENT '已预订/已完成/已取消',
    settlement_method VARCHAR(16) COMMENT '结账方式：前台现付/储值余额',
    settlement_amount DECIMAL(10,2) COMMENT '结账合计金额',
    settled_at DATETIME COMMENT '结账成功时间',
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room (id),
    INDEX idx_booking_member_status (member_id, status, settled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 消费明细 ----------------
CREATE TABLE consumption (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT       NOT NULL,
    item_name  VARCHAR(64)  NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    at         DATETIME,
    CONSTRAINT fk_consumption_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员 ----------------
CREATE TABLE member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no  VARCHAR(16)  NOT NULL UNIQUE,
    member_name VARCHAR(32) NOT NULL,
    phone      VARCHAR(24)  NOT NULL,
    level      VARCHAR(16)  NOT NULL COMMENT '普通/银卡/金卡/钻石',
    balance    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '当前储值余额',
    CONSTRAINT ck_member_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员储值资金流水（只追加） ----------------
-- 结账扣减余额、会员续充与建档初始储值均在此留痕。amount 带符号：储值为正、扣款为负；
-- balance_after 记录该笔流水后的余额。扣款流水必须关联消费单（booking_id），并用唯一约束防重复扣款。
CREATE TABLE member_transaction (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT        NOT NULL,
    type          VARCHAR(16)   NOT NULL COMMENT '初始储值/储值/消费扣款',
    amount        DECIMAL(10,2) NOT NULL COMMENT '带符号金额：入账为正，扣款为负',
    balance_after DECIMAL(10,2) NOT NULL COMMENT '本笔流水后的余额',
    booking_id    BIGINT        COMMENT '扣款关联的消费单（预订 id）',
    remark        VARCHAR(255),
    created_at    DATETIME      NOT NULL,
    CONSTRAINT fk_member_txn_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_member_txn_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT uk_member_txn_booking UNIQUE (booking_id),
    INDEX idx_member_txn_member_time (member_id, created_at, id),
    CONSTRAINT ck_member_txn_balance_after CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 歌曲库 ----------------
CREATE TABLE song (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_code   VARCHAR(16)  NOT NULL UNIQUE,
    song_name   VARCHAR(64)  NOT NULL,
    singer      VARCHAR(32),
    language    VARCHAR(16)  COMMENT '国语/粤语/英文/日文',
    duration_sec INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 清场任务 ----------------
-- 每场使用结束后生成；未闭环（待领取/清洁中/待验收/整改中）期间包厢保持「待清场」不可接待。
-- 状态迁移全部走条件 UPDATE（WHERE status=期望前置状态），并发领取/重复验收只有一人命中。
CREATE TABLE cleaning_task (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id             BIGINT      NOT NULL,
    booking_id          BIGINT      COMMENT '触发本次清场的场次（预订）id，可空',
    status              VARCHAR(16) NOT NULL COMMENT '待领取/清洁中/待验收/整改中/已放行/已停用',
    cleaner_name        VARCHAR(32) COMMENT '保洁负责人（领取人）',
    manager_name        VARCHAR(32) COMMENT '验收经理',
    hygiene_result      VARCHAR(16) COMMENT '最近一轮卫生结果：合格/不合格',
    leftover_items      VARCHAR(255) COMMENT '最近一轮遗留物',
    equipment_issue     VARCHAR(255) COMMENT '最近一轮设备异常（麦克风/点歌屏等）',
    has_equipment_issue TINYINT(1)  NOT NULL DEFAULT 0,
    issue_resolved      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '设备异常是否已处置',
    issue_resolution    VARCHAR(255) COMMENT '设备异常处置记录',
    rework_note         VARCHAR(255) COMMENT '最近一次退回整改要求',
    accept_note         VARCHAR(255) COMMENT '验收意见/停用结论',
    submit_round        INT         NOT NULL DEFAULT 0 COMMENT '提交轮次',
    created_at          DATETIME    NOT NULL,
    claimed_at          DATETIME,
    submitted_at        DATETIME,
    finished_at         DATETIME COMMENT '闭环时间（放行/停用）',
    CONSTRAINT fk_cleaning_room FOREIGN KEY (room_id) REFERENCES room (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ---------------- 清场交接流水（只追加） ----------------
-- 每次创建/领取/提交/验收放行/退回整改/异常处置/停用都落一条；
-- 提交与退回时把当轮问题快照进 detail，前一次问题与处理记录永久保留。
CREATE TABLE cleaning_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT       NOT NULL,
    action        VARCHAR(16)  NOT NULL COMMENT '创建/领取/提交/验收放行/退回整改/异常处置/停用',
    operator_name VARCHAR(32)  NOT NULL COMMENT '操作人（保洁/经理/前台）',
    detail        VARCHAR(500) COMMENT '当轮内容快照',
    created_at    DATETIME     NOT NULL,
    CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES cleaning_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO room (room_code, room_name, capacity, min_spend, status) VALUES
('K01', '迷你包',   2,  188.00, '待清场'),
('K02', '小包',     4,  388.00, '预订'),
('K03', '中包',     8,  688.00, '使用中'),
('K04', '豪华中包', 10, 888.00, '待清场'),
('K05', '大包',     12, 988.00, '待清场'),
('K06', 'VIP大包',  20, 1888.00, '空闲');

INSERT INTO member (member_no, member_name, phone, level, balance) VALUES
('M001', '张伟', '13800001111', '普通', 856.00),
('M002', '李娜', '13800002222', '金卡', 1500.00),
('M003', '王芳', '13800003333', '银卡', 600.00),
('M004', '刘洋', '13800004444', '钻石', 5000.00);

INSERT INTO song (song_code, song_name, singer, language, duration_sec) VALUES
('S001', '海阔天空', 'Beyond',    '粤语', 320),
('S002', '七里香',   '周杰伦',     '国语', 299),
('S003', 'Hotel California', 'Eagles', '英文', 391),
('S004', '红',       '福山雅治',   '日文', 347),
('S005', '晴天',     '周杰伦',     '国语', 269);

INSERT INTO booking
(room_id, member_id, start_at, end_at, party_size, status, settlement_method, settlement_amount, settled_at)
VALUES
(2, 2, '2026-10-02 19:00:00', '2026-10-02 22:00:00', 6,  '已预订', NULL, NULL, NULL),
(3, 4, '2026-10-02 20:00:00', '2026-10-02 23:30:00', 12, '使用中', NULL, NULL, NULL),
(5, 1, '2026-10-03 18:00:00', '2026-10-03 21:00:00', 10, '已预订', NULL, NULL, NULL),
(1, 1, '2026-10-01 14:00:00', '2026-10-01 16:00:00', 2,  '已完成',
 '储值余额', 656.00, '2026-10-01 16:05:00');

INSERT INTO consumption (booking_id, item_name, amount, at) VALUES
(4, '欢唱套餐', 500.00, '2026-10-01 15:36:00'),
(4, '精酿啤酒', 88.00,  '2026-10-01 15:48:00'),
(4, '缤纷果盘', 68.00,  '2026-10-01 15:57:00');

-- 会员资金流水：当前 member.balance 必须等于该会员最后一条流水的 balance_after。
INSERT INTO member_transaction
(member_id, type, amount, balance_after, booking_id, remark, created_at) VALUES
(1, '初始储值', 1512.00, 1512.00, NULL, '建档初始储值 1512.00 元', '2026-10-01 13:00:00'),
(1, '消费扣款', -656.00, 856.00,  4, '预订#4 储值余额结账', '2026-10-01 16:05:00'),
(2, '初始储值', 1500.00, 1500.00, NULL, '建档初始储值 1500.00 元', '2026-09-20 10:00:00'),
(3, '初始储值', 600.00,  600.00,  NULL, '建档初始储值 600.00 元', '2026-09-18 11:20:00'),
(4, '初始储值', 5000.00, 5000.00, NULL, '建档初始储值 5000.00 元', '2026-09-10 15:30:00');

-- ---------------- 清场任务种子 ----------------
-- 覆盖闭环各形态：已放行（K06 历史可追溯）、待验收且设备异常未处置（K05）、
-- 待领取（K04）、整改中（K01，退回时保留了前一轮问题快照）。
INSERT INTO cleaning_task
(id, room_id, booking_id, status, cleaner_name, manager_name, hygiene_result, leftover_items,
 equipment_issue, has_equipment_issue, issue_resolved, issue_resolution, rework_note, accept_note,
 submit_round, created_at, claimed_at, submitted_at, finished_at) VALUES
(1, 6, NULL, '已放行', '王洁', '李倩', '合格', NULL, NULL, 0, 0, NULL, NULL, '验收合格，包厢恢复空闲可接待',
 1, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 3 HOUR,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 170 MINUTE,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 140 MINUTE,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 130 MINUTE),
(2, 5, NULL, '待验收', '王洁', NULL, '合格', '客人遗落充电宝一个（已交前台）', '2 号麦克风有电流杂音', 1, 0, NULL, NULL, NULL,
 1, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 MINUTE,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 45 MINUTE,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE, NULL),
(3, 4, NULL, '待领取', NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, NULL,
 0, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 MINUTE, NULL, NULL, NULL),
(4, 1, 4, '整改中', '陈芳', '李倩', '合格', NULL, NULL, 0, 0, NULL, '沙发缝有饮料渍未清理、点歌屏需擦拭，整改后重新提交', NULL,
 1, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 110 MINUTE,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 MINUTE, NULL);

INSERT INTO cleaning_record (task_id, action, operator_name, detail, created_at) VALUES
(1, '创建', '前台', '包厢 VIP大包 场次结束，生成待清场任务，验收放行前不可接待',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 3 HOUR),
(1, '领取', '王洁', '保洁领取任务，开始清场',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 170 MINUTE),
(1, '提交', '王洁', '第 1 轮清场提交：卫生合格；遗留物：无；设备异常：无',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 140 MINUTE),
(1, '验收放行', '李倩', '验收合格，包厢恢复空闲可接待',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY - INTERVAL 130 MINUTE),
(2, '创建', '前台', '包厢 大包 场次结束，生成待清场任务，验收放行前不可接待',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 MINUTE),
(2, '领取', '王洁', '保洁领取任务，开始清场',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 45 MINUTE),
(2, '提交', '王洁', '第 1 轮清场提交：卫生合格；遗留物：客人遗落充电宝一个（已交前台）；设备异常：2 号麦克风有电流杂音',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE),
(3, '创建', '前台', '包厢 豪华中包 场次结束，生成待清场任务，验收放行前不可接待',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 MINUTE),
(4, '创建', '前台', '包厢 迷你包 场次结束，生成待清场任务，验收放行前不可接待',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR),
(4, '领取', '陈芳', '保洁领取任务，开始清场',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 110 MINUTE),
(4, '提交', '陈芳', '第 1 轮清场提交：卫生合格；遗留物：无；设备异常：无',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 MINUTE),
(4, '退回整改', '李倩', '整改要求：沙发缝有饮料渍未清理、点歌屏需擦拭，整改后重新提交。前次提交（第 1 轮）：卫生合格；遗留物：无；设备异常：无',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 MINUTE);
