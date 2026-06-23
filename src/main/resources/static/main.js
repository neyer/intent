import { BrowserStream } from './browser-stream.mjs';

const browserStream = BrowserStream.load();

// --- WebSocket connection management ---

let ws = null;
let currentFocusText = "";
let pendingAuthCapture = null; // { username, authToken } set when provide-user-token is in flight

function isResultError(result) {
    const lower = result.toLowerCase();
    return lower.startsWith("not authenticated") ||
           lower.startsWith("invalid credentials") ||
           lower.startsWith("error") ||
           lower.startsWith("usage:") ||
           lower.startsWith("no intent");
}

function connect() {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(protocol + "//" + location.host + "/ws");

    ws.onopen = function () {
        document.getElementById("result-text").textContent = "Connected";
        const creds = browserStream.getCurrentUser();
        if (creds) {
            ws.send(JSON.stringify({ type: "auth", username: creds.username, authToken: creds.authToken }));
        }
    };

    ws.onmessage = function (event) {
        const msg = JSON.parse(event.data);
        if (msg.type === "auth_result") {
            const el = document.getElementById("result-text");
            if (msg.success) {
                el.textContent = "Authenticated as " + msg.username;
                el.classList.remove("error");
            } else {
                el.textContent = msg.message || "Authentication failed";
                el.classList.add("error");
                browserStream.clearCurrentUser();
            }
            return;
        }
        if (msg.type === "scope") {
            if (msg.focus) currentFocusText = msg.focus.text || "";
            if (msg.result !== undefined) {
                const el = document.getElementById("result-text");
                el.textContent = msg.result;
                const isError = isResultError(msg.result);
                el.classList.toggle("error", isError);
                if (pendingAuthCapture) {
                    if (!isError && pendingAuthCapture.username) {
                        browserStream.setCurrentUser(pendingAuthCapture.username, pendingAuthCapture.authToken);
                    }
                    pendingAuthCapture = null;
                }
            }
            renderTree(msg);
        } else if (msg.type === "tree_update") {
            renderTree(msg);
        }
    };

    ws.onclose = function () {
        document.getElementById("result-text").textContent = "Disconnected - reconnecting...";
        setTimeout(connect, 2000);
    };

    ws.onerror = function () {
        ws.close();
    };
}

// --- Command submission ---

function submitCommand(command) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        document.getElementById("result-text").textContent = "Not connected";
        return;
    }
    const trimmed = command.trim();
    if (trimmed.startsWith("provide-user-token ")) {
        const token = trimmed.slice("provide-user-token ".length).trim();
        pendingAuthCapture = { username: currentFocusText, authToken: token };
    }
    ws.send(JSON.stringify({ command: command }));
}

// --- Intent tree renderer ---

function formatTimestamp(epochNanos) {
    if (!epochNanos) return "unknown time";
    const millis = Math.floor(epochNanos / 1000000);
    const d = new Date(millis);
    const yyyy = d.getFullYear();
    const MM = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const HH = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    return yyyy + "/" + MM + "/" + dd + " " + HH + ":" + mm;
}

function renderIntentRow(intent, prefix, cssClass) {
    const lines = [];
    const ts = formatTimestamp(intent.lastUpdatedTimestamp || intent.createdTimestamp);
    const typeLabel = intent.typeName ? " [" + intent.typeName + "]" : "";
    const row = document.createElement("div");
    row.className = "intent-row clickable " + cssClass;
    row.textContent = prefix + intent.id + typeLabel + " - " + intent.text + " (at " + ts + ")";
    row.addEventListener("click", function (e) {
        e.stopPropagation();
        submitCommand("focus " + intent.id);
    });
    lines.push(row);

    // Field values indented below the intent
    const fieldIndent = prefix + "    ";
    if (intent.fieldValues) {
        for (const [name, value] of Object.entries(intent.fieldValues)) {
            const fieldRow = document.createElement("div");
            fieldRow.className = "field-row";
            fieldRow.textContent = fieldIndent + name + ": " + value;
            lines.push(fieldRow);
        }
    }
    return lines;
}

function renderTree(msg) {
    const tree = document.getElementById("intent-tree");
    tree.innerHTML = "";

    // Ancestry paths - one chain per direct parent
    const paths = msg.ancestryPaths || (msg.ancestry ? [msg.ancestry] : [[]]);
    const multiPath = paths.length > 1;
    paths.forEach(function (path, pathIdx) {
        if (multiPath) {
            const sep = document.createElement("div");
            sep.className = "path-separator";
            sep.textContent = "--- Path " + (pathIdx + 1) + " ---";
            tree.appendChild(sep);
        }
        path.forEach(function (intent, i) {
            const prefix = " ".repeat(i);
            renderIntentRow(intent, prefix, "ancestor").forEach(function (el) {
                tree.appendChild(el);
            });
        });
    });

    // Spacer before focus
    const spacer1 = document.createElement("div");
    spacer1.className = "tree-spacer";
    tree.appendChild(spacer1);

    // Focus intent - no indentation
    if (msg.focus) {
        renderIntentRow(msg.focus, "", "focus").forEach(function (el) {
            tree.appendChild(el);
        });
    }

    // Spacer before children
    const spacer2 = document.createElement("div");
    spacer2.className = "tree-spacer";
    tree.appendChild(spacer2);

    // Children - indented with single space
    if (msg.children) {
        msg.children.forEach(function (intent) {
            renderIntentRow(intent, " ", "child").forEach(function (el) {
                tree.appendChild(el);
            });
        });
    }
}

// --- Autocomplete ---

let allCommands = [];
let commandArgTypes = {};
let activeIndex = -1;
let pendingSearchId = 0;

function hideDropdown() {
    document.getElementById("autocomplete-dropdown").style.display = "none";
    activeIndex = -1;
}

function showDropdownItems(items) {
    const dropdown = document.getElementById("autocomplete-dropdown");
    dropdown.innerHTML = "";
    activeIndex = -1;
    items.forEach(function (item) {
        const el = document.createElement("div");
        el.className = "autocomplete-item";
        el.textContent = item.label;
        el.addEventListener("mousedown", function (e) {
            e.preventDefault();
            item.onSelect();
        });
        dropdown.appendChild(el);
    });
    dropdown.style.display = items.length > 0 ? "block" : "none";
}

function highlightItem(index) {
    const items = document.querySelectorAll(".autocomplete-item");
    items.forEach(function (item, i) { item.classList.toggle("active", i === index); });
    activeIndex = index;
}

function updateDropdown(value) {
    const spaceIdx = value.indexOf(" ");

    // No space yet — suggest command keywords
    if (spaceIdx === -1) {
        if (value === "") { hideDropdown(); return; }
        const matches = allCommands.filter(function (cmd) { return cmd.startsWith(value); });
        showDropdownItems(matches.map(function (cmd) {
            return {
                label: cmd,
                onSelect: function () {
                    document.getElementById("command-input").value = cmd + " ";
                    hideDropdown();
                    document.getElementById("command-input").focus();
                }
            };
        }));
        return;
    }

    // Has space — check if we're in an id-type arg position
    const keyword = value.slice(0, spaceIdx);
    const argTypes = commandArgTypes[keyword];
    if (!argTypes || argTypes.length === 0) { hideDropdown(); return; }

    const parts = value.split(" ");
    const argIndex = parts.length - 2; // 0-based index into args (parts[0] is keyword)
    const typeIndex = Math.min(argIndex, argTypes.length - 1);
    if (argTypes[typeIndex] !== "id") { hideDropdown(); return; }

    const token = parts[parts.length - 1];
    if (token === "") { hideDropdown(); return; }

    // Search intents whose text contains the token
    const searchId = ++pendingSearchId;
    fetch("/api/search?q=" + encodeURIComponent(token))
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (pendingSearchId !== searchId) return; // stale
            const results = data.results || [];
            showDropdownItems(results.map(function (r) {
                return {
                    label: r.id + " — " + r.text,
                    onSelect: function () {
                        const input = document.getElementById("command-input");
                        const p = input.value.split(" ");
                        p[p.length - 1] = String(r.id);
                        input.value = p.join(" ") + " ";
                        hideDropdown();
                        input.focus();
                    }
                };
            }));
        });
}

// --- Keyboard handling ---

document.addEventListener("DOMContentLoaded", function () {
    const input = document.getElementById("command-input");
    const dropdown = document.getElementById("autocomplete-dropdown");

    input.addEventListener("keydown", function (e) {
        const items = dropdown.querySelectorAll(".autocomplete-item");
        const open = dropdown.style.display === "block" && items.length > 0;

        if (open) {
            if (e.key === "ArrowDown") {
                e.preventDefault();
                highlightItem((activeIndex + 1) % items.length);
                return;
            }
            if (e.key === "ArrowUp") {
                e.preventDefault();
                highlightItem((activeIndex - 1 + items.length) % items.length);
                return;
            }
            if (e.key === "Escape") {
                hideDropdown();
                return;
            }
            if ((e.key === "Tab" || e.key === "Enter") && activeIndex >= 0) {
                e.preventDefault();
                items[activeIndex].dispatchEvent(new MouseEvent("mousedown"));
                return;
            }
        }

        if (e.key === "Enter") {
            const command = input.value.trim();
            if (command) {
                hideDropdown();
                submitCommand(command);
                input.value = "";
            }
        }
    });

    input.addEventListener("input", function () {
        updateDropdown(input.value);
    });

    input.addEventListener("blur", function () {
        setTimeout(hideDropdown, 150);
    });

    // Keep focus on input
    document.addEventListener("click", function () {
        input.focus();
    });

    fetch("/api/commands")
        .then(function (r) { return r.json(); })
        .then(function (data) {
            allCommands = (data.commands || []).slice().sort();
            commandArgTypes = data.argTypes || {};
        })
        .catch(function (err) {
            document.getElementById("result-text").textContent = "Failed to load commands: " + err;
        });

    connect();
});
