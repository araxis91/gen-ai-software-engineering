from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import ValidationError

from src.models.transaction import Transaction, TransactionCreate, TransactionResponse
from src.storage.transaction_store import TransactionStore, get_transaction_store

router = APIRouter(prefix="/transactions", tags=["transactions"])

Store = Annotated[TransactionStore, Depends(get_transaction_store)]


def _validation_error_response(exc: ValidationError) -> dict:
    """Convert a Pydantic ValidationError into the standard API error format."""
    return {
        "error": "Validation failed",
        "details": [
            {"field": ".".join(str(loc) for loc in err["loc"]), "message": err["msg"]}
            for err in exc.errors()
        ],
    }


@router.post("", response_model=TransactionResponse, status_code=status.HTTP_201_CREATED)
def create_transaction(payload: dict, store: Store) -> Transaction:
    """Create a new transaction and persist it in the store."""
    try:
        data = TransactionCreate(**payload)
    except ValidationError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=_validation_error_response(exc),
        )

    transaction = Transaction(**data.model_dump())
    return store.add(transaction)


@router.get("", response_model=list[TransactionResponse], status_code=status.HTTP_200_OK)
def list_transactions(
    store: Store,
    accountId: str | None = Query(default=None),
    type: str | None = Query(default=None),
    from_: str | None = Query(default=None, alias="from"),
    to: str | None = Query(default=None),
) -> list[Transaction]:
    """Return all transactions with optional filtering."""
    return store.get_all(
        account_id=accountId,
        type=type,
        from_date=from_,
        to_date=to,
    )


@router.get("/{transaction_id}", response_model=TransactionResponse, status_code=status.HTTP_200_OK)
def get_transaction(transaction_id: str, store: Store) -> Transaction:
    """Return a single transaction by ID, or 404 if not found."""
    transaction = store.get_by_id(transaction_id)
    if transaction is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": "Transaction not found", "details": []},
        )
    return transaction
