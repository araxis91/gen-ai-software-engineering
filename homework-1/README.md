# 🏦 Banking Transactions API

> **Student:** Dmytro Cherneha
> **Date Submitted:** 2026-05-10
> **AI Tools Used:** Claude Code (claude-sonnet-4-6)

A RESTful API for managing banking transactions built with FastAPI and Pydantic v2.
Supports creating and querying transactions across accounts with full validation.
Storage is in-memory — no database required.

---

## 🔗 Endpoints

| Method | Path                     | Description                    | Status    |
| ------ | ------------------------ | ------------------------------ | --------- |
| `GET`  | `/`                      | Health check                   | 200       |
| `POST` | `/transactions`          | Create a transaction           | 201 / 400 |
| `GET`  | `/transactions`          | List transactions (filterable) | 200       |
| `GET`  | `/transactions/{id}`     | Get transaction by ID          | 200 / 404 |
| `GET`  | `/accounts/{id}/balance` | Get account balance            | 200 / 404 |
| `GET`  | `/accounts/{id}/summary` | Get account statistics         | 200 / 404 |

---

## 📡 Request & Response Examples

### POST /transactions

```bash
curl -X POST http://localhost:3000/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccount": "ACC-A1B2C",
    "toAccount": "ACC-00001",
    "amount": 250.50,
    "currency": "USD",
    "type": "transfer"
  }'
```

```json
{
  "fromAccount": "ACC-A1B2C",
  "toAccount": "ACC-00001",
  "amount": 250.5,
  "currency": "USD",
  "type": "transfer",
  "id": "06f00e63-2385-4d4a-a59f-7fc5ae83be9b",
  "timestamp": "2026-05-10T12:53:08.963081Z",
  "status": "pending"
}
```

**Validation error (400):**

```bash
curl -X POST http://localhost:3000/transactions \
  -H "Content-Type: application/json" \
  -d '{"fromAccount": "INVALID", "amount": -10, "currency": "XYZ", "type": "transfer"}'
```

```json
{
  "detail": {
    "error": "Validation failed",
    "details": [
      {
        "field": "fromAccount",
        "message": "Value error, Account number must follow format ACC-XXXXX"
      },
      {
        "field": "amount",
        "message": "Value error, Amount must be a positive number with max 2 decimal places"
      },
      {
        "field": "currency",
        "message": "Value error, Invalid currency code. Supported: USD, EUR, GBP, JPY, UAH, CHF, CAD, AUD, CNY, PLN"
      }
    ]
  }
}
```

---

### GET /transactions

```bash
# All transactions
curl http://localhost:3000/transactions

# Filter by account
curl "http://localhost:3000/transactions?accountId=ACC-00001"

# Filter by type
curl "http://localhost:3000/transactions?type=deposit"

# Filter by date range
curl "http://localhost:3000/transactions?from=2026-01-01&to=2026-12-31"
```

```json
[
  {
    "fromAccount": "ACC-A1B2C",
    "toAccount": "ACC-00001",
    "amount": 250.5,
    "currency": "USD",
    "type": "transfer",
    "id": "06f00e63-2385-4d4a-a59f-7fc5ae83be9b",
    "timestamp": "2026-05-10T12:53:08.963081Z",
    "status": "pending"
  }
]
```

---

### GET /transactions/{id}

```bash
curl http://localhost:3000/transactions/06f00e63-2385-4d4a-a59f-7fc5ae83be9b
```

**Not found (404):**

```json
{ "detail": { "error": "Transaction not found", "details": [] } }
```

---

### GET /accounts/{id}/balance

```bash
curl http://localhost:3000/accounts/ACC-00001/balance
```

```json
{
  "accountId": "ACC-00001",
  "balance": 250.5,
  "currency": "USD"
}
```

---

### GET /accounts/{id}/summary

```bash
curl http://localhost:3000/accounts/ACC-00001/summary
```

```json
{
  "total_deposits": 0.0,
  "total_withdrawals": 0.0,
  "transaction_count": 1,
  "last_transaction_date": "2026-05-10T12:53:08.963081+00:00"
}
```

---

## 🏗️ Architecture Decisions

**FastAPI** — automatic OpenAPI docs generation, native async support, and type-based dependency injection via `Depends`. Pydantic models are used directly as request/response schemas with zero boilerplate.

**Pydantic v2** — declarative validation with `@field_validator` and `@model_validator` keeps all business rules in one place (the model). Errors are structured and consistent across all endpoints. Serialization of `Decimal`, `UUID`, and `datetime` is handled automatically.

**In-memory storage (`dict`)** — sufficient for a learning project; eliminates infrastructure setup and keeps focus on API design. The `TransactionStore` is injected via `Depends(get_transaction_store)` — no global variables, easy to swap for a real database later, and trivial to replace with a fresh instance in tests via `dependency_overrides`.

---

## 🤖 AI Assistance

Claude Code (claude-sonnet-4-6) was used throughout the project as a pair programmer:

- Generated the initial project structure, `requirements.txt`, and `.gitignore`
- Implemented all Pydantic models with validators, catching issues like deprecated `datetime.utcnow()`, naive vs. aware datetime comparison, and `Decimal` JSON serialization
- Wrote all FastAPI routes and the storage layer
- Generated integration and unit tests — the tests immediately caught a real bug in `transaction_store.py` (timezone-naive/aware datetime comparison in date filters)

Prompts were written in Ukrainian and followed a consistent pattern: reference the architecture doc, describe the task, list requirements, request only the file content.

---

<div align="center">

_This project was completed as part of the AI-Assisted Development course._

</div>
