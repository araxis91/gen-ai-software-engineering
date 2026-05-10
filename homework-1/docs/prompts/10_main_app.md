Always use 1_project_architecture.md file as main project documentation

Ось мої роутери:

- transactions router: src/routes/transactions.py
- accounts router: src/routes/accounts.py

Поточна задача: створити файл src/main.py

Створи FastAPI додаток який:

1. Підключає обидва роутери
2. Має title: "Banking Transactions API", version: "1.0.0"
3. Має кастомний exception handler для ValidationError який повертає формат:
   {"error": "Validation failed", "details": [...]}
4. Має endpoint GET / який повертає {"message": "Banking Transactions API", "docs": "/docs"}

Поверни лише код файлу src/main.py
