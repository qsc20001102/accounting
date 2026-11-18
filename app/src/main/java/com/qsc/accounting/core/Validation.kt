package com.qsc.accounting.core

/** 可直接展示给用户的业务校验错误，与插件里的 ValidationError 一一对应。 */
class ValidationException(message: String) : Exception(message)

fun fail(message: String): Nothing = throw ValidationException(message)

fun <T> T?.orFail(message: String): T = this ?: fail(message)
