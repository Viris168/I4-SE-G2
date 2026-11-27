package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the security_audit table. Append-only: there are no setters. */
@Entity
@Table(name = "security_audit")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditType type;

    @Column(length = 100)
    private String username;

    @Column(length = 64)
    private String ip;

    @Column(length = 64)
    private String requestId;

    @Column(length = 500)
    private String detail;

    protected AuditEvent() { }   // for JPA

    public AuditEvent(Instant at, AuditType type, String username, String ip, String requestId, String detail) {
        this.at = at;
        this.type = type;
        this.username = username;
        this.ip = ip;
        this.requestId = requestId;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public AuditType getType() { return type; }
    public String getUsername() { return username; }
    public String getIp() { return ip; }
    public String getRequestId() { return requestId; }
    public String getDetail() { return detail; }
}
