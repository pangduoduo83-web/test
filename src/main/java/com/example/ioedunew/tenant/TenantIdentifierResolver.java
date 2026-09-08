package com.example.ioedunew.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * 告诉 Hibernate 当前会话属于哪个租户。
 * 未绑定租户时返回哨兵标识 UNBOUND:Spring Data 在启动阶段会为读取元模型开一个会话,这一步不能失败;
 * 但带哨兵标识的会话一旦真正申请数据库连接,会在 SchemaMultiTenantConnectionProvider 处被拒绝,
 * 因此忘记绑定租户的后台代码只会报错,不会默默落到某个客户的库里。
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    public static final String UNBOUND = "__unbound__";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String code = TenantContext.get();
        return code == null ? UNBOUND : code;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
