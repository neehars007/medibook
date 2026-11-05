package com.coolapp.medibook.audit;

import com.coolapp.medibook.common.security.AuthUser;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.common.web.ClientIp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final AccessLogRepository accessRepo;

    /** Writes an audit entry in the caller's transaction. Actor is null for system jobs. */
    public void log(String action, String entityType, Object entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorId(CurrentUser.optional().map(AuthUser::id).orElse(null));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : entityId.toString());
        log.setDetails(details == null ? null : truncate(details, 2000));
        log.setIpAddress(ClientIp.current());
        auditRepo.save(log);
    }

    /** Records a view of clinical data. */
    public void access(UUID patientId, AccessResource resource, Object resourceId) {
        AccessLog log = new AccessLog();
        log.setActorId(CurrentUser.get().id());
        log.setPatientId(patientId);
        log.setResource(resource);
        log.setResourceId(resourceId == null ? null : resourceId.toString());
        accessRepo.save(log);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
