from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID

from src.models.transaction import Transaction, TransactionType


class TransactionStore:
    """In-memory storage for transactions backed by a plain dict."""

    def __init__(self) -> None:
        self._store: dict[UUID, Transaction] = {}

    def add(self, transaction: Transaction) -> Transaction:
        """Save a transaction and return it."""
        self._store[transaction.id] = transaction
        return transaction

    def get_by_id(self, transaction_id: str) -> Transaction | None:
        """Return a transaction by its UUID string, or None if not found."""
        try:
            uid = UUID(transaction_id)
        except ValueError:
            return None
        return self._store.get(uid)

    def get_all(
        self,
        account_id: str | None = None,
        type: str | None = None,
        from_date: str | None = None,
        to_date: str | None = None,
    ) -> list[Transaction]:
        """Return all transactions, optionally filtered by account, type, and date range."""
        results: list[Transaction] = list(self._store.values())

        if account_id is not None:
            results = [
                tx for tx in results
                if tx.fromAccount == account_id or tx.toAccount == account_id
            ]

        if type is not None:
            results = [tx for tx in results if tx.type.value == type]

        if from_date is not None:
            from_dt: datetime = datetime.fromisoformat(from_date).replace(tzinfo=timezone.utc)
            results = [tx for tx in results if tx.timestamp >= from_dt]

        if to_date is not None:
            to_dt: datetime = datetime.fromisoformat(to_date).replace(tzinfo=timezone.utc)
            results = [tx for tx in results if tx.timestamp <= to_dt]

        return results

    def get_balance(self, account_id: str) -> dict:
        """Calculate net balance for an account across all transactions."""
        balance: Decimal = Decimal("0")
        currency: str = ""

        for tx in self._store.values():
            if tx.toAccount == account_id and tx.type == TransactionType.deposit:
                balance += tx.amount
                currency = tx.currency
            elif tx.fromAccount == account_id and tx.type == TransactionType.withdrawal:
                balance -= tx.amount
                currency = tx.currency
            elif tx.type == TransactionType.transfer:
                if tx.toAccount == account_id:
                    balance += tx.amount
                    currency = tx.currency
                elif tx.fromAccount == account_id:
                    balance -= tx.amount
                    currency = tx.currency

        return {
            "accountId": account_id,
            "balance": float(balance),
            "currency": currency,
        }

    def get_summary(self, account_id: str) -> dict:
        """Return deposit/withdrawal totals and transaction count for an account."""
        total_deposits: Decimal = Decimal("0")
        total_withdrawals: Decimal = Decimal("0")
        transaction_count: int = 0
        last_transaction_date: datetime | None = None

        for tx in self._store.values():
            is_involved = tx.fromAccount == account_id or tx.toAccount == account_id
            if not is_involved:
                continue

            transaction_count += 1

            if last_transaction_date is None or tx.timestamp > last_transaction_date:
                last_transaction_date = tx.timestamp

            if tx.type == TransactionType.deposit and tx.toAccount == account_id:
                total_deposits += tx.amount
            elif tx.type == TransactionType.withdrawal and tx.fromAccount == account_id:
                total_withdrawals += tx.amount

        return {
            "total_deposits": float(total_deposits),
            "total_withdrawals": float(total_withdrawals),
            "transaction_count": transaction_count,
            "last_transaction_date": last_transaction_date.isoformat() if last_transaction_date else None,
        }


_store_instance: TransactionStore | None = None


def get_transaction_store() -> TransactionStore:
    """Return the singleton TransactionStore instance for FastAPI Depends."""
    global _store_instance
    if _store_instance is None:
        _store_instance = TransactionStore()
    return _store_instance
