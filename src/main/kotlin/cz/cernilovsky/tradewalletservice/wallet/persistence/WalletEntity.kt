package cz.cernilovsky.tradewalletservice.wallet.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "wallets")
class WalletEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,
    @Column(nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal,
    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    var reservedAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 3)
    val currency: String = "USD",
) {
    fun available(): BigDecimal = balance.subtract(reservedAmount)
}
