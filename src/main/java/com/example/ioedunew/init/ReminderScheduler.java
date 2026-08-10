package com.example.ioedunew.init;

import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 借阅到期提醒定时任务。
 * 规则:借用中(APPROVED)的记录,距到期日不足 3 天或已逾期且未提醒过时,
 * 发送站内通知并置位 reminderSent,保证每条记录只提醒一次。
 * 触发:启动 30 秒后先跑一次,之后每小时整点扫描。
 */
@Slf4j
@Component
public class ReminderScheduler {

    private final BorrowRequestRepository borrowRepository;
    private final NotificationService notificationService;

    public ReminderScheduler(BorrowRequestRepository borrowRepository,
                             NotificationService notificationService) {
        this.borrowRepository = borrowRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(initialDelay = 30_000, fixedRate = 3_600_000)
    @Transactional
    public void remindDueBorrows() {
        List<BorrowRequest> active = borrowRepository.findByStatusOrderByAppliedAtDesc("APPROVED");
        LocalDate today = LocalDate.now();
        int sent = 0;
        for (BorrowRequest br : active) {
            if (Boolean.TRUE.equals(br.getReminderSent()) || br.getStartDate() == null) {
                continue;
            }
            LocalDate dueDate = br.getStartDate().plusDays(br.getDurationDays());
            long daysLeft = ChronoUnit.DAYS.between(today, dueDate);
            if (daysLeft > 3) {
                continue;
            }
            String message = daysLeft >= 0
                    ? "《" + br.getEquipmentName() + "》将于 " + dueDate + " 到期(剩余 " + daysLeft + " 天),请按时归还。"
                    : "《" + br.getEquipmentName() + "》已逾期 " + (-daysLeft) + " 天,请尽快归还,逾期将影响信用评分!";
            notificationService.create(br.getUserId(), "borrow", "归还提醒", message);
            br.setReminderSent(true);
            borrowRepository.save(br);
            sent++;
        }
        if (sent > 0) {
            log.info("借阅到期提醒:本轮发送 {} 条", sent);
        }
    }
}
