package me.rerere.rikkahub.service

/** 对会话或消息的操作不合法时抛出，调用方可以按 [ApiException] 统一处理。 */
sealed class ApiException(override val message: String) : RuntimeException(message)

class BadRequestException(message: String) : ApiException(message)
class NotFoundException(message: String) : ApiException(message)
