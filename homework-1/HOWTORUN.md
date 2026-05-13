# How to Run the Application

## Requirements

- Python 3.12+

## Setup

**1. Clone the repository and navigate to the project folder:**

```bash
cd homework-1
```

**2. Create and activate a virtual environment:**

```bash
python3 -m venv .venv
source .venv/bin/activate
```

**3. Install dependencies:**

```bash
pip install -r requirements.txt
```

## Run

**Start the development server:**

```bash
python3 -m uvicorn src.main:app --reload --port 3000
```

The API will be available at:

- Base URL: http://localhost:3000
- Swagger UI (interactive docs): http://localhost:3000/docs
- ReDoc: http://localhost:3000/redoc

## Stop

Press `Ctrl+C` in the terminal where the server is running.

Or stop it from another terminal:

```bash
pkill -f "uvicorn src.main:app"
```

## Run Tests

```bash
python3 -m pytest tests/ -v
```
