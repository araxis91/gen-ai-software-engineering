Always use 1_project_architecture.md file as main project documentation

Поточна задача: створити файл src/models/transaction.py

Вимоги:

- Enum клас TransactionType зі значеннями: deposit, withdrawal, transfer
- Enum клас TransactionStatus зі значеннями: pending, completed, failed
- Pydantic модель TransactionCreate — для вхідних даних від клієнта
  (поля: fromAccount, toAccount, amount, currency, type)
- Pydantic модель Transaction — повна модель зі всіма полями
  (додає: id як UUID, timestamp як datetime, status зі значенням pending за замовчуванням)
- Pydantic модель TransactionResponse — для відповіді API (можна зробити аліасом Transaction)

Поверни лише код файлу src/models/transaction.py
