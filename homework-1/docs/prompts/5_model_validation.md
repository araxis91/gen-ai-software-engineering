Always use 1_project_architecture.md file as main project documentation

Поточна задача: додати валідацію до моделей у src/models/transaction.py

Додай Pydantic @field_validator для:

1. amount:
   - Має бути позитивним числом (> 0)
   - Максимум 2 знаки після коми
   - Повідомлення: "Amount must be a positive number with max 2 decimal places"

2. fromAccount і toAccount:
   - Формат: ACC- і рівно 5 алфавітно-цифрових символів (великі літери або цифри)
   - Regex: ^ACC-[A-Z0-9]{5}$
   - Повідомлення: "Account number must follow format ACC-XXXXX"

3. currency:
   - Список дозволених кодів: USD, EUR, GBP, JPY, UAH, CHF, CAD, AUD, CNY, PLN
   - Повідомлення: "Invalid currency code. Supported: USD, EUR, GBP, JPY, UAH, CHF, CAD, AUD, CNY, PLN"

Поверни повний оновлений файл src/models/transaction.py
