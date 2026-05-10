Always use 1_project_architecture.md file as main project documentation

Мої моделі у файлі: src/models/transaction.py

Поточна задача: створити файл src/storage/transaction_store.py

Створи клас TransactionStore з методами:

1. add(transaction: Transaction) -> Transaction
   Зберігає транзакцію, повертає її

2. get_by_id(transaction_id: str) -> Transaction | None
   Повертає транзакцію або None якщо не знайдено

3. get_all(account_id: str | None, type: str | None, from_date: str | None, to_date: str | None) -> list[Transaction]
   Повертає список з фільтрацією (всі параметри опціональні)

4. get_balance(account_id: str) -> dict
   Рахує баланс: deposits мінус withdrawals для рахунку account_id
   Повертає: {"accountId": str, "balance": float, "currency": str}

5. get_summary(account_id: str) -> dict
   Повертає: total_deposits, total_withdrawals, transaction_count, last_transaction_date

Клас має бути синглтоном через get_transaction_store() функцію для FastAPI Depends.
Зберігання — звичайний dict: {id: Transaction}

Поверни лише код файлу src/storage/transaction_store.py
