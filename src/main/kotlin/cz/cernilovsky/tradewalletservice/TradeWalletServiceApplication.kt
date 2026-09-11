package cz.cernilovsky.tradewalletservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class TradeWalletServiceApplication

fun main(args: Array<String>) {
    runApplication<TradeWalletServiceApplication>(*args)
}
