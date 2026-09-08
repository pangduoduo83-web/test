package com.example.ioedunew.service;

import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.repository.BorrowRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 借阅到期提醒(单个租户内的一轮扫描,事务边界在此,由 ReminderScheduler 逐租户调用)。
 * 规则:借用中(APPROVED)的记录,距到期日不足 3 天或已逾期且未提醒过时,
 * 发送站内通知并置位 reminderSent,保证每条记录只提醒一次。
 */
@Service
public class BorrowReminderService {

    private final BorrowRequestRepository borrowRepository;
    private final NotificationService notificationService;

    public BorrowReminderService(BorrowRequestRepository borrowRepository,
                                 NotificationService notificationService) {
        this.borrowRepository = borrowRepository;
        this.notificationService = notificationService;
    }

    /** 返回本轮发送的提醒条数 */
    @Transactional
    public int remindDueBorrows() {
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
        return sent;
    }
}
