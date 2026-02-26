const baseUrl = window.location.origin;
    const state = { mode: "login", token: null, session: null, authInfo: null };
    const authView = document.getElementById("auth-view");
    const appView = document.getElementById("app-view");
    const authNotice = document.getElementById("auth-notice");
    const searchNotice = document.getElementById("search-notice");
    const authForm = document.getElementById("auth-form");
    const searchForm = document.getElementById("search-form");
    const loginTab = document.getElementById("login-tab");
    const signupTab = document.getElementById("signup-tab");
    const nameWrap = document.getElementById("name-wrap");
    const fullNameInput = document.getElementById("full-name");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const authSubmit = document.getElementById("auth-submit");
    const authClear = document.getElementById("auth-clear");
    const oauthPanel = document.getElementById("oauth-panel");
    const oauthCaption = document.getElementById("oauth-caption");
    const oauthButtons = document.getElementById("oauth-buttons");
    const authStrategyNote = document.getElementById("auth-strategy-note");
    const passwordHelper = document.getElementById("password-helper");
    const logoutButton = document.getElementById("logout");
    const welcome = document.getElementById("welcome");
    const memberEmail = document.getElementById("member-email");
    const memberRole = document.getElementById("member-role");
    const tokenExpiry = document.getElementById("token-expiry");
    const sessionStatus = document.getElementById("session-status");
    const renewTokenButton = document.getElementById("renew-token");
    const librariesEl = document.getElementById("libraries");
    const searchQuery = document.getElementById("search-query");
    const searchButton = document.getElementById("search-button");
    const resultsSummary = document.getElementById("results-summary");
    const resultsEl = document.getElementById("results");
    let sessionTimer = null;

    const esc = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;").replaceAll("'", "&#39;");
    function notice(el, text, type = "") { el.className = `notice visible ${type}`.trim(); el.textContent = text; }
    function clearNotice(el) { el.className = "notice"; el.textContent = ""; }
    function setBusy(button, busy, busyText, idleText) { button.disabled = busy; button.textContent = busy ? busyText : idleText; }
    function wait(ms) { return new Promise((resolve) => window.setTimeout(resolve, ms)); }
    function isIsbnLikeQuery(value) { return /^[0-9-]+$/.test(value); }

    function setMode(mode) {
      state.mode = mode;
      const signup = mode === "signup";
      nameWrap.classList.toggle("hidden", !signup);
      loginTab.className = signup ? "tab-off" : "tab-on";
      signupTab.className = signup ? "tab-on" : "tab-off";
      authSubmit.textContent = signup ? "Create Account" : "Log In";
      passwordInput.autocomplete = signup ? "new-password" : "current-password";
      clearNotice(authNotice);
    }

    function parseFragmentParams() {
      const hash = window.location.hash.startsWith("#") ? window.location.hash.slice(1) : "";
      return hash ? new URLSearchParams(hash) : null;
    }

    function clearUrlFragment() {
      window.history.replaceState({}, document.title, window.location.pathname + window.location.search);
    }

    function providerLabel(provider) {
      switch (provider) {
        case "google": return "Google";
        case "github": return "GitHub";
        case "facebook": return "Facebook";
        default: return provider;
      }
    }

    function providerBadge(provider) {
      switch (provider) {
        case "google": return "G";
        case "github": return "GH";
        case "facebook": return "f";
        default: return "?";
      }
    }

    function providerConfigHint(provider) {
      switch (provider) {
        case "google": return "Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET";
        case "github": return "Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET";
        case "facebook": return "Set FACEBOOK_CLIENT_ID and FACEBOOK_CLIENT_SECRET";
        default: return "Provider setup required";
      }
    }

    function renderOAuthProviders(providers = []) {
      const configuredProviders = new Set(providers || []);
      const allProviders = ["google", "github", "facebook"];

      oauthButtons.innerHTML = allProviders.map((provider) => {
        const configured = configuredProviders.has(provider);
        const label = providerLabel(provider);
        const badge = providerBadge(provider);
        const state = configured ? "Ready to sign in" : "Not configured yet";

        if (configured) {
          return `
            <a class="oauth-button" href="${esc(`/auth/oauth2/authorization/${provider}`)}">
              <span class="oauth-badge">${esc(badge)}</span>
              <span class="oauth-label">
                <span class="oauth-name">${esc(label)}</span>
                <span class="oauth-state">${esc(state)}</span>
              </span>
            </a>
          `;
        }

        return `
          <button type="button" class="oauth-button disabled" data-provider="${esc(provider)}">
            <span class="oauth-badge">${esc(badge)}</span>
            <span class="oauth-label">
              <span class="oauth-name">${esc(label)}</span>
              <span class="oauth-state">${esc(state)}</span>
            </span>
          </button>
        `;
      }).join("");

      oauthCaption.textContent = configuredProviders.size
        ? "Configured providers are clickable. Social login still ends with the same 30 minute JWT session."
        : "All three login options are shown below, but none can be used yet until at least one provider client ID and secret is configured.";
    }

    function renderOAuthLoading() {
      const allProviders = ["google", "github", "facebook"];

      oauthButtons.innerHTML = allProviders.map((provider) => `
        <button type="button" class="oauth-button disabled" data-provider="${esc(provider)}">
          <span class="oauth-badge">${esc(providerBadge(provider))}</span>
          <span class="oauth-label">
            <span class="oauth-name">${esc(providerLabel(provider))}</span>
            <span class="oauth-state">Checking...</span>
          </span>
        </button>
      `).join("");

      oauthCaption.textContent = "Checking which social providers are configured...";
    }

    async function loadAuthInfo() {
      const attempts = 8;

      for (let attempt = 1; attempt <= attempts; attempt += 1) {
        try {
          const info = await api("/auth/info");
          state.authInfo = info;
          if (info && info.note) passwordHelper.textContent = info.note.includes("same JWT")
            ? "Local passwords are stored as BCrypt hashes. Social login still ends with the same member JWT session and does not store the provider password in this database."
            : passwordHelper.textContent;
          renderOAuthProviders(info && Array.isArray(info.oauth_providers) ? info.oauth_providers : []);
          return;
        } catch {
          if (attempt === attempts) {
            renderOAuthProviders([]);
            return;
          }
          await wait(1500);
        }
      }
    }

    function sessionFromFragment(params) {
      return {
        access_token: params.get("access_token") || "",
        token_type: params.get("token_type") || "Bearer",
        expires_in: Number(params.get("expires_in") || 0),
        username: params.get("username") || "",
        email: params.get("email") || "",
        full_name: params.get("full_name") || "",
        role: params.get("role") || "USER",
        roles: (params.get("roles") || "").split(",").filter(Boolean)
      };
    }

    async function handleOAuthCallback() {
      const params = parseFragmentParams();
      if (!params) return false;

      if (params.has("oauth_error")) {
        const message = params.get("oauth_error") || "Provider login failed.";
        clearUrlFragment();
        showAuth();
        notice(authNotice, message, "error");
        return true;
      }

      if (!params.has("access_token")) {
        return false;
      }

      const session = sessionFromFragment(params);
      clearUrlFragment();
      await enterApp(session);
      return true;
    }

    async function api(path, options = {}, needsAuth = false) {
      const headers = { Accept: "application/json", ...(options.headers || {}) };
      if (options.body !== undefined && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
      if (needsAuth && state.token) headers.Authorization = "Bearer " + state.token;
      const response = await fetch(baseUrl + path, { ...options, headers });
      const raw = await response.text();
      let payload = null;
      try { payload = raw ? JSON.parse(raw) : null; } catch { payload = raw; }
      if (!response.ok) {
        const message = typeof payload === "object" && payload !== null
          ? payload.message || (payload.fieldErrors ? Object.values(payload.fieldErrors)[0] : null) || "Request failed"
          : payload || "Request failed";
        const error = new Error(message);
        error.status = response.status;
        throw error;
      }
      return payload;
    }

    function persistSession(session) {
      state.token = session.access_token;
      state.session = session;
      localStorage.setItem("library_member_token", session.access_token);
      localStorage.setItem("library_member_session", JSON.stringify(session));
    }

    function clearSession() {
      state.token = null;
      state.session = null;
      localStorage.removeItem("library_member_token");
      localStorage.removeItem("library_member_session");
      stopSessionTimer();
    }

    function showAuth() { appView.classList.remove("visible"); authView.classList.remove("hidden"); }
    function showApp() { authView.classList.add("hidden"); appView.classList.add("visible"); }

    function updateHeader() {
      const session = state.session || {};
      welcome.textContent = `Welcome, ${session.full_name || session.fullName || "Member"}.`;
      memberEmail.textContent = session.email || "Unknown member";
      memberRole.textContent = session.role || "USER";
      updateTokenExpiry(session.access_token || state.token);
    }

    function parseJwtPayload(token) {
      if (!token) return null;
      try {
        const payload = token.split(".")[1];
        if (!payload) return null;
        const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
        const padded = normalized.padEnd(normalized.length + (4 - normalized.length % 4) % 4, "=");
        return JSON.parse(atob(padded));
      } catch {
        return null;
      }
    }

    function formatCountdown(milliseconds) {
      if (!Number.isFinite(milliseconds)) {
        return { label: "Unavailable", expired: false };
      }
      if (milliseconds <= 0) {
        return { label: "00:00:00", expired: true };
      }

      const totalSeconds = Math.floor(milliseconds / 1000);
      const hours = Math.floor(totalSeconds / 3600);
      const minutes = Math.floor((totalSeconds % 3600) / 60);
      const seconds = totalSeconds % 60;

      return {
        label: [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":"),
        expired: false
      };
    }

    function startSessionTimer() {
      stopSessionTimer();
      sessionTimer = window.setInterval(updateSessionTiming, 1000);
    }

    function stopSessionTimer() {
      if (sessionTimer !== null) {
        window.clearInterval(sessionTimer);
        sessionTimer = null;
      }
    }

    function updateSessionTiming() {
      const payload = parseJwtPayload(state.token);
      const expiry = payload && payload.exp ? new Date(payload.exp * 1000) : null;
      const renewed = Boolean(payload && payload.renewed);
      const millisRemaining = expiry instanceof Date ? expiry.getTime() - Date.now() : Number.NaN;
      const formatted = formatCountdown(millisRemaining);

      tokenExpiry.textContent = formatted.label;

      if (!(expiry instanceof Date) || Number.isNaN(expiry.getTime())) {
        renewTokenButton.classList.add("hidden");
        sessionStatus.textContent = "Token expiry could not be read from the JWT.";
        return;
      }

      if (formatted.expired) {
        logout("Your session has expired. Please log in again.", "error");
        return;
      }

      if (renewed) {
        renewTokenButton.classList.add("hidden");
        sessionStatus.textContent = "Session time already extended once.";
        return;
      }

      if (millisRemaining <= 5 * 60 * 1000) {
        renewTokenButton.classList.remove("hidden");
        sessionStatus.textContent = "Less than 5 minutes left. You can extend this session once.";
        return;
      }

      renewTokenButton.classList.add("hidden");
      sessionStatus.textContent = "Tokens are valid for 30 minutes and can be extended once in the last 5 minutes.";
    }

    function updateTokenExpiry(token) {
      state.token = token || state.token;
      updateSessionTiming();
      startSessionTimer();
    }

    async function renewToken() {
      clearNotice(searchNotice);
      setBusy(renewTokenButton, true, "Renewing...", "Extend Time");
      try {
        const session = await api("/auth/renew", { method: "POST" }, true);
        persistSession(session);
        updateHeader();
        sessionStatus.textContent = "Session time extended. It cannot be extended again.";
      } catch (error) {
        sessionStatus.textContent = error.message || "Token renewal failed.";
      } finally {
        setBusy(renewTokenButton, false, "Renewing...", "Extend Time");
      }
    }

    function wireExclusiveAccordions(root, selector) {
      const folds = Array.from(root.querySelectorAll(selector));
      folds.forEach((fold) => {
        fold.addEventListener("toggle", () => {
          if (!fold.open) return;
          folds.forEach((other) => {
            if (other !== fold) other.open = false;
          });
        });
      });
    }

    function renderLibraryBooks(books) {
      if (!books.length) {
        return '<div class="library-note">No books are currently linked to this library record.</div>';
      }

      return `
        <div class="book-shelf">
          ${books.map((book) => `
            <article class="book-entry">
              <h4>${esc(book.title)}</h4>
              <p>${esc(book.author || "Unknown author")}</p>
              <div class="book-meta">
                <span>${esc(book.genre || "General collection")}</span>
                <span class="book-dot">•</span>
                <span>${esc(book.publicationYear || "Year unknown")}</span>
                <span class="book-dot">•</span>
                <span>ISBN ${esc(book.isbn)}</span>
              </div>
            </article>
          `).join("")}
        </div>
      `;
    }

    function renderLibraries(libraries, booksByLibrary) {
      if (!libraries.length) {
        librariesEl.innerHTML = '<div class="empty">No libraries were returned by the protected endpoint.</div>';
        return;
      }
      librariesEl.innerHTML = libraries.map((library) => {
        const books = [...(booksByLibrary.get(library.id) || [])]
          .sort((left, right) => String(left.title || "").localeCompare(String(right.title || "")));
        const indexedCount = books.length || library.bookCount;

        return `
        <details class="library-fold">
          <summary class="library-summary">
            <div class="line-grid">
              <div class="library-kicker">
                <span class="pill">Library ${esc(library.id)}</span>
                <span class="helper">${esc(library.city)}</span>
              </div>
              <h3>${esc(library.name)}</h3>
              <p>${esc(library.address)}</p>
            </div>
            <div class="library-summary-side">
              <div class="value">${esc(indexedCount)}</div>
              <div class="helper">books indexed</div>
              <span class="library-chevron">▾</span>
            </div>
          </summary>
          <div class="library-panel">
            <div class="library-facts">
              <span><strong>Books</strong>${esc(indexedCount)}</span>
              <span class="library-fact-dot">•</span>
              <span><strong>City</strong>${esc(library.city)}</span>
              <span class="library-fact-dot">•</span>
              <span><strong>Address</strong>${esc(library.address)}</span>
            </div>
            <div class="library-note">
              Browse the books held by this library directly here, then use search to inspect availability and branch-level stock.
            </div>
            ${renderLibraryBooks(books)}
          </div>
        </details>
      `;
      }).join("");
      wireExclusiveAccordions(librariesEl, ".library-fold");
    }

    async function loadLibraries() {
      const [libraries, books] = await Promise.all([
        api("/api/libraries", {}, true),
        api("/api/books", {}, true)
      ]);

      const booksByLibrary = new Map();
      (books || []).forEach((book) => {
        if (!book || book.libraryId == null) return;
        const bucket = booksByLibrary.get(book.libraryId) || [];
        bucket.push(book);
        booksByLibrary.set(book.libraryId, bucket);
      });

      renderLibraries(libraries || [], booksByLibrary);
    }

    function availabilityLabel(item) {
      if (!item || !item.inventory) return "Status unknown";
      return item.inventory.available ? "Available now" : "Currently unavailable";
    }

    function availabilityText(item) {
      if (!item || !item.inventory) return "Availability could not be loaded for this title.";
      const inv = item.inventory;
      if (inv.branchCount === 0) return "No inventory record was found for this title yet.";
      if (inv.available) return `${inv.availableCopies} copies available across ${inv.branchCount} branches.`;
      return `This title is currently not available. ${inv.reservedCopies} copies are reserved.`;
    }

    function holderId(isbn) { return "branches-" + String(isbn).replace(/[^a-zA-Z0-9]/g, "-"); }

    function renderResults(items, query) {
      resultsSummary.textContent = `Showing ${items.length} result${items.length === 1 ? "" : "s"} for "${query}".`;
      if (!items.length) {
        resultsEl.innerHTML = '<div class="empty">No books matched that prefix. Try another title, author, genre, or ISBN.</div>';
        return;
      }
      resultsEl.innerHTML = items.map(({ book, availability }) => {
        const id = holderId(book.isbn);
        const inv = availability ? availability.inventory : null;
        const homeLibrary = (availability && availability.libraryName) || book.libraryName || "Unknown";
        const sideValue = inv ? `${inv.availableCopies}/${inv.totalCopies}` : "n/a";
        const sideCaption = inv ? "available / total" : "availability pending";
        return `
          <details class="result-fold">
            <summary class="result-summary">
              <div class="line-grid">
                <div class="result-kicker">
                  <span class="pill">${esc(book.genre || "Book")}</span>
                  <span class="status ${inv && inv.available ? "ok" : ""}">${availabilityLabel(availability)}</span>
                </div>
                <h3>${esc(book.title)}</h3>
                <p>${esc(book.author)} · ISBN ${esc(book.isbn)}</p>
                <div class="result-support">
                  <p><strong>Home library:</strong> ${esc(homeLibrary)}</p>
                </div>
              </div>
              <div class="result-summary-side">
                <div class="value">${esc(sideValue)}</div>
                <div class="helper">${esc(sideCaption)}</div>
                <span class="result-chevron">▾</span>
              </div>
            </summary>
            <div class="result-panel">
              <p>${esc(availabilityText(availability))}</p>
              <div class="stats">
                <div class="chip inline-chip"><strong>Total</strong><span class="chip-value">${esc(inv ? inv.totalCopies : "n/a")}</span></div>
                <div class="chip inline-chip"><strong>Available</strong><span class="chip-value">${esc(inv ? inv.availableCopies : "n/a")}</span></div>
              </div>
              <p class="helper"><strong>Branches checked:</strong> ${esc(inv ? inv.branchCount : "n/a")}</p>
              <div>
                <button type="button" class="ghost small branch-toggle" data-isbn="${esc(book.isbn)}" data-target="${esc(id)}">Show Branches</button>
              </div>
              <div id="${esc(id)}" class="branch-holder"></div>
            </div>
          </details>
        `;
      }).join("");
      wireExclusiveAccordions(resultsEl, ".result-fold");
    }

    async function loadBranches(isbn, id, button) {
      const holder = document.getElementById(id);
      if (!holder) return;
      if (holder.classList.contains("visible")) {
        holder.classList.remove("visible");
        holder.innerHTML = "";
        button.textContent = "Show Branches";
        return;
      }
      button.disabled = true;
      button.textContent = "Loading...";
      try {
        const branches = await api(`/api/inventory/${encodeURIComponent(isbn)}/branches`, {}, true);
        holder.innerHTML = !branches.length
          ? '<div class="empty" style="margin-top:12px;">No branch detail is available for this title.</div>'
          : `<div class="branches">${branches.map((branch) => `
              <div class="branch-card">
                <div class="line-grid">
                  <span class="status ${branch.available ? "ok" : ""}">${branch.available ? "In stock" : "Unavailable"}</span>
                  <h3>${esc(branch.branchName)}</h3>
                  <p>${esc(branch.branchId)}</p>
                </div>
                <div class="stats">
                  <div class="chip inline-chip"><strong>Total</strong><span class="chip-value">${esc(branch.totalCopies)}</span></div>
                  <div class="chip inline-chip"><strong>Available</strong><span class="chip-value">${esc(branch.availableCopies)}</span></div>
                </div>
              </div>
            `).join("")}</div>`;
        holder.classList.add("visible");
        button.textContent = "Hide Branches";
      } catch (error) {
        holder.innerHTML = `<div class="empty" style="margin-top:12px;">${esc(error.message || "Could not load branch data.")}</div>`;
        holder.classList.add("visible");
        button.textContent = "Hide Branches";
      } finally {
        button.disabled = false;
      }
    }

    async function searchBooks() {
      const query = searchQuery.value.trim();
      clearNotice(searchNotice);
      if (!query) {
        notice(searchNotice, "Enter a title, author, genre, or ISBN prefix.");
        return;
      }
      if (query.length < 2 && !isIsbnLikeQuery(query)) {
        notice(searchNotice, "Use at least 2 characters for title, author, or genre prefix searches.");
        return;
      }
      setBusy(searchButton, true, "Searching...", "Search Books");
      resultsSummary.textContent = "Checking books and availability...";
      resultsEl.innerHTML = '<div class="empty">Searching protected book endpoints through the gateway...</div>';
      try {
        const books = await api(`/api/books/search?q=${encodeURIComponent(query)}`, {}, true);
        const items = await Promise.all((books || []).map(async (book) => {
          try { return { book, availability: await api(`/api/books/${book.id}/availability`, {}, true) }; }
          catch { return { book, availability: null }; }
        }));
        renderResults(items, query);
      } catch (error) {
        resultsSummary.textContent = "Search could not be completed.";
        resultsEl.innerHTML = `<div class="empty">${esc(error.message || "Book search failed.")}</div>`;
        notice(searchNotice, error.message || "Book search failed.", "error");
      } finally {
        setBusy(searchButton, false, "Searching...", "Search Books");
      }
    }

    async function enterApp(session) {
      persistSession(session);
      showApp();
      updateHeader();
      try { await loadLibraries(); }
      catch (error) {
        clearSession();
        showAuth();
        notice(authNotice, error.message || "Your session is no longer valid. Please log in again.", "error");
        return;
      }
      clearNotice(authNotice);
      searchQuery.focus();
    }

    async function handleAuth(event) {
      event.preventDefault();
      clearNotice(authNotice);
      const payload = { email: emailInput.value.trim(), password: passwordInput.value };
      if (state.mode === "signup") payload.fullName = fullNameInput.value.trim();
      const path = state.mode === "signup" ? "/auth/signup" : "/auth/login";
      const busy = state.mode === "signup" ? "Creating..." : "Logging in...";
      const idle = state.mode === "signup" ? "Create Account" : "Log In";
      setBusy(authSubmit, true, busy, idle);
      try {
        const session = await api(path, { method: "POST", body: JSON.stringify(payload) });
        notice(authNotice, state.mode === "signup" ? "Account created. Loading your dashboard..." : "Login successful. Loading your dashboard...", "success");
        await enterApp(session);
      } catch (error) {
        notice(authNotice, error.message || "Authentication failed.", "error");
      } finally {
        setBusy(authSubmit, false, busy, idle);
      }
    }

    function logout(message = "You have been logged out.", type = "success") {
      if (message instanceof Event) {
        message = "You have been logged out.";
        type = "success";
      }
      clearSession();
      librariesEl.innerHTML = "";
      searchQuery.value = "";
      memberEmail.textContent = "No member";
      memberRole.textContent = "USER";
      tokenExpiry.textContent = "00:00:00";
      sessionStatus.textContent = "Tokens are valid for 30 minutes and can be extended once in the last 5 minutes.";
      renewTokenButton.classList.add("hidden");
      resultsSummary.textContent = "Search for a book to see where it is held and whether it is currently available.";
      resultsEl.innerHTML = '<div class="empty">Start with a search term. Results will show the title, library, availability, and branch detail on demand.</div>';
      showAuth();
      notice(authNotice, message, type);
    }

    async function restoreSession() {
      const token = localStorage.getItem("library_member_token");
      const raw = localStorage.getItem("library_member_session");
      if (!token || !raw) { showAuth(); return; }
      try {
        state.token = token;
        state.session = JSON.parse(raw);
        showApp();
        updateHeader();
        await loadLibraries();
      } catch {
        clearSession();
        showAuth();
      }
    }

    loginTab.addEventListener("click", () => setMode("login"));
    signupTab.addEventListener("click", () => setMode("signup"));
    authForm.addEventListener("submit", handleAuth);
    authClear.addEventListener("click", () => { authForm.reset(); clearNotice(authNotice); });
    searchForm.addEventListener("submit", (event) => { event.preventDefault(); searchBooks(); });
    logoutButton.addEventListener("click", () => logout());
    renewTokenButton.addEventListener("click", renewToken);
    oauthButtons.addEventListener("click", (event) => {
      const button = event.target.closest("button[data-provider]");
      if (!button) return;
      notice(authNotice, `${providerLabel(button.dataset.provider)} login is not configured yet. ${providerConfigHint(button.dataset.provider)}.`, "error");
    });
    resultsEl.addEventListener("click", (event) => {
      const button = event.target.closest(".branch-toggle");
      if (button) loadBranches(button.dataset.isbn, button.dataset.target, button);
    });

    setMode("login");
    renderOAuthLoading();
    loadAuthInfo();
    handleOAuthCallback().then((handled) => {
      if (!handled) restoreSession();
    });
