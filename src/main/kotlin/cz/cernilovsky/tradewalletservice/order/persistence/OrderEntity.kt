package cz.cernilovsky.tradewalletservice.order.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(nullable = false, length = 32)
    var symbol: String,
    @Column(nullable = false, precision = 19, scale = 4)
    var price: BigDecimal,
    @Column(nullable = false, precision = 19, scale = 4)
    var quantity: BigDecimal,
    @Column(name = "stop_loss", precision = 19, scale = 4)
    var stopLoss: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.PENDING,
    @Column(name = "idempotency_key", nullable = false, length = 128)
    val idempotencyKey: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    /**
     * Column exists in Flyway (`orders.version`). Do **not** treat this as optimistic locking
     * until you add `@Version` in Phase 2 — a plain field is just another column.
     */
    @Column(nullable = false)
    var version: Long = 0,
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /**
     * TODO(learning) Phase 2 — Optimistic locking:
     *
     * 1. Import `jakarta.persistence.Version`.
     * 2. Annotate `version` with `@Version` (keep `@Column(nullable = false)`).
     * 3. Hibernate will then:
     *    - `INSERT` with version 0
     *    - `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`
     *    - throw `OptimisticLockException` / `ObjectOptimisticLockingFailureException`
     *      when the WHERE matches 0 rows (someone else already updated).
     * 4. Do not increment `version` yourself in service code.
     */
}
