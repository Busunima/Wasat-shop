# ProGuard/R8 правила (release). Дополняются по мере добавления зависимостей.
# Firebase/Firestore модели — сохраняем поля для (де)сериализации.
-keepclassmembers class com.wasat.shop.** {
    <fields>;
}

# Stripe SDK поставляет собственные consumer-rules; здесь — место для проектных исключений.
