package cz.cernilovsky.tradewalletservice.wallet.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface WalletRepository : JpaRepository<WalletEntity, UUID> {
    fun findByUserId(userId: String): WalletEntity?

    // SELECT ... FOR UPDATE. Only this query locks; findByUserId does not.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.userId = :userId")
    fun findByUserIdForUpdate(userId: String): WalletEntity?
}
