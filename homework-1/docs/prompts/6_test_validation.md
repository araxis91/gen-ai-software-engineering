Always use 1_project_architecture.md file as main project documentation

Ось мої Pydantic моделі: файл src/models/transaction.py

Напиши pytest тести у файл tests/test_models.py для перевірки валідації.

Покрий обов'язково:

- Happy path: валідна транзакція створюється успішно
- amount: від'ємне число, нуль, 3 знаки після коми, рядок замість числа
- fromAccount/toAccount: без префіксу ACC-, 4 символи, 6 символів, малі літери
- currency: неіснуючий код (XYZ), порожній рядок, число

Кожен тест має назву що описує що саме тестується.
Поверни лише код файлу tests/test_models.py
