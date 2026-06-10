package com.wasat.shop.feature.admin

import com.wasat.shop.core.util.PriceParser

/**
 * Валидация формы товара — зеркало zod-схемы сервера (server/src/schemas/product.ts):
 * name 1..200, description ≤5000, цена — неотрицательные минорные единицы.
 * Возвращают null при успехе, иначе текст ошибки. Pure JVM.
 */
object ProductFormValidation {
    const val NAME_MAX = 200
    const val DESCRIPTION_MAX = 5000

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Укажите название товара"
        name.length > NAME_MAX -> "Название не длиннее $NAME_MAX символов"
        else -> null
    }

    fun validatePrice(input: String, currencyCode: String): String? = when {
        input.isBlank() -> "Укажите цену"
        PriceParser.parse(input, currencyCode) == null -> "Некорректная цена"
        else -> null
    }

    fun validateDescription(description: String): String? = when {
        description.length > DESCRIPTION_MAX -> "Описание не длиннее $DESCRIPTION_MAX символов"
        else -> null
    }
}
