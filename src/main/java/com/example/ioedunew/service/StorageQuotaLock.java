package com.example.ioedunew.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.function.Supplier;

/** A lock row in each tenant database serializes quota reservations across backend replicas. */
@Component
public class StorageQuotaLock {
    @PersistenceContext private EntityManager em;
    private final TransactionTemplate transaction;
    public StorageQuotaLock(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public <T> T run(Supplier<T> action) {
        return transaction.execute(status -> {
            em.createNativeQuery("SELECT id FROM storage_quota_lock WHERE id=1 FOR UPDATE").getSingleResult();
            return action.get();
        });
    }
}
