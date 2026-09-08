package com.example.ioedunew.init;

import com.example.ioedunew.service.BorrowReminderService;
import com.example.ioedunew.tenant.Tenant;
import com.example.ioedunew.tenant.TenantContext;
import com.example.ioedunew.tenant.TenantRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 借阅到期提醒定时任务:启动 30 秒后先跑一次,之后每小时整点扫描。
 * 逐个活动租户绑定上下文后调用 BorrowReminderService,一个租户出错不影响其他租户。
 */
@Slf4j
@Component
public class ReminderScheduler {

    private final TenantRegistry tenantRegistry;
    private final BorrowReminderService reminderService;

    public ReminderScheduler(TenantRegistry tenantRegistry, BorrowReminderService reminderService) {
        this.tenantRegistry = tenantRegistry;
        this.reminderService = reminderService;
    }

    @Scheduled(initialDelay = 30_000, fixedRate = 3_600_000)
    public void remindDueBorrows() {
        for (Tenant tenant : tenantRegistry.active()) {
            try {
                int sent = TenantContext.runAs(tenant.getCode(), reminderService::remindDueBorrows);
                if (sent > 0) {
                    log.info("借阅到期提醒:租户 {} 本轮发送 {} 条", tenant.getCode(), sent);
                }
            } catch (Exception e) {
                log.warn("借阅到期提醒:租户 {} 执行失败: {}", tenant.getCode(), e.getMessage());
            }
        }
    }
}
