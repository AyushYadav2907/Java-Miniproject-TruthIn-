# TruthScan

Scan a packaged food's barcode and get a simple 1–5 health rating, hidden sugars,
additives, allergens and warnings personalised to your health profile.

**Stack:** Java 17 · Spring Boot 3 · PostgreSQL · Flyway · plain HTML/CSS/JS · html5-qrcode

## Folders

| Folder / file | What is inside |
|---|---|
| `backend/` | Java code (Spring Boot), `pom.xml`, settings, tests, and `API.md` (all endpoints) |
| `frontend/` | The website: HTML pages, `css/`, `js/` (incl. the barcode library) |
| `database/` | SQL setup script (runs automatically on start) |
| `.github/workflows/` | Automatic build + tests on every push |
| `Dockerfile` | Builds the whole app into one runnable image (used by hosting services) |

`backend/pom.xml` copies `frontend/` and `database/` into the app when it is built. Keep its `<resources>` section.

## Run on your computer
Needs Java 17+ and Maven. From the `backend` folder:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Open http://localhost:8080 (admin page `/admin.html`, key `dev-admin-key`). Tests: `mvn test`.

## Hosting
GitHub only stores the code (GitHub Pages cannot run Java). To put the website online, connect this
repo to a host that runs Docker/Java and add a PostgreSQL database with these variables:

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   (from the database)
ADMIN_KEY            a long random secret (password for /admin.html)
OFF_CONTACT_EMAIL    your email
```

Do not set `DATABASE_URL` unless it is in JDBC format (`jdbc:postgresql://...`).
The camera needs `https://`, which hosting services provide automatically.

## Before going public
- Have a nutritionist review the thresholds in `ScoringService` and `IngredientAnalyzer`.
- Keep the Open Food Facts credit (ODbL licence) and the medical disclaimer.
- Pick your own brand name ("TruthScan" is close to "TruthIn").
- If you store health profiles on the server, India's DPDP Act 2023 applies.
