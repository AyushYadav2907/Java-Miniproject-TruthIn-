# TruthScan API

Base URL: `http://localhost:8080` (local) or your Render URL.
All responses are JSON. Errors always look like:

```json
{ "code": "NOT_FOUND", "message": "Human-readable text", "details": [] }
```

## Public endpoints

### GET /api/products/{barcode}
Returns the product, its 1–5 rating and warnings. Looks in your database first, then Open Food Facts (and saves it).

| Query param | Example | Notes |
|---|---|---|
| `conditions` | `diabetes,hypertension` | optional. Keys: `diabetes`, `hypertension`, `heart`, `weight_loss`, `pregnancy`, `kids` |
| `allergies` | `milk,peanuts` | optional. Keys: `milk`, `peanuts`, `tree nuts`, `gluten`, `soy`, `egg`, `sesame`, `fish`, `crustaceans`, `mustard` |

Nothing about the health profile is stored on the server.

Response fields: `id, barcode, name, brand, category, imageUrl, ingredientsText, nutrition{energyKcal,sugar,fat,saturatedFat,salt,fiber,protein}, score (1.0–5.0 or null), scoreLabel, dataComplete, nutrientLevels, scoreReasons[], flags[{name,category,severity,reason}], allergens[], mayContain[], personalWarnings[], source (OPEN_FOOD_FACTS|USER), sourceUrl, status (APPROVED|PENDING)`

Errors: `404 NOT_FOUND` (not in any database, show the "add product" form), `404 PENDING_REVIEW` (submitted, waiting for admin).

### GET /api/products/search?q=biscuit
Up to 20 approved products matching the text. Same fields as above.

### POST /api/products
Submit a missing product. It stays hidden until an admin approves it. Returns `201 Created` with the product (status `PENDING`).

```json
{
  "barcode": "8901234567890",
  "name": "Example Biscuits",
  "brand": "Example",
  "category": "Biscuits",
  "ingredientsText": "Wheat flour, sugar, palm oil, salt",
  "allergens": "gluten",
  "energyKcal": 480, "sugar": 25, "fat": 20, "saturatedFat": 9,
  "salt": 1.1, "fiber": 2, "protein": 6,
  "submittedBy": "you@example.com"
}
```

Rules: `barcode` (8–14 digits) and `name` are required; energyKcal 0–900; the other numbers 0–100 (per 100 g/ml); `submittedBy` must be an email if given.
Errors: `400 VALIDATION_FAILED` (with `details` list), `400 BAD_REQUEST` (bad JSON), `409 ALREADY_EXISTS`.

## Admin endpoints
Every request needs the header `X-Admin-Key: <your ADMIN_KEY>`. Missing or wrong key gives `401 UNAUTHORIZED`. If `ADMIN_KEY` is empty, admin is disabled. Local dev key: `dev-admin-key`.

| Method | Path | What it does |
|---|---|---|
| GET | `/api/admin/products/pending` | List products waiting for review |
| POST | `/api/admin/products/{id}/approve` | Approve (makes it public) |
| DELETE | `/api/admin/products/{id}` | Reject (deletes it), returns `204` |

## Other
| Method | Path | Notes |
|---|---|---|
| GET | `/actuator/health` | Health check used by Render |
| any | `500 SERVER_ERROR` | Unexpected error, generic message |
