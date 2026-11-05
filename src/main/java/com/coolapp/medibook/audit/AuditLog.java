package com.coolapp.medibook.audit;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(nullable = false)
    private String action;
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    @Column(name = "entity_id")
    private String entityId;
    private String details;
    @Column(name = "ip_address")
    private String ipAddress;
}
