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

    /** Старая цена опциональна: пусто — ок, иначе валидная цена. */
    fun validateOptionalPrice(input: String, currencyCode: String): String? = when {
        input.isBlank() -> null
        PriceParser.parse(input, currencyCode) == null -> "Некорректная цена"
        else -> null
    }

    const val SKU_MAX = 64

    /** SKU/штрихкод: опционально, ≤64 (зеркало схемы сервера). */
    fun validateSku(input: String): String? = when {
        input.length > SKU_MAX -> "Не длиннее $SKU_MAX символов"
        else -> null
    }

    const val CATEGORY_MAX = 80

    fun validateCategory(input: String): String? = when {
        input.length > CATEGORY_MAX -> "Не длиннее $CATEGORY_MAX символов"
        else -> null
    }

    fun validateDescription(description: String): String? = when {
        description.length > DESCRIPTION_MAX -> "Описание не длиннее $DESCRIPTION_MAX символов"
        else -> null
    }

    const val IMAGES_MAX = 10

    /** Остаток (stock) варианта: целое ≥ 0 (зеркало z.number().int().min(0)). */
    fun validateStock(input: String): String? = when {
        input.isBlank() -> "Укажите остаток"
        parseStock(input) == null -> "Остаток — целое число ≥ 0"
        else -> null
    }

    fun parseStock(input: String): Int? =
        input.trim().toIntOrNull()?.takeIf { it >= 0 }
}
