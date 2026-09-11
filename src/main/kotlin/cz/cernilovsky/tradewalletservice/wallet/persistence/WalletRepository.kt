package cz.cernilovsky.tradewalletservice.wallet.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletRepository : JpaRepository<WalletEntity, UUID> {
    fun findByUserId(userId: String): WalletEntity?

    /**
     * TODO(learning) Phase 1 — Pessimistic locking:
     *
     * Add a method that loads the wallet for a user with
     * `jakarta.persistence.LockModeType.PESSIMISTIC_WRITE`.
     *
     * Suggested signature:
     * ```
     * @Lock(LockModeType.PESSIMISTIC_WRITE)
     * @Query("SELECT w FROM WalletEntity w WHERE w.userId = :userId")
     * fun findByUserIdForUpdate(userId: String): WalletEntity?
     * ```
     *
     * Why `@Query` + `@Lock` rather than `findByUserId`?
     * Spring Data only applies `@Lock` to the annotated query method. Reusing the unlocked
     * finder from a `@Transactional` service does **not** emit `SELECT ... FOR UPDATE`.
     *
     * This method must be called from inside an open transaction (`WalletService.reserve`).
     */
}
