import pytest
from fastapi.testclient import TestClient

from src.main import app
from src.storage.transaction_store import TransactionStore, get_transaction_store

VALID_PAYLOAD: dict = {
    "fromAccount": "ACC-A1B2C",
    "toAccount": "ACC-00001",
    "amount": 99.99,
    "currency": "USD",
    "type": "transfer",
}


def make(**overrides) -> dict:
    return {**VALID_PAYLOAD, **overrides}


@pytest.fixture
def client() -> TestClient:
    """Return a TestClient wired to a fresh empty TransactionStore."""
    fresh_store = TransactionStore()
    app.dependency_overrides[get_transaction_store] = lambda: fresh_store
    yield TestClient(app)
    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# POST /transactions
# ---------------------------------------------------------------------------

def test_create_transaction_returns_201_with_all_fields(client: TestClient):
    response = client.post("/transactions", json=VALID_PAYLOAD)
    assert response.status_code == 201
    body = response.json()
    assert body["fromAccount"] == "ACC-A1B2C"
    assert body["toAccount"] == "ACC-00001"
    assert body["amount"] == 99.99
    assert body["currency"] == "USD"
    assert body["type"] == "transfer"
    assert body["status"] == "pending"
    assert "id" in body
    assert "timestamp" in body


def test_create_transaction_with_deposit_type(client: TestClient):
    response = client.post("/transactions", json=make(type="deposit"))
    assert response.status_code == 201
    assert response.json()["type"] == "deposit"


def test_create_transaction_with_invalid_amount_returns_400(client: TestClient):
    response = client.post("/transactions", json=make(amount=-50))
    assert response.status_code == 400
    body = response.json()
    assert body["detail"]["error"] == "Validation failed"
    assert len(body["detail"]["details"]) > 0


def test_create_transaction_with_zero_amount_returns_400(client: TestClient):
    response = client.post("/transactions", json=make(amount=0))
    assert response.status_code == 400


def test_create_transaction_with_invalid_account_format_returns_400(client: TestClient):
    response = client.post("/transactions", json=make(fromAccount="12345"))
    assert response.status_code == 400


def test_create_transaction_with_unsupported_currency_returns_400(client: TestClient):
    response = client.post("/transactions", json=make(currency="XYZ"))
    assert response.status_code == 400


def test_create_transaction_missing_required_field_returns_400(client: TestClient):
    payload = {k: v for k, v in VALID_PAYLOAD.items() if k != "amount"}
    response = client.post("/transactions", json=payload)
    assert response.status_code == 400


def test_create_transaction_missing_type_field_returns_400(client: TestClient):
    payload = {k: v for k, v in VALID_PAYLOAD.items() if k != "type"}
    response = client.post("/transactions", json=payload)
    assert response.status_code == 400


def test_create_transaction_empty_body_returns_400(client: TestClient):
    response = client.post("/transactions", json={})
    assert response.status_code == 400


def test_create_transaction_currency_lowercase_is_normalized(client: TestClient):
    response = client.post("/transactions", json=make(currency="eur"))
    assert response.status_code == 201
    assert response.json()["currency"] == "EUR"


# ---------------------------------------------------------------------------
# GET /transactions
# ---------------------------------------------------------------------------

def test_list_transactions_returns_empty_list_when_no_transactions(client: TestClient):
    response = client.get("/transactions")
    assert response.status_code == 200
    assert response.json() == []


def test_list_transactions_returns_all_created_transactions(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    client.post("/transactions", json=make(type="deposit"))
    response = client.get("/transactions")
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_list_transactions_filter_by_account_id(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    client.post("/transactions", json=make(fromAccount="ACC-ZZZZZ", toAccount="ACC-YYYYY", type="deposit"))
    response = client.get("/transactions", params={"accountId": "ACC-A1B2C"})
    assert response.status_code == 200
    results = response.json()
    assert len(results) == 1
    assert results[0]["fromAccount"] == "ACC-A1B2C"


def test_list_transactions_filter_by_type(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    client.post("/transactions", json=make(type="deposit"))
    response = client.get("/transactions", params={"type": "deposit"})
    assert response.status_code == 200
    results = response.json()
    assert all(tx["type"] == "deposit" for tx in results)


def test_list_transactions_filter_by_from_date_excludes_older(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    response = client.get("/transactions", params={"from": "2099-01-01"})
    assert response.status_code == 200
    assert response.json() == []


def test_list_transactions_filter_by_to_date_excludes_newer(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    response = client.get("/transactions", params={"to": "2000-01-01"})
    assert response.status_code == 200
    assert response.json() == []


def test_list_transactions_filter_by_date_range_includes_matching(client: TestClient):
    client.post("/transactions", json=VALID_PAYLOAD)
    response = client.get("/transactions", params={"from": "2000-01-01", "to": "2099-12-31"})
    assert response.status_code == 200
    assert len(response.json()) == 1


# ---------------------------------------------------------------------------
# GET /transactions/{id}
# ---------------------------------------------------------------------------

def test_get_transaction_by_id_returns_correct_transaction(client: TestClient):
    created = client.post("/transactions", json=VALID_PAYLOAD).json()
    response = client.get(f"/transactions/{created['id']}")
    assert response.status_code == 200
    assert response.json()["id"] == created["id"]


def test_get_transaction_with_nonexistent_id_returns_404(client: TestClient):
    response = client.get("/transactions/00000000-0000-0000-0000-000000000000")
    assert response.status_code == 404
    assert response.json()["detail"]["error"] == "Transaction not found"


def test_get_transaction_with_invalid_uuid_returns_404(client: TestClient):
    response = client.get("/transactions/not-a-uuid")
    assert response.status_code == 404


# ---------------------------------------------------------------------------
# GET /accounts/{id}/balance
# ---------------------------------------------------------------------------

def test_get_balance_returns_correct_deposit_balance(client: TestClient):
    client.post("/transactions", json=make(
        fromAccount="ACC-SRC00",
        toAccount="ACC-DST00",
        amount=200.00,
        type="deposit",
    ))
    response = client.get("/accounts/ACC-DST00/balance")
    assert response.status_code == 200
    body = response.json()
    assert body["accountId"] == "ACC-DST00"
    assert body["balance"] == 200.00
    assert body["currency"] == "USD"


def test_get_balance_subtracts_withdrawals(client: TestClient):
    client.post("/transactions", json=make(
        fromAccount="ACC-SRC00",
        toAccount="ACC-DST00",
        amount=500.00,
        type="deposit",
    ))
    client.post("/transactions", json=make(
        fromAccount="ACC-DST00",
        toAccount="ACC-SRC00",
        amount=150.00,
        type="withdrawal",
    ))
    response = client.get("/accounts/ACC-DST00/balance")
    assert response.status_code == 200
    assert response.json()["balance"] == 350.00


def test_get_balance_for_nonexistent_account_returns_404(client: TestClient):
    response = client.get("/accounts/ACC-XXXXX/balance")
    assert response.status_code == 404
    assert response.json()["detail"]["error"] == "Account not found"


# ---------------------------------------------------------------------------
# GET /accounts/{id}/summary
# ---------------------------------------------------------------------------

def test_get_summary_returns_all_required_fields(client: TestClient):
    client.post("/transactions", json=make(
        fromAccount="ACC-SRC00",
        toAccount="ACC-DST00",
        amount=100.00,
        type="deposit",
    ))
    response = client.get("/accounts/ACC-DST00/summary")
    assert response.status_code == 200
    body = response.json()
    assert "total_deposits" in body
    assert "total_withdrawals" in body
    assert "transaction_count" in body
    assert "last_transaction_date" in body


def test_get_summary_counts_transactions_correctly(client: TestClient):
    client.post("/transactions", json=make(
        fromAccount="ACC-SRC00",
        toAccount="ACC-DST00",
        amount=100.00,
        type="deposit",
    ))
    client.post("/transactions", json=make(
        fromAccount="ACC-DST00",
        toAccount="ACC-SRC00",
        amount=40.00,
        type="withdrawal",
    ))
    response = client.get("/accounts/ACC-DST00/summary")
    assert response.status_code == 200
    body = response.json()
    assert body["transaction_count"] == 2
    assert body["total_deposits"] == 100.00
    assert body["total_withdrawals"] == 40.00
    assert body["last_transaction_date"] is not None


def test_get_summary_for_nonexistent_account_returns_404(client: TestClient):
    response = client.get("/accounts/ACC-XXXXX/summary")
    assert response.status_code == 404
    assert response.json()["detail"]["error"] == "Account not found"
