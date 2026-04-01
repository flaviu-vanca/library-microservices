# Demo UI Guide

Professional browser-based demonstration guide for the Gateway Member Dashboard.

## Purpose

This guide walks through the user-facing demo of the system through the browser UI exposed by `gateway-service`. It is intended for product walkthroughs and manual verification, with a focus on the member journey rather than direct API testing.

## Icon Legend

| Icon | Meaning |
|---|---|
| `🌐` | URL or browser destination |
| `🔐` | Authentication or session behavior |
| `📚` | Library browsing |
| `🔎` | Search and availability |
| `📦` | Inventory or branch-level stock |
| `⚠️` | Important note or common mistake |
| `✅` | Expected successful outcome |

## 🌐 Quick Links

| Surface | URL |
|---|---|
| Gateway UI | [Gateway UI](http://localhost:8085/) |
| Eureka Dashboard | [Eureka Dashboard](http://localhost:8761) |
| Zipkin Tracing | [Zipkin Tracing](http://localhost:9412) |

## Demo Goal

Show that the gateway exposes a real member-facing browser UI on top of the protected microservice APIs, including:

- account creation and login
- JWT-backed browser session handling
- protected library browsing
- book search
- aggregated availability
- branch-level stock lookup

## Before You Start

Run the smoke check first:

```powershell
.\scripts\demo-check.ps1
```

If the stack was just restarted, wait 30 to 90 seconds and rerun it until the happy path passes.

Open these tabs before the walkthrough:

- [Gateway UI](http://localhost:8085/)
- [Eureka Dashboard](http://localhost:8761)
- [Zipkin Tracing](http://localhost:9412)

> ℹ️ **Default:** A fresh clone exposes the gateway UI on `http://localhost:8085/`. Override it in `.env` only if you need a different host port.

Useful pre-demo facts:

- the page is served directly by `gateway-service`
- the browser communicates only with `/auth/**` and `/api/**` through the gateway host
- the UI always shows email/password and social provider buttons
- only configured social providers are clickable

## Recommended Walkthrough

### 1. 🌐 Open the Gateway UI

Open:

[Gateway UI](http://localhost:8085/)

Point out:

- this is the single browser entry point
- no Postman, curl, or Swagger is needed for the member demo
- the page is a static UI served directly from `gateway-service`

### 2. 🔐 Show the Authentication Surface

Before signing in, explain what is visible:

- `Member Login` tab
- `Create Account` tab
- email and password form
- Google, GitHub, and Facebook buttons
- the authentication note explaining that social login still ends in the same JWT session model

Suggested line:

> “This UI supports local email/password login and can also start OAuth for Google, GitHub, or Facebook when provider credentials are configured. In every case, the user ends up with the same JWT-backed session through the gateway.”

> ⚠️ **Important:** A social login button may be visible but still disabled. That means the provider exists in the UI, but the client credentials are not configured yet.

### 3. 🔐 Create an Account

For a reliable demo, create a fresh member account first.

Use values such as:

- Full name: `UI Demo Member`
- Email: `ui-demo-<timestamp>@library.local`
- Password: `Library123`

Expected outcome:

- account creation succeeds
- the user is logged in immediately
- the dashboard view appears without needing a second login step

Implementation details worth mentioning:

- passwords must be 8 to 100 characters
- passwords must contain at least one letter
- passwords must contain at least one number
- successful signup returns a JWT immediately

> ⚠️ **Important:** Use a fresh email for each live demo signup to avoid duplicate account conflicts.

Optional alternative:

- if the account already exists, switch to `Member Login`
- if social auth is configured, use one of the enabled provider buttons

### 4. 🔐 Explain the Session Banner

After authentication, point out the session strip at the top of the dashboard:

- `Member Dashboard`
- `Signed in as`
- `Role`
- `Security mode`
- `Token expires in`
- `Log Out`
- `Extend Time` when eligible

Important behavior to explain:

- the UI stores a JWT-backed session locally
- the session survives a page refresh
- tokens are issued for 30 minutes
- the token can be extended once
- `Extend Time` appears only in the last 5 minutes
- expired sessions return the user to the auth view

Suggested line:

> “This is a browser session backed by a JWT, not a server-rendered session. The gateway validates the token, and the UI tracks the remaining lifetime visibly.”

### 5. 📚 Browse the Libraries

Scroll to the `All libraries` section.

Show:

- the protected list of libraries
- expandable library cards
- library facts such as city, address, and book count
- per-library book listings inside each expanded card

Recommended stable example:

1. Open `TUS Moylish Library`
2. Show the card expanding in place
3. Point out that the books stay inside the card rather than navigating away
4. Call out a known seeded title such as `Clean Code`

Why this matters:

- the UI is loading protected data only after authentication
- the browser is not showing raw JSON
- the gateway and downstream services are being presented as a usable member experience

### 6. 🔎 Run a Search

Go to the `Find a book` section.

Recommended search inputs:

- `Clean Code`
- `978-0-13-468599-1`

Show:

- the search input
- the results summary
- the availability cards returned for the query

Search behavior worth mentioning:

- title, author, and genre searches need at least 2 characters
- ISBN-like searches can be shorter because they are treated as numeric prefix searches

> ⚠️ **Important:** If a short text search fails, use a more precise query or search by ISBN instead.

### 7. 📦 Show Availability and Branch Stock

Open the `Clean Code` search result.

Point out:

- title
- author
- ISBN
- home library
- available versus total copies
- availability status message

Use these stable seeded values:

- home library: `TUS Moylish Library`
- total copies: `12`
- available copies: `9`
- branches: `3`

Then click `Show Branches`.

Explain:

- the UI makes an additional protected call to `/api/inventory/{isbn}/branches`
- the branch-level inventory is displayed on demand
- this is the detailed inventory view behind the aggregated availability card

Expected branch identifiers:

- `TUS-MOYLISH`
- `TUS-ATHLONE`
- `DUBLIN-CITY`

> ⚠️ **Important:** Friendly branch names may be blank in this environment. That is expected with the current seed data and is not a UI defect.

### 8. ✅ Summarize the Member Journey

At this point, the UI demo has shown:

- a member can create an account or log in
- the browser receives and keeps a JWT-backed session
- protected library data is visible only after authentication
- books can be searched through the UI
- availability is aggregated and understandable
- branch-level stock can be opened only when needed

Suggested wrap-up line:

> “This page gives a real member-facing flow on top of the gateway, auth, library, and inventory services. The backend remains fully service-based, but the user interacts with it as a coherent product.”

### 9. 🌐 Optional Technical Tie-In

If you want to connect the UI to the backend architecture, open Zipkin:

[Zipkin Tracing](http://localhost:9412)

After a recent search or availability request, explain the trace path:

```text
gateway-service -> library-service -> inventory-service
```

This is a useful bridge from the user-facing walkthrough into the technical architecture discussion.

## Suggested Presenter Talk Track

> “The member enters through the gateway UI, creates an account or logs in, receives a JWT-backed session, browses protected library data, searches for a title, and then drills into live stock availability. The experience is simple in the browser, but it is still powered by coordinated cloud-native services underneath.”

## Troubleshooting

### The UI is not reachable

Wait briefly and rerun:

```powershell
.\scripts\demo-check.ps1
```

### The page looks stale

Force refresh:

```text
Ctrl+F5
```

### Signup fails because the email already exists

Use a fresh unique email such as:

```text
ui-demo-<timestamp>@library.local
```

If you want to reuse an existing account, switch to `Member Login`.

### Search returns no results

Use a known seeded value such as:

- `Clean Code`
- `978-0-13-468599-1`

### A social login button is visible but cannot be used

That provider is not configured in `.env` yet. Use email/password instead, or configure the provider credentials first.

### Branch details show IDs instead of friendly names

That is expected with the current inventory seed data. Use the branch IDs and the copy counts as the proof point.

### `demo-check.ps1` fails only on `CONFIG-SERVER` registration

The browser happy path may still work if gateway, auth, library, and inventory are healthy. Restart `discovery-server` and `config-server` before a formal architecture walkthrough that includes centralized configuration.
