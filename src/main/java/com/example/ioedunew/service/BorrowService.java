package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.BorrowDtos;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 借阅服务:借阅申请状态机与库存控制中心。
 *
 * 状态流转与库存副作用(禁止在服务外直接改动这两者):
 * - apply:校验库存后创建 PENDING,不占库存;
 * - approve:PENDING → APPROVED,扣减 availableCount、累加 borrowCount、通知申请人、经验 +5;
 * - reject:PENDING → REJECTED,记录原因并通知;
 * - cancel:申请人本人将 PENDING → CANCELLED;
 * - requestReturn:申请人本人将 APPROVED → RETURN_REQUESTED;
 * - confirmReturn:管理员将 RETURN_REQUESTED/APPROVED → RETURNED,回补库存并通知。
 * 失败语义:状态不匹配一律抛 BusinessException,不做静默兜底。
 */
@Service
public class BorrowService {

    private final BorrowRequestRepository borrowRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public BorrowService(BorrowRequestRepository borrowRepository,
                         EquipmentRepository equipmentRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.borrowRepository = borrowRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public BorrowRequest apply(Long userId, BorrowDtos.ApplyRequest req) {
        Equipment eq = equipmentRepository.findById(req.getEquipmentId())
                .orElseThrow(() -> new BusinessException(404, "设备不存在"));
        if (!"AVAILABLE".equals(eq.getStatus())) {
            throw new BusinessException("该设备正在维护中,暂停借阅");
        }
        if (eq.getAvailableCount() < req.getQuantity()) {
            throw new BusinessException("库存不足,当前可借 " + eq.getAvailableCount() + " 件");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        BorrowRequest br = new BorrowRequest();
        br.setRequestNo(generateRequestNo());
        br.setUserId(userId);
        br.setUserName(user.getName());
        br.setEquipmentId(eq.getId());
        br.setEquipmentName(eq.getName());
        br.setQuantity(req.getQuantity());
        br.setPurpose(req.getPurpose());
        br.setProjectName(req.getProjectName());
        br.setStartDate(req.getStartDate());
        br.setDurationDays(req.getDurationDays());
        br.setRemark(req.getRemark());
        borrowRepository.save(br);
        return br;
    }

    public List<BorrowRequest> myRequests(Long userId, String status) {
        List<BorrowRequest> items = borrowRepository.findByUserIdOrderByAppliedAtDesc(userId);
        if (status == null || status.isEmpty() || "ALL".equals(status)) {
            return items;
        }
        return items.stream().filter(b -> status.equals(b.getStatus())).collect(Collectors.toList());
    }

    public Map<String, Object> myStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", borrowRepository.countByUserId(userId));
        stats.put("borrowing", borrowRepository.countByUserIdAndStatus(userId, "APPROVED")
                + borrowRepository.countByUserIdAndStatus(userId, "RETURN_REQUESTED"));
        stats.put("pending", borrowRepository.countByUserIdAndStatus(userId, "PENDING"));
        return stats;
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        BorrowRequest br = getOwned(userId, requestId);
        if (!"PENDING".equals(br.getStatus())) {
            throw new BusinessException("仅审批中的申请可以撤销");
        }
        br.setStatus("CANCELLED");
        borrowRepository.save(br);
    }

    @Transactional
    public void requestReturn(Long userId, Long requestId) {
        BorrowRequest br = getOwned(userId, requestId);
        if (!"APPROVED".equals(br.getStatus())) {
            throw new BusinessException("仅借用中的记录可以申请归还");
        }
        br.setStatus("RETURN_REQUESTED");
        borrowRepository.save(br);
    }

    // ---------- 管理端 ----------

    public List<BorrowRequest> listAll(String status) {
        if (status == null || status.isEmpty() || "ALL".equals(status)) {
            return borrowRepository.findAllByOrderByAppliedAtDesc();
        }
        return borrowRepository.findByStatusOrderByAppliedAtDesc(status);
    }

    @Transactional
    public BorrowRequest decide(Long requestId, BorrowDtos.DecisionRequest req, String approverName) {
        BorrowRequest br = borrowRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (!"PENDING".equals(br.getStatus())) {
            throw new BusinessException("该申请已处理过");
        }
        if ("approve".equals(req.getAction())) {
            Equipment eq = equipmentRepository.findById(br.getEquipmentId())
                    .orElseThrow(() -> new BusinessException(404, "设备不存在"));
            if (eq.getAvailableCount() < br.getQuantity()) {
                throw new BusinessException("库存不足,无法批准(当前可借 " + eq.getAvailableCount() + " 件)");
            }
            eq.setAvailableCount(eq.getAvailableCount() - br.getQuantity());
            eq.setBorrowCount(eq.getBorrowCount() + br.getQuantity());
            equipmentRepository.save(eq);

            br.setStatus("APPROVED");
            br.setApproverName(approverName);
            br.setApprovedAt(LocalDateTime.now());
            notificationService.create(br.getUserId(), "borrow", "设备借阅申请已通过",
                    "《" + br.getEquipmentName() + "》已批准,请到 " + eq.getLocation() + " 领取,借用期 "
                            + br.getDurationDays() + " 天。");
            userRepository.findById(br.getUserId()).ifPresent(u -> {
                u.setExp(u.getExp() + 5);
                userRepository.save(u);
            });
        } else if ("reject".equals(req.getAction())) {
            if (req.getReason() == null || req.getReason().trim().isEmpty()) {
                throw new BusinessException("请填写拒绝原因");
            }
            br.setStatus("REJECTED");
            br.setApproverName(approverName);
            br.setRejectReason(req.getReason());
            br.setApprovedAt(LocalDateTime.now());
            notificationService.create(br.getUserId(), "borrow", "设备借阅申请被拒绝",
                    "《" + br.getEquipmentName() + "》申请未通过:" + req.getReason());
        } else {
            throw new BusinessException("未知决策动作:" + req.getAction());
        }
        return borrowRepository.save(br);
    }

    @Transactional
    public BorrowRequest confirmReturn(Long requestId, String approverName) {
        BorrowRequest br = borrowRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (!"RETURN_REQUESTED".equals(br.getStatus()) && !"APPROVED".equals(br.getStatus())) {
            throw new BusinessException("该记录不在借用中,无法归还验收");
        }
        Equipment eq = equipmentRepository.findById(br.getEquipmentId())
                .orElseThrow(() -> new BusinessException(404, "设备不存在"));
        eq.setAvailableCount(Math.min(eq.getTotalCount(), eq.getAvailableCount() + br.getQuantity()));
        equipmentRepository.save(eq);

        br.setStatus("RETURNED");
        br.setApproverName(approverName);
        br.setReturnedAt(LocalDateTime.now());
        notificationService.create(br.getUserId(), "borrow", "归还验收完成",
                "《" + br.getEquipmentName() + "》已完成归还验收,感谢按时归还。");
        return borrowRepository.save(br);
    }

    private BorrowRequest getOwned(Long userId, Long requestId) {
        BorrowRequest br = borrowRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (!br.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的申请");
        }
        return br;
    }

    private String generateRequestNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = borrowRepository.count() + 1;
        return "BR" + date + String.format("%04d", seq);
    }
}
