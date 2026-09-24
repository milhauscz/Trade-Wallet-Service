package cz.cernilovsky.tradewalletservice.common.exception

class NotImplementedYetException(feature: String) :
    RuntimeException("Not implemented: $feature")

class ResourceNotFoundException(resource: String, id: Any) :
    RuntimeException("$resource '$id' was not found")

class InsufficientFundsException(available: String, requested: String) :
    RuntimeException("Insufficient funds: available=$available, requested=$requested")

class BadRequestException(message: String) : RuntimeException(message)
