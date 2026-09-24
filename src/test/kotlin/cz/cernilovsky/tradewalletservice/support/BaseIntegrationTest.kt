package cz.cernilovsky.tradewalletservice.support

import cz.cernilovsky.tradewalletservice.common.web.RequestHeaderConsts
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class BaseIntegrationTest {
    @Autowired
    protected lateinit var walletRepository: WalletRepository

    @BeforeEach
    fun resetAliceWalletToSeed() {
        val wallet = walletRepository.findByUserId("alice")
            ?: throw IllegalStateException("alice's wallet not found")
        wallet.balance = BigDecimal("10000")
        wallet.reservedAmount = BigDecimal.ZERO
        walletRepository.saveAndFlush(wallet)
    }

    protected fun MockHttpServletRequestDsl.configureHeaders(
        authorization: String,
        contentTypeValue: MediaType = MediaType.APPLICATION_JSON,
        idempotencyKey: String = UUID.randomUUID().toString()
    ) {
        header(RequestHeaderConsts.AUTHORIZATION, authorization)
        header(RequestHeaderConsts.X_IDEMPOTENCY_KEY, idempotencyKey)
        contentType = contentTypeValue
    }
}
