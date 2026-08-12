package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 借阅申请:核心状态机。
 * 状态流转:PENDING → APPROVED / REJECTED / CANCELLED;
 * APPROVED → RETURN_REQUESTED → RETURNED。
 * 库存副作用:批准时扣减 availableCount,归还验收/拒绝后回补,由 BorrowService 统一控制,禁止绕过。
 */
@Data
@Entity
@Table(name = "borrow_requests")
public class BorrowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请编号,形如 BR20260724XXXX */
    @Column(nullable = false, unique = true, length = 30)
    private String requestNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long equipmentId;

    /** 冗余显示字段,避免列表页多次关联查询 */
    @Column(length = 100)
    private String equipmentName;

    @Column(length = 50)
    private String userName;

    @Column(nullable = false)
    private Integer quantity = 1;

    /** 课程实验 / 竞赛准备 / 科研研究 / 毕业设计 */
    @Column(length = 30)
    private String purpose;

    @Column(length = 100)
    private String projectName;

    private LocalDate startDate;

    @Column(nullable = false)
    private Integer durationDays = 14;

    @Column(length = 500)
    private String remark;

    /** PENDING / APPROVED / REJECTED / RETURN_REQUESTED / RETURNED / CANCELLED */
    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(length = 50)
    private String approverName;

    @Column(length = 200)
    private String rejectReason;

    /** 到期提醒是否已发送,由定时任务置位,避免重复提醒 */
    @Column(nullable = false)
    private Boolean reminderSent = false;

    /** 是否已续借(每单仅可续借一次,续借后重置到期提醒) */
    @Column(nullable = false)
    private Boolean renewed = false;

    @Column(nullable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    private LocalDateTime approvedAt;

    private LocalDateTime returnedAt;
}
