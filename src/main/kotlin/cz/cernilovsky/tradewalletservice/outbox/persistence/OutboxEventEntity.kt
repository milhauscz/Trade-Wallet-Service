package cz.cernilovsky.tradewalletservice.outbox.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 128)
    val topic: String,
    @Column(name = "aggregate_id", nullable = false, length = 64)
    val aggregateId: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
) {
    @PrePersist
    fun onCreate() {
        if (createdAt == Instant.EPOCH) {
            createdAt = Instant.now()
        }
    }
}
