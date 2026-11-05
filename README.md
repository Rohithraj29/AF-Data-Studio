# AF Data Studio

**ALTIUS FORTIUS --- HIGHER AND STRONGER**

AF Data Studio is a web-based data preparation and management
application designed to import datasets, inspect records, detect
duplicate entries, review and merge duplicate records, track merge
history, undo merges, and export cleaned data.

## Project status

The core dataset and duplicate-management workflow has been implemented
and manually tested:

-   Excel dataset import and storage
-   Dataset listing and record viewing
-   Exact and near-duplicate detection
-   Duplicate record review
-   Merge preview and field-level value selection
-   Merge execution with audit history
-   Undo of completed merge operations
-   Refresh of duplicate results after merge and undo
-   CSV and Excel export that excludes merged records

This is an actively developed project. Features and implementation
details may change.

## Technology stack

### Frontend

-   Next.js
-   React
-   TypeScript

### Backend

-   Java 21
-   Spring Boot
-   Maven
-   Spring Data / JDBC
-   PostgreSQL

## Repository structure

``` text
AF-Data-Studio/
├── frontend/       # Next.js frontend application
├── backend/        # Spring Boot REST API
├── README.md       # Project documentation (this file)
└── .gitignore      # Files and folders excluded from Git
```

## Prerequisites

Install the following before running the project:

-   Git
-   Node.js and npm
-   Java Development Kit (JDK) 21
-   Maven (or use the Maven Wrapper if included in the backend)
-   PostgreSQL

## Getting started

### 1. Clone the repository

``` bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd AF-Data-Studio
```

Replace `<YOUR_GITHUB_REPOSITORY_URL>` with the HTTPS or SSH URL of your
GitHub repository.

### 2. Configure PostgreSQL

Create a PostgreSQL database for the application. The current
development database has been named `dataforge_db`.

``` sql
CREATE DATABASE dataforge_db;
```

Configure the backend database connection in the backend's Spring
configuration (for example,
`backend/src/main/resources/application.properties` or the corresponding
YAML file).

Example only --- adjust the username, password, host, port, and property
names to match your local configuration:

``` properties
spring.datasource.url=jdbc:postgresql://localhost:5432/dataforge_db
spring.datasource.username=YOUR_DATABASE_USERNAME
spring.datasource.password=YOUR_DATABASE_PASSWORD
```

**Security:** Do not commit real database passwords, API keys, tokens,
or private environment files to GitHub. Use environment variables or a
local, untracked configuration file for secrets.

### 3. Run the backend

Open a terminal in the backend folder:

``` bash
cd backend
```

Run with the Maven Wrapper if it exists:

Windows:

``` powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

``` bash
./mvnw spring-boot:run
```

Alternatively, if Maven is installed:

``` bash
mvn spring-boot:run
```

The backend development server is configured to use port `8080`. A
health endpoint is available at:

``` text
http://localhost:8080/api/health
```

### 4. Run the frontend

Open a second terminal in the frontend folder:

``` bash
cd frontend
npm install
npm run dev
```

Open:

``` text
http://localhost:3000
```

The frontend uses the Next.js development server. Its `/backend`
requests are configured to be proxied to the backend API; keep the
backend running while using data features.

## Main application workflows

1.  Import an Excel dataset.
2.  Review the imported dataset and its columns.
3.  Find exact or near-duplicate groups using the appropriate matching
    column.
4.  Review the records in a duplicate group.
5.  Preview the merge and choose values for conflicting fields.
6.  Execute the merge.
7.  Review merge history and undo an operation when required.
8.  Export the active dataset to Excel or CSV.

**Important:** Choose the intended matching column before running
duplicate detection. For example, selecting `customer_name` finds
repeated names; selecting `email` finds repeated email addresses.
Different matching columns can produce different duplicate groups.

## API overview

The backend currently includes API routes in these areas:

  -----------------------------------------------------------------------------------------------------------------
  Area                                Endpoint
  ----------------------------------- -----------------------------------------------------------------------------
  Health                              `GET /api/health`

  Datasets                            `GET /api/datasets`

  Dataset details                     `GET /api/datasets/{id}`

  Dataset records                     `GET /api/datasets/{id}/data?page=0&pageSize=10`

  Create dataset                      `POST /api/datasets`

  Import preview                      `POST /api/import/preview`

  Import execution                    `POST /api/import/execute`

  Exact duplicates                    `GET /api/datasets/{id}/duplicates/exact?column=email`

  Near duplicates                     `GET /api/datasets/{id}/duplicates/near?column=company_name&threshold=0.80`

  Merge preview                       `POST /api/datasets/{id}/merge/preview`

  Merge execution                     `POST /api/datasets/{id}/merge/execute`

  Merge history                       `GET /api/datasets/{id}/merge/history`

  Undo merge                          `POST /api/datasets/{id}/merge/{operationId}/undo`
  -----------------------------------------------------------------------------------------------------------------

Export routes are implemented in the backend; check the export
controller for the exact route paths in the current version.

## Data and merge safety

-   Merged source records are retained for audit and undo rather than
    physically deleted.
-   The active dataset export excludes records marked as merged.
-   Merge history stores the original record snapshots and the selected
    consolidated values.
-   Keep regular backups of your PostgreSQL database, especially before
    testing with important data.

## Development notes

-   Frontend source code is in `frontend/`.
-   Backend source code is in `backend/`.
-   Do not commit generated dependencies or build output such as
    `node_modules/`, `.next/`, `target/`, or `dist/`.
-   Do not commit local environment files containing secrets.
-   Use test datasets for development and validation.

## License

No open-source license has been specified yet. Unless a license is added
to this repository, all rights remain with the project owner and reuse
is not automatically granted.
