package edu.itc.shop.web.dto;

import edu.itc.shop.domain.AuditEvent;
import edu.itc.shop.domain.AuditType;
import java.time.Instant;

public record AuditEventResponse(Long id, Instant at, AuditType type, String username,
                                 String ip, String requestId, String detail) {

    public static AuditEventResponse of(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getAt(), e.getType(), e.getUsername(),
                e.getIp(), e.getRequestId(), e.getDetail());
    }
}
