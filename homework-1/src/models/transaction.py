import re
from datetime import datetime, timezone
from decimal import Decimal
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field, field_serializer, field_validator, model_validator

ALLOWED_CURRENCIES: frozenset[str] = frozenset(
    {"USD", "EUR", "GBP", "JPY", "UAH", "CHF", "CAD", "AUD", "CNY", "PLN"}
)
ACCOUNT_PATTERN: re.Pattern[str] = re.compile(r"^ACC-[A-Z0-9]{5}$")


class TransactionType(str, Enum):
    deposit = "deposit"
    withdrawal = "withdrawal"
    transfer = "transfer"


class TransactionStatus(str, Enum):
    pending = "pending"
    completed = "completed"
    failed = "failed"


class TransactionCreate(BaseModel):
    """Input model for creating a new transaction."""

    fromAccount: str
    toAccount: str
    amount: Decimal
    currency: str
    type: TransactionType

    @field_validator("fromAccount", "toAccount")
    @classmethod
    def validate_account_format(cls, v: str) -> str:
        """Validate account number matches ACC-XXXXX format."""
        if not ACCOUNT_PATTERN.match(v):
            raise ValueError("Account number must follow format ACC-XXXXX")
        return v

    @field_validator("amount")
    @classmethod
    def validate_amount(cls, v: Decimal) -> Decimal:
        """Validate amount is positive and has at most 2 decimal places."""
        if v <= 0 or v.as_tuple().exponent < -2:
            raise ValueError("Amount must be a positive number with max 2 decimal places")
        return v

    @field_validator("currency", mode="before")
    @classmethod
    def validate_currency(cls, v: str) -> str:
        """Validate currency is an allowed ISO 4217 code."""
        normalized = v.upper() if isinstance(v, str) else v
        if normalized not in ALLOWED_CURRENCIES:
            raise ValueError(
                "Invalid currency code. Supported: USD, EUR, GBP, JPY, UAH, CHF, CAD, AUD, CNY, PLN"
            )
        return normalized

    @field_serializer("amount")
    def serialize_amount(self, v: Decimal) -> float:
        """Serialize Decimal amount as a JSON number."""
        return float(v)

    @model_validator(mode="after")
    def accounts_must_differ_for_transfer(self) -> "TransactionCreate":
        """Reject transfers where fromAccount and toAccount are identical."""
        if self.type == TransactionType.transfer and self.fromAccount == self.toAccount:
            raise ValueError("fromAccount and toAccount must differ for a transfer")
        return self


class Transaction(TransactionCreate):
    """Full transaction model including auto-generated fields."""

    id: UUID = Field(default_factory=uuid4)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    status: TransactionStatus = TransactionStatus.pending


TransactionResponse = Transaction
