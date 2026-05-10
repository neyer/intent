// --- WebSocket connection management ---

let ws = null;

function connect() {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    ws = new WebSocket(protocol + "//" + location.host + "/ws");

    ws.onopen = function () {
        document.getElementById("result-text").textContent = "Connected";
    };

    ws.onmessage = function (event) {
        const msg = JSON.parse(event.data);
        if (msg.type === "scope") {
            if (msg.result !== undefined) {
                const el = document.getElementById("result-text");
                el.textContent = msg.result;
                const isError = msg.result.toLowerCase().startsWith("not authenticated") ||
                                msg.result.toLowerCase().startsWith("invalid credentials") ||
                                msg.result.toLowerCase().startsWith("error");
                el.classList.toggle("error", isError);
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
let activeIndex = -1;

function updateDropdown(value) {
    const dropdown = document.getElementById("autocomplete-dropdown");
    if (value === "" || value.includes(" ")) {
        dropdown.style.display = "none";
        return;
    }
    const matches = allCommands.filter(function (cmd) { return cmd.startsWith(value); });
    if (matches.length === 0) {
        dropdown.style.display = "none";
        return;
    }
    dropdown.innerHTML = "";
    activeIndex = -1;
    matches.forEach(function (cmd) {
        const item = document.createElement("div");
        item.className = "autocomplete-item";
        item.textContent = cmd;
        item.addEventListener("mousedown", function (e) {
            e.preventDefault();
            selectSuggestion(cmd);
        });
        dropdown.appendChild(item);
    });
    dropdown.style.display = "block";
}

function highlightItem(index) {
    const items = document.querySelectorAll(".autocomplete-item");
    items.forEach(function (item, i) { item.classList.toggle("active", i === index); });
    activeIndex = index;
}

function selectSuggestion(cmd) {
    const input = document.getElementById("command-input");
    input.value = cmd + " ";
    document.getElementById("autocomplete-dropdown").style.display = "none";
    activeIndex = -1;
    input.focus();
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
                dropdown.style.display = "none";
                activeIndex = -1;
                return;
            }
            if ((e.key === "Tab") && activeIndex >= 0) {
                e.preventDefault();
                selectSuggestion(items[activeIndex].textContent);
                return;
            }
            if (e.key === "Enter" && activeIndex >= 0) {
                e.preventDefault();
                selectSuggestion(items[activeIndex].textContent);
                return;
            }
        }

        if (e.key === "Enter") {
            const command = input.value.trim();
            if (command) {
                dropdown.style.display = "none";
                submitCommand(command);
                input.value = "";
            }
        }
    });

    input.addEventListener("input", function () {
        updateDropdown(input.value);
    });

    input.addEventListener("blur", function () {
        setTimeout(function () { dropdown.style.display = "none"; }, 150);
    });

    // Keep focus on input
    document.addEventListener("click", function () {
        input.focus();
    });

    fetch("/api/commands")
        .then(function (r) { return r.json(); })
        .then(function (data) {
            allCommands = (data.commands || []).slice().sort();
        });

    connect();
});
