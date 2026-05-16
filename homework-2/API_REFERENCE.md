# API Reference
This document describes the HTTP contract for the Intelligent Customer Support System.
## Base URL
- Local: `http://localhost:8080`
## Content types
- Request: `application/json` (except file upload endpoint)
- Response: `application/json`
- Import upload: `multipart/form-data`
## Authentication
No authentication is configured in the current implementation.
## Endpoints
### 1) Create ticket
`POST /tickets`
Optional query params:
- `autoClassify=true`
- `auto_classify=true`
Request body:
```json
{
  "customer_id": "cust-001",
  "customer_email": "customer@example.com",
  "customer_name": "Jane Customer",
  "subject": "Cannot access account",
  "description": "Cannot access account after enabling 2FA. This blocks production work.",
  "category": "account_access",
  "priority": "high",
  "status": "new",
  "resolved_at": null,
  "assigned_to": null,
  "tags": ["login", "2fa"],
  "metadata": {
    "source": "web_form",
    "browser": "Chrome",
    "device_type": "desktop"
  }
}
```
Success response (`201 Created`):
```json
{
  "id": "9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4",
  "customer_id": "cust-001",
  "customer_email": "customer@example.com",
  "customer_name": "Jane Customer",
  "subject": "Cannot access account",
  "description": "Cannot access account after enabling 2FA. This blocks production work.",
  "category": "account_access",
  "priority": "high",
  "status": "new",
  "created_at": "2026-05-16T15:00:00.000",
  "updated_at": "2026-05-16T15:00:00.000",
  "resolved_at": null,
  "assigned_to": null,
  "tags": ["login", "2fa"],
  "metadata": {
    "source": "web_form",
    "browser": "Chrome",
    "device_type": "desktop"
  }
}
```
cURL:
```bash
curl -X POST "http://localhost:8080/tickets?auto_classify=true" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id":"cust-001",
    "customer_email":"customer@example.com",
    "customer_name":"Jane Customer",
    "subject":"Cannot access account",
    "description":"Cannot access account after enabling 2FA. This blocks production work.",
    "category":"other",
    "priority":"low",
    "status":"new",
    "tags":["login","2fa"],
    "metadata":{"source":"web_form","browser":"Chrome","device_type":"desktop"}
  }'
```
### 2) Bulk import tickets
`POST /tickets/import`
Request:
- multipart field name: `file`
- supported file types: CSV, JSON, XML
Success response (`200 OK`):
```json
{
  "total_records": 50,
  "successful": 50,
  "failed": 0,
  "errors": []
}
```
Partial-failure response example (`200 OK`):
```json
{
  "total_records": 3,
  "successful": 0,
  "failed": 3,
  "errors": [
    {"record_number": 1, "message": "customerEmail: must be a well-formed email address"},
    {"record_number": 2, "message": "Invalid category value: invalid_category"},
    {"record_number": 3, "message": "metadata.source: must not be null"}
  ]
}
```
cURL:
```bash
curl -X POST "http://localhost:8080/tickets/import" \
  -F "file=@sample_tickets.csv;type=text/csv"
```
### 3) List tickets
`GET /tickets`
Query params:
- `category`
- `priority`
- `status`
- `customerId`
- `customerEmail`
- `createdAtFrom` (ISO date-time)
- `createdAtTo` (ISO date-time)
- `page` (default `0`)
- `size` (default `20`)
- `sort` (default `createdAt,desc`)
Success response (`200 OK`) is a Spring Data `Page` payload. Example below is trimmed to key fields:
```json
{
  "content": [
    {
      "id": "9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4",
      "customer_id": "cust-001",
      "customer_email": "customer@example.com",
      "customer_name": "Jane Customer",
      "subject": "Cannot access account",
      "description": "Cannot access account after enabling 2FA. This blocks production work.",
      "category": "account_access",
      "priority": "high",
      "status": "new",
      "created_at": "2026-05-16T15:00:00.000",
      "updated_at": "2026-05-16T15:00:00.000",
      "resolved_at": null,
      "assigned_to": null,
      "tags": ["login", "2fa"],
      "metadata": {"source": "web_form", "browser": "Chrome", "device_type": "desktop"}
    }
  ]
}
```
cURL:
```bash
curl "http://localhost:8080/tickets?category=billing_question&priority=high&page=0&size=10"
```
### 4) Get ticket by ID
`GET /tickets/{id}`
Success response: `200 OK` with `TicketResponse`.
cURL:
```bash
curl "http://localhost:8080/tickets/9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4"
```
### 5) Update ticket
`PUT /tickets/{id}`
Request body: same schema as create.
Success response: `200 OK` with updated `TicketResponse`.
Notes:
- `resolved_at` is managed by status transitions in service logic.
- Manual category/priority changes are recorded as manual classification override.
cURL:
```bash
curl -X PUT "http://localhost:8080/tickets/9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id":"cust-001",
    "customer_email":"customer@example.com",
    "customer_name":"Jane Customer",
    "subject":"Issue resolved",
    "description":"Issue is now resolved and customer has confirmed the fix.",
    "category":"technical_issue",
    "priority":"medium",
    "status":"resolved",
    "tags":["resolved"],
    "metadata":{"source":"web_form","browser":"Chrome","device_type":"desktop"}
  }'
```
### 6) Delete ticket
`DELETE /tickets/{id}`
Success response: `204 No Content`
cURL:
```bash
curl -X DELETE "http://localhost:8080/tickets/9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4"
```
### 7) Auto-classify ticket
`POST /tickets/{id}/auto-classify`
Success response (`200 OK`):
```json
{
  "ticket_id": "9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4",
  "category": "account_access",
  "priority": "urgent",
  "confidence": 0.86,
  "reasoning": "Category 'account_access' selected because matched category keywords [login, password]; priority 'urgent' selected because matched priority keywords [cannot access, critical].",
  "keywords_found": ["login", "password", "cannot access", "critical"]
}
```
cURL:
```bash
curl -X POST "http://localhost:8080/tickets/9dbd9f16-3c5f-4c74-bf23-e1f0e96570b4/auto-classify"
```
## Schemas
### Ticket enums
- `category`: `account_access | technical_issue | billing_question | feature_request | bug_report | other`
- `priority`: `urgent | high | medium | low`
- `status`: `new | in_progress | waiting_customer | resolved | closed`
- `metadata.source`: `web_form | email | api | chat | phone`
- `metadata.device_type`: `desktop | mobile | tablet`
### `TicketRequest`
- `customer_id`: string, required
- `customer_email`: email, required
- `customer_name`: string, required
- `subject`: string, required, length `1..200`
- `description`: string, required, length `10..2000`
- `category`: enum, required
- `priority`: enum, required
- `status`: enum, required
- `resolved_at`: datetime, nullable
- `assigned_to`: string, nullable
- `tags`: array of non-blank strings
- `metadata`: object, required
### `TicketResponse`
`TicketRequest` fields plus:
- `id`: UUID
- `created_at`: datetime
- `updated_at`: datetime
### `TicketImportSummaryResponse`
- `total_records`: int
- `successful`: int
- `failed`: int
- `errors`: list of `{record_number, message}`
### `TicketAutoClassificationResponse`
- `ticket_id`: UUID
- `category`: enum
- `priority`: enum
- `confidence`: number (`0..1`)
- `reasoning`: string
- `keywords_found`: string[]
## Error response format
All handled errors use:
```json
{
  "timestamp": "2026-05-16T15:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed.",
  "path": "/tickets",
  "details": [
    {"field": "customerEmail", "message": "must be a well-formed email address"}
  ]
}
```
Common status codes:
- `400` bad request / validation / malformed payload
- `404` ticket not found
- `415` unsupported media type
- `500` unexpected server error
## AI model/tool provenance
- Tool: Warp Oz agent
- Model setting: `auto` (dynamically resolved by Warp)
- This reference was drafted from code-level contracts in controller, DTO, and global exception handler classes.
