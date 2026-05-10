from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from src.routes.accounts import router as accounts_router
from src.routes.transactions import router as transactions_router

app = FastAPI(title="Banking Transactions API", version="1.0.0")


@app.exception_handler(ValidationError)
async def validation_error_handler(request: Request, exc: ValidationError) -> JSONResponse:
    """Return a structured 400 response for any unhandled Pydantic ValidationError."""
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "error": "Validation failed",
            "details": [
                {"field": ".".join(str(loc) for loc in err["loc"]), "message": err["msg"]}
                for err in exc.errors()
            ],
        },
    )


@app.get("/", status_code=status.HTTP_200_OK)
def root() -> dict:
    """Return API name and docs URL."""
    return {"message": "Banking Transactions API", "docs": "/docs"}


# Routers are included after exception_handler and root — FastAPI registers
# handlers in declaration order, so the ValidationError handler must exist
# before any router starts processing requests.
app.include_router(transactions_router)
app.include_router(accounts_router)
