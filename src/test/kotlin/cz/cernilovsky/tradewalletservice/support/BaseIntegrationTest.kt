package cz.cernilovsky.tradewalletservice.support

import cz.cernilovsky.tradewalletservice.common.web.RequestHeaderConsts
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class BaseIntegrationTest {
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
