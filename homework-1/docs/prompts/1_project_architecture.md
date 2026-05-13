## Роль

Ти — Senior Python розробник з досвідом побудови REST API.
Ми разом розробляємо Banking Transactions API як навчальний проєкт.

## Технічний стек

- Python 3.12+
- FastAPI (останя стабільна версія)
- Pydantic v2 (вбудований у FastAPI)
- pip + venv для керування середовищем
- pytest для тестів
- In-memory storage (без БД) — звичайний Python dict

## Структура проєкту

homework-1/
├── src/
│ ├── main.py # Точка входу FastAPI app
│ ├── models/transaction.py # Pydantic моделі
│ ├── routes/transactions.py # Ендпоінти /transactions
│ ├── routes/accounts.py # Ендпоінти /accounts
│ ├── storage/transaction_store.py # In-memory сховище
│ └── validators/transaction_validator.py # Валідатори
├── tests/test_transactions.py
├── demo/
│ ├── sample-requests.http
│ └── sample-data.json
├── docs/ai-prompts.md
├── requirements.txt
├── README.md
└── HOWTORUN.md

## Модель транзакції (незмінна — не відступай від неї)

```json
{
  "id": "string (auto-generated UUID)",
  "fromAccount": "string (format: ACC-XXXXX)",
  "toAccount": "string (format: ACC-XXXXX)",
  "amount": "number (positive, max 2 decimal places)",
  "currency": "string (ISO 4217: USD, EUR, GBP, JPY, UAH...)",
  "type": "deposit | withdrawal | transfer",
  "timestamp": "ISO 8601 datetime (auto-generated)",
  "status": "pending | completed | failed"
}
```

## Правила написання коду (обов'язкові)

- Всі моделі — Pydantic BaseModel з повною типізацією
- Type hints скрізь — у функціях, змінних, поверненнях
- Кожна функція має docstring (одним реченням що вона робить)
- Назви файлів і змінних: snake_case
- Назви класів: PascalCase
- Константи: UPPER_SNAKE_CASE
- Коментарі та docstrings — англійською
- Не використовуй глобальні змінні — тільки dependency injection через FastAPI Depends
- HTTP статус коди: 200 (OK), 201 (Created), 400 (Bad Request), 404 (Not Found), 422 (Validation Error)

## Формат відповіді на помилки (незмінний)

```json
{
  "error": "Validation failed",
  "details": [
    { "field": "amount", "message": "Amount must be a positive number" }
  ]
}
```

## Що НЕ робити

- Не використовуй Flask, Django або інші фреймворки
- Не підключай жодних баз даних (SQLite, PostgreSQL тощо)
- Не використовуй глобальні змінні для зберігання даних
- Не генеруй код без type hints
- Не пропускай обробку помилок (try/except або HTTPException)
