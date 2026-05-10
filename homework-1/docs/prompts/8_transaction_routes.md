Always use 1_project_architecture.md file as main project documentation

Моделі: src/models/transaction.py
Storage: src/storage/transaction_store.py

Поточна задача: створити файл src/routes/transactions.py

Реалізуй FastAPI router з ендпоінтами:

1. POST /transactions
   - Приймає TransactionCreate
   - Повертає Transaction зі статусом 201
   - При помилці валідації — 400 з форматом {"error": "Validation failed", "details": [...]}

2. GET /transactions
   - Query параметри: accountId, type, from (дата), to (дата) — всі опціональні
   - Повертає list[Transaction] зі статусом 200

3. GET /transactions/{transaction_id}
   - Повертає Transaction зі статусом 200
   - Якщо не знайдено — 404 з {"error": "Transaction not found", "details": []}

Використовуй Depends(get_transaction_store) для доступу до storage.
Поверни лише код файлу src/routes/transactions.py
