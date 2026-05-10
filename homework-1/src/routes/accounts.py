from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from src.storage.transaction_store import TransactionStore, get_transaction_store

router = APIRouter(prefix="/accounts", tags=["accounts"])

Store = Annotated[TransactionStore, Depends(get_transaction_store)]


@router.get("/{account_id}/balance", status_code=status.HTTP_200_OK)
def get_account_balance(account_id: str, store: Store) -> dict:
    """Return the net balance for an account, or 404 if no transactions exist."""
    transactions = store.get_all(account_id=account_id)
    if not transactions:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": "Account not found", "details": []},
        )
    return store.get_balance(account_id)


@router.get("/{account_id}/summary", status_code=status.HTTP_200_OK)
def get_account_summary(account_id: str, store: Store) -> dict:
    """Return deposit/withdrawal totals and stats for an account, or 404 if no transactions exist."""
    transactions = store.get_all(account_id=account_id)
    if not transactions:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": "Account not found", "details": []},
        )
    return store.get_summary(account_id)
