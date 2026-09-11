package cz.cernilovsky.tradewalletservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TradeWalletServiceApplication

fun main(args: Array<String>) {
	runApplication<TradeWalletServiceApplication>(*args)
}
