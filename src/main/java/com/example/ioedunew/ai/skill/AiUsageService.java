package com.example.ioedunew.ai.skill;

import com.example.ioedunew.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 每用户每日运行次数配额与 token 用量统计 */
@Service
public class AiUsageService {

    private final AiUsageDailyRepository repo;
    private final com.example.ioedunew.service.AiConfigService configService;
    private final com.example.ioedunew.tenant.TenantQuotaService quotaService;

    @Value("${ioedu.ai.daily-runs-per-user:50}")
    private int dailyRunsPerUser;

    public AiUsageService(AiUsageDailyRepository repo, com.example.ioedunew.service.AiConfigService configService,
                          com.example.ioedunew.tenant.TenantQuotaService quotaService) {
        this.repo = repo;
        this.configService = configService;
        this.quotaService = quotaService;
    }

    public void checkQuota(Long userId) {
        if (dailyRunsPerUser > 0) {
            int used = repo.findByUserIdAndDay(userId, LocalDate.now()).map(AiUsageDaily::getRuns).orElse(0);
            if (used >= dailyRunsPerUser) {
                throw new BusinessException("今日 AI 使用次数已达上限(" + dailyRunsPerUser + " 次),请明天再来");
            }
        }
        // 站点自己设的预算优先;没设则用平台给该站点的套餐额度
        long budget = configService.monthlyTokenBudget();
        if (budget <= 0) {
            budget = quotaService.tenantAiMonthlyTokens();
        }
        if (budget > 0 && monthTokens() >= budget) {
            throw new BusinessException("本站本月 AI 额度已用完,请联系管理员调整预算或下月再用");
        }
    }

    /** 本自然月全站已用 Token(输入+输出) */
    public long monthTokens() {
        LocalDate today = LocalDate.now();
        long sum = 0;
        for (AiUsageDaily r : repo.findByDayBetween(today.withDayOfMonth(1), today)) {
            sum += r.getPromptTokens() + r.getCompletionTokens();
        }
        return sum;
    }

    @Transactional
    public void record(Long userId, int promptTokens, int completionTokens) {
        AiUsageDaily u = repo.findByUserIdAndDay(userId, LocalDate.now()).orElseGet(() -> {
            AiUsageDaily n = new AiUsageDaily();
            n.setUserId(userId);
            n.setDay(LocalDate.now());
            return n;
        });
        u.setRuns(u.getRuns() + 1);
        u.setPromptTokens(u.getPromptTokens() + Math.max(0, promptTokens));
        u.setCompletionTokens(u.getCompletionTokens() + Math.max(0, completionTokens));
        repo.save(u);
    }

    /** 最近 N 天按日汇总 */
    public Map<String, Object> summary(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(1, days) - 1);
        List<AiUsageDaily> rows = repo.findByDayBetween(from, to);
        Map<String, int[]> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d.toString(), new int[3]);
        }
        int runs = 0;
        int pt = 0;
        int ct = 0;
        java.util.Set<Long> users = new java.util.HashSet<>();
        for (AiUsageDaily r : rows) {
            int[] acc = byDay.get(r.getDay().toString());
            if (acc != null) {
                acc[0] += r.getRuns();
                acc[1] += r.getPromptTokens();
                acc[2] += r.getCompletionTokens();
            }
            runs += r.getRuns();
            pt += r.getPromptTokens();
            ct += r.getCompletionTokens();
            users.add(r.getUserId());
        }
        java.util.List<Map<String, Object>> series = new java.util.ArrayList<>();
        for (Map.Entry<String, int[]> e : byDay.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", e.getKey());
            m.put("runs", e.getValue()[0]);
            m.put("promptTokens", e.getValue()[1]);
            m.put("completionTokens", e.getValue()[2]);
            series.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("runs", runs);
        out.put("promptTokens", pt);
        out.put("completionTokens", ct);
        out.put("activeUsers", users.size());
        out.put("dailyRunsPerUser", dailyRunsPerUser);
        out.put("monthTokens", monthTokens());
        long budget = configService.monthlyTokenBudget();
        out.put("monthlyTokenBudget", budget > 0 ? budget : quotaService.tenantAiMonthlyTokens());
        out.put("budgetSource", budget > 0 ? "SITE" : quotaService.tenantAiMonthlyTokens() > 0 ? "PLAN" : "NONE");
        out.put("series", series);
        return out;
    }
}
