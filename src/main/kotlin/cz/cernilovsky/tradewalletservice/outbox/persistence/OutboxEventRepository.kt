package cz.cernilovsky.tradewalletservice.outbox.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findTop50ByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEventEntity>
}
