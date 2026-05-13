Always use 1_project_architecture.md file as main project documentation

Ось мій повний проєкт:

- main.py: src/main.py
- models: src/models
- storage: src/storage
- routes: src/routes

Напиши інтеграційні pytest тести у tests/test_transactions.py
використовуючи FastAPI TestClient.

Обов'язково покрий:

1. POST /transactions — успішне створення, невалідні дані, відсутні поля
2. GET /transactions — порожній список, фільтр по accountId, по type, по датах
3. GET /transactions/{id} — знайдено, не знайдено (404)
4. GET /accounts/{id}/balance — правильний підрахунок, рахунок не існує
5. GET /accounts/{id}/summary — всі поля присутні

Кожен тест незалежний (використовуй fixture для чистого стану storage).
Поверни лише код файлу tests/test_transactions.py
