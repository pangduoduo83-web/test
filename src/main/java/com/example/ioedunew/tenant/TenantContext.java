package com.example.ioedunew.tenant;

import java.util.function.Supplier;

/**
 * 当前线程绑定的租户编码。
 * Web 请求由 TenantFilter 按 Host 解析后绑定;后台任务/开通流程必须通过 runAs 显式指定,
 * 未绑定时 Hibernate 拒绝开启会话(见 TenantIdentifierResolver),避免误写到其他客户的库。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String code = CURRENT.get();
        if (code == null) {
            throw new IllegalStateException("当前线程未绑定租户,请通过 TenantContext.runAs 执行");
        }
        return code;
    }

    public static void set(String code) {
        if (code == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(code);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 在指定租户上下文中执行并返回结果,结束后恢复之前的绑定 */
    public static <T> T runAs(String code, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(code);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(String code, Runnable action) {
        runAs(code, () -> {
            action.run();
            return null;
        });
    }
}
