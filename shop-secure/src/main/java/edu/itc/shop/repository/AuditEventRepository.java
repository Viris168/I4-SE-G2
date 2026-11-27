package edu.itc.shop.repository;

import edu.itc.shop.domain.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByOrderByIdDesc();

    List<AuditEvent> findTop200ByUsernameOrderByIdDesc(String username);
}
