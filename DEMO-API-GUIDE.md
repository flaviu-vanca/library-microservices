# Demo API Guide

Professional Swagger-based API testing guide for the `auth-service`, `library-service`, and `inventory-service`.

## Purpose

This guide provides a structured walkthrough for demonstrating and testing the project APIs through Swagger UI. It is designed for technical walkthroughs and manual verification of the main API flows.

The recommended order is:

1. Authenticate through the Auth Service
2. Use the JWT to authorize Library Service Swagger
3. Use the same JWT to authorize Inventory Service Swagger
4. Run read operations first
5. Run write operations only if an `ADMIN` account is available

## Icon Legend

| Icon | Meaning |
|---|---|
| `🔐` | Authentication or JWT handling |
| `📚` | Library Service testing |
| `📦` | Inventory Service testing |
| `🌐` | Swagger or service URL |
| `⚠️` | Important note or common mistake |
| `✅` | Expected successful outcome |
| `🛡️` | Authorization or role requirement |

## 🌐 Swagger Endpoints

| Service | Swagger URL |
|---|---|
| Auth Service | [Auth Swagger](http://localhost:8084/swagger-ui.html) |
| Library Service | [Library Swagger](http://localhost:8081/swagger-ui.html) |
| Inventory Service | [Inventory Swagger](http://localhost:8083/swagger-ui.html) |

## 🧪 Recommended Demo Data

Use these values for a stable walkthrough.

| Field | Value |
|---|---|
| Library ID | `1` |
| Book ID | `1` |
| ISBN | `978-0-13-468599-1` |
| Book Title | `Clean Code` |
| Library Search City | `Limerick` |
| Inventory Branch ID | `TUS-MOYLISH` |

## Service API Overview

### Auth Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/auth/info` | Retrieve authentication metadata |
| `POST` | `/auth/signup` | Create a member account and receive a JWT |
| `POST` | `/auth/login` | Authenticate an existing member and receive a JWT |
| `POST` | `/auth/renew` | Renew an eligible JWT once |

### Library Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/libraries` | List all libraries |
| `GET` | `/api/libraries/{id}` | Retrieve one library |
| `GET` | `/api/libraries/search` | Search libraries by city |
| `POST` | `/api/libraries` | Create a library |
| `PUT` | `/api/libraries/{id}` | Update a library |
| `DELETE` | `/api/libraries/{id}` | Delete a library |
| `GET` | `/api/books` | List all books |
| `GET` | `/api/books/{id}` | Retrieve one book |
| `GET` | `/api/books/{id}/availability` | Retrieve a book with aggregated inventory |
| `GET` | `/api/books/isbn/{isbn}` | Retrieve a book by ISBN |
| `GET` | `/api/books/search` | Search books |
| `GET` | `/api/books/library/{libraryId}` | Retrieve books for one library |
| `POST` | `/api/books` | Create a book |
| `PUT` | `/api/books/{id}` | Update a book |
| `DELETE` | `/api/books/{id}` | Delete a book |

### Inventory Service

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/inventory/{isbn}` | Retrieve aggregated inventory by ISBN |
| `GET` | `/api/inventory/{isbn}/branches` | Retrieve inventory split by branch |
| `GET` | `/api/inventory/branch/{branchId}` | Retrieve all inventory items for a branch |
| `GET` | `/api/inventory/item/{id}` | Retrieve one inventory item |
| `POST` | `/api/inventory` | Create an inventory item |
| `PUT` | `/api/inventory/item/{id}` | Update an inventory item |
| `DELETE` | `/api/inventory/item/{id}` | Delete an inventory item |
| `POST` | `/api/inventory/{isbn}/reserve` | Reserve one copy |
| `POST` | `/api/inventory/{isbn}/return` | Return one copy |

## 🔐 Step 1: Authenticate Through the Auth Service

Open [Auth Service Swagger](http://localhost:8084/swagger-ui.html).

### 1.1 Retrieve Auth Metadata

Run `GET /auth/info`.

Purpose:

- confirm the active authentication mode
- confirm the registration role
- confirm whether OAuth providers are configured

Expected result:

- `200 OK`

### 1.2 Create a Member Account

Run `POST /auth/signup`.

> ⚠️ **Important:** After signup succeeds, copy the returned `access_token` immediately. You will need this token to authenticate Library Service Swagger and Inventory Service Swagger.

Use a fresh email address each time:

> Copy and paste this JSON into [Auth Service Swagger](http://localhost:8084/swagger-ui.html) -> `POST /auth/signup` -> `Try it out` -> `Request body`.

```json
{
  "fullName": "Swagger Demo Member",
  "email": "swagger-demo-20260327123045@library.local",
  "password": "Library123"
}
```

Expected result:

- `200 OK`
- response contains `access_token`
- role is `USER`

> ⚠️ **Important:** Keep the copied token available before moving to the next service.

Password rules:

- minimum 8 characters
- maximum 100 characters
- must contain at least one letter
- must contain at least one number

### 1.3 Log In to an Existing Account

If the signup email already exists, use `POST /auth/login` instead:

> ⚠️ **Important:** After login succeeds, copy the returned `access_token`. You will use the same token in the other Swagger pages.
>
> Copy and paste this JSON into [Auth Service Swagger](http://localhost:8084/swagger-ui.html) -> `POST /auth/login` -> `Try it out` -> `Request body`.

```json
{
  "email": "swagger-demo-20260327123045@library.local",
  "password": "Library123"
}
```

Expected result:

- `200 OK`
- response contains `access_token`

> ⚠️ **Important:** Do not continue to Library Service or Inventory Service until you have copied the token.

### 1.4 Authorize Swagger for Protected Services

After signup or login:

1. Copy the `access_token`
2. Open Library Swagger
3. Click `Authorize`
4. Paste the raw JWT token only
5. Repeat the same process in Inventory Swagger

> ⚠️ **Important:** Do not include `Bearer ` in the Swagger authorization dialog. The same copied token is used for both Library Service and Inventory Service authentication.
>
> ⚠️ **Important:** If you forget to copy the token during signup or login, return to Auth Service and get a fresh one before testing the protected APIs.

### 1.5 Renew the JWT

Optional.

Run `POST /auth/renew` only when the token is close to expiry.

Expected result:

- `200 OK` if renewal is allowed
- `409 Conflict` if the token is not eligible for renewal

## 📚 Step 2: Test the Library Service

Open [Library Service Swagger](http://localhost:8081/swagger-ui.html) and ensure it is authorized.

> ⚠️ **Important:** Library Service endpoints require the JWT copied from the Auth Service step.

### 2.1 Test Library GET Operations

#### `GET /api/libraries`

Purpose:

- confirm the service returns the seeded library list

Expected result:

- `200 OK`

#### `GET /api/libraries/{id}`

Use:

- `id = 1`

Purpose:

- confirm a single library can be retrieved by ID

Expected result:

- `200 OK`

#### `GET /api/libraries/search`

Use:

- `city = Limerick`

Purpose:

- confirm city-based search works

Expected result:

- `200 OK`
- includes `TUS Moylish Library`

### 2.2 Test Book GET Operations

#### `GET /api/books`

Purpose:

- confirm the service returns seeded books

Expected result:

- `200 OK`

#### `GET /api/books/{id}`

Use:

- `id = 1`

Purpose:

- confirm book retrieval by ID

Expected result:

- `200 OK`
- book title `Clean Code`

#### `GET /api/books/isbn/{isbn}`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm book retrieval by ISBN

Expected result:

- `200 OK`
- book title `Clean Code`

#### `GET /api/books/search`

Use:

- `q = Clean Code`

Purpose:

- confirm text search works

Expected result:

- `200 OK`
- result includes `Clean Code`

#### `GET /api/books/library/{libraryId}`

Use:

- `libraryId = 1`

Purpose:

- confirm library-specific book listing works

Expected result:

- `200 OK`

#### `GET /api/books/{id}/availability`

Use:

- `id = 1`

Purpose:

- confirm the `library-service` returns book data enriched with inventory data from `inventory-service`

Expected key values:

Compare the response with the key fields below.

```json
{
  "id": 1,
  "isbn": "978-0-13-468599-1",
  "title": "Clean Code",
  "inventory": {
    "totalCopies": 12,
    "availableCopies": 9,
    "reservedCopies": 1,
    "available": true,
    "branchCount": 3
  }
}
```

### 2.3 Test Library Write Operations

These endpoints require an `ADMIN` token.

If you only have a normal member token, the expected result is:

- `403 Forbidden`

> ⚠️ **Important:** The member account created through `/auth/signup` is a `USER` account, not an `ADMIN` account.

#### `POST /api/libraries`

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `POST /api/libraries` -> `Try it out` -> `Request body`.

```json
{
  "name": "Swagger Demo Library",
  "address": "123 Demo Street",
  "city": "Galway",
  "country": "Ireland"
}
```

Expected with admin:

- `201 Created`

Save the returned library `id` for update and delete testing.

#### `PUT /api/libraries/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `PUT /api/libraries/{id}` -> `Try it out` -> `Request body`.

```json
{
  "name": "Swagger Demo Library Updated",
  "city": "Cork"
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/libraries/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

### 2.4 Test Book Write Operations

These endpoints also require an `ADMIN` token.

> ⚠️ **Important:** If you only have a member token, these endpoints should fail with `403 Forbidden`. That is expected behavior.

#### `POST /api/books`

Use a unique ISBN:

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `POST /api/books` -> `Try it out` -> `Request body`.

```json
{
  "isbn": "978-1-99999-001-1",
  "title": "Swagger Demo Book",
  "author": "Demo Author",
  "publicationYear": 2026,
  "genre": "Technology",
  "libraryId": 1
}
```

Expected with admin:

- `201 Created`

Save the returned book `id`.

#### `PUT /api/books/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Library Service Swagger](http://localhost:8081/swagger-ui.html) -> `PUT /api/books/{id}` -> `Try it out` -> `Request body`.

```json
{
  "title": "Swagger Demo Book Updated",
  "author": "Updated Author",
  "publicationYear": 2027,
  "genre": "Software",
  "libraryId": 1
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/books/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

## 📦 Step 3: Test the Inventory Service

Open [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) and ensure it is authorized.

> ⚠️ **Important:** Inventory Service Swagger must also be authorized with the same JWT copied from Auth Service.

### 3.1 Test Inventory GET Operations

#### `GET /api/inventory/{isbn}`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm aggregated inventory lookup works

Expected result:

- `200 OK`

#### `GET /api/inventory/{isbn}/branches`

Use:

- `isbn = 978-0-13-468599-1`

Purpose:

- confirm branch-level inventory breakdown works

Expected branch IDs:

- `TUS-MOYLISH`
- `TUS-ATHLONE`
- `DUBLIN-CITY`

#### `GET /api/inventory/branch/{branchId}`

Use:

- `branchId = TUS-MOYLISH`

Purpose:

- confirm branch-wide inventory listing works

Expected result:

- `200 OK`

Copy one returned inventory item `id`.

#### `GET /api/inventory/item/{id}`

Use the `id` returned from the branch lookup.

Purpose:

- confirm single inventory item retrieval works

Expected result:

- `200 OK`

### 3.2 Test Reserve and Return Operations

These operations work with a normal `USER` token when you call `inventory-service` directly on port `8083`.

> ⚠️ **Important:** These calls work on Inventory Service Swagger directly. They are not the same as going through the gateway policy.

#### `POST /api/inventory/{isbn}/reserve`

Use:

- `isbn = 978-0-13-468599-1`

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory/{isbn}/reserve` -> `Try it out` -> `Request body`.

```json
{
  "branchId": "TUS-MOYLISH"
}
```

Purpose:

- confirm one copy can be reserved

Expected result:

- `200 OK`
- `availableCopies` decreases by 1

#### `POST /api/inventory/{isbn}/return`

Use:

- `isbn = 978-0-13-468599-1`

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory/{isbn}/return` -> `Try it out` -> `Request body`.

```json
{
  "branchId": "TUS-MOYLISH"
}
```

Purpose:

- confirm one copy can be returned

Expected result:

- `200 OK`
- `availableCopies` increases again

### 3.3 Test Inventory Write Operations

These endpoints require an `ADMIN` token.

> ⚠️ **Important:** A member token is sufficient for reserve and return, but not for inventory create, update, or delete operations.

#### `POST /api/inventory`

Use an existing ISBN and a new branch ID:

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `POST /api/inventory` -> `Try it out` -> `Request body`.

```json
{
  "isbn": "978-0-13-468599-1",
  "branchId": "SWAGGER-DEMO",
  "branchName": "Swagger Demo Branch",
  "totalCopies": 5,
  "availableCopies": 4
}
```

Expected with admin:

- `201 Created`

Save the returned inventory item `id`.

#### `PUT /api/inventory/item/{id}`

Use the `id` returned by the create call.

> Copy and paste this JSON into [Inventory Service Swagger](http://localhost:8083/swagger-ui.html) -> `PUT /api/inventory/item/{id}` -> `Try it out` -> `Request body`.

```json
{
  "totalCopies": 6,
  "availableCopies": 5,
  "reservedCopies": 1,
  "branchName": "Swagger Demo Branch Updated"
}
```

Expected with admin:

- `200 OK`

#### `DELETE /api/inventory/item/{id}`

Use the same created `id`.

Expected with admin:

- `204 No Content`

## 🛡️ Authorization Notes

`/auth/signup` creates `USER` accounts only.

> ⚠️ **Important:** The default Swagger walkthrough gives you a member JWT. That token is enough for read operations and direct reserve/return testing on Inventory Service, but not enough for admin write operations.

This means:

- all GET endpoints can be tested with a normal member JWT
- inventory `reserve` and `return` can be tested directly on `inventory-service` with a member JWT
- successful POST, PUT, and DELETE tests for library and inventory require a pre-provisioned `ADMIN` account

If no admin account is available, the write endpoints can still be tested to confirm that the API correctly returns `403 Forbidden` for a member token.

## ⚠️ Troubleshooting

### Swagger returns `401`

> ⚠️ **Important:** Most `401` issues during the demo happen because the JWT was not copied, was copied incorrectly, or Swagger was not authorized again after refresh.

- get a fresh token from Auth Swagger
- click `Authorize` again
- paste the raw JWT only
- do not include `Bearer `

### `POST /auth/signup` returns `409 Conflict`

Use a fresh email address or switch to `POST /auth/login`.

### Swagger says `Failed to fetch`

- confirm the relevant service is running
- refresh the page
- authorize again
