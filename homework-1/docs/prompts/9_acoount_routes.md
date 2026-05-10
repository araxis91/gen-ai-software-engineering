Always use 1_project_architecture.md file as main project documentation

Ось мої файли:
Моделі: src/models/transaction.py
Storage: src/storage/transaction_store.py

Поточна задача: створити файл src/routes/accounts.py

Реалізуй FastAPI router з ендпоінтами:

1. GET /accounts/{account_id}/balance
   - Повертає баланс рахунку зі статусом 200
   - Якщо рахунок не знайдено (0 транзакцій) — 404

2. GET /accounts/{account_id}/summary
   - Повертає: total_deposits, total_withdrawals, transaction_count, last_transaction_date
   - Якщо рахунок не знайдено — 404

Поверни лише код файлу src/routes/accounts.py
