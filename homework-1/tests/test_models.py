import pytest
from decimal import Decimal
from pydantic import ValidationError

from src.models.transaction import Transaction, TransactionCreate, TransactionStatus, TransactionType


VALID_PAYLOAD: dict = {
    "fromAccount": "ACC-A1B2C",
    "toAccount": "ACC-00001",
    "amount": Decimal("99.99"),
    "currency": "USD",
    "type": TransactionType.transfer,
}


def make(**overrides) -> dict:
    return {**VALID_PAYLOAD, **overrides}


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

def test_valid_transaction_create_succeeds():
    tx = TransactionCreate(**VALID_PAYLOAD)
    assert tx.fromAccount == "ACC-A1B2C"
    assert tx.toAccount == "ACC-00001"
    assert tx.amount == Decimal("99.99")
    assert tx.currency == "USD"
    assert tx.type == TransactionType.transfer


def test_transaction_auto_fields_are_set():
    tx = Transaction(**VALID_PAYLOAD)
    assert tx.id is not None
    assert tx.timestamp is not None
    assert tx.status == TransactionStatus.pending


def test_currency_lowercase_is_normalized_to_uppercase():
    tx = TransactionCreate(**make(currency="eur"))
    assert tx.currency == "EUR"


def test_deposit_type_accepted():
    tx = TransactionCreate(**make(type=TransactionType.deposit))
    assert tx.type == TransactionType.deposit


def test_withdrawal_type_accepted():
    tx = TransactionCreate(**make(type=TransactionType.withdrawal))
    assert tx.type == TransactionType.withdrawal


def test_amount_with_one_decimal_place_is_valid():
    tx = TransactionCreate(**make(amount=Decimal("10.5")))
    assert tx.amount == Decimal("10.5")


def test_amount_integer_value_is_valid():
    tx = TransactionCreate(**make(amount=Decimal("100")))
    assert tx.amount == Decimal("100")


def test_same_accounts_allowed_for_deposit():
    tx = TransactionCreate(**make(
        fromAccount="ACC-00001",
        toAccount="ACC-00001",
        type=TransactionType.deposit,
    ))
    assert tx.fromAccount == tx.toAccount


# ---------------------------------------------------------------------------
# amount validation
# ---------------------------------------------------------------------------

def test_negative_amount_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(amount=Decimal("-10.00")))


def test_zero_amount_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(amount=Decimal("0")))


def test_amount_with_three_decimal_places_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(amount=Decimal("10.999")))


def test_amount_as_string_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(amount="not-a-number"))


# ---------------------------------------------------------------------------
# fromAccount / toAccount validation
# ---------------------------------------------------------------------------

def test_account_without_acc_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(fromAccount="12345"))


def test_account_with_four_chars_after_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(fromAccount="ACC-1234"))


def test_account_with_six_chars_after_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(fromAccount="ACC-123456"))


def test_account_with_lowercase_letters_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(fromAccount="ACC-abcde"))


def test_to_account_without_acc_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(toAccount="12345"))


def test_to_account_with_four_chars_after_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(toAccount="ACC-1234"))


def test_to_account_with_six_chars_after_prefix_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(toAccount="ACC-123456"))


def test_to_account_with_lowercase_letters_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(toAccount="ACC-abcde"))


def test_transfer_with_same_from_and_to_account_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(
            fromAccount="ACC-00001",
            toAccount="ACC-00001",
            type=TransactionType.transfer,
        ))


# ---------------------------------------------------------------------------
# currency validation
# ---------------------------------------------------------------------------

def test_unsupported_currency_code_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(currency="XYZ"))


def test_empty_currency_string_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(currency=""))


def test_numeric_currency_raises_validation_error():
    with pytest.raises(ValidationError):
        TransactionCreate(**make(currency=840))


def test_all_supported_currencies_are_accepted():
    supported = ["USD", "EUR", "GBP", "JPY", "UAH", "CHF", "CAD", "AUD", "CNY", "PLN"]
    for code in supported:
        tx = TransactionCreate(**make(currency=code))
        assert tx.currency == code
