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
                document.getElementById("result-text").textContent = msg.result;
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
    const row = document.createElement("div");
    row.className = "intent-row clickable " + cssClass;
    row.textContent = prefix + intent.id + " - " + intent.text + " (at " + ts + ")";
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

    // Ancestry - each ancestor indented by its depth
    if (msg.ancestry) {
        msg.ancestry.forEach(function (intent, i) {
            const prefix = " ".repeat(i);
            renderIntentRow(intent, prefix, "ancestor").forEach(function (el) {
                tree.appendChild(el);
            });
        });
    }

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

// --- Keyboard handling ---

document.addEventListener("DOMContentLoaded", function () {
    const input = document.getElementById("command-input");

    input.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            const command = input.value.trim();
            if (command) {
                submitCommand(command);
                input.value = "";
            }
        }
    });

    // Keep focus on input
    document.addEventListener("click", function () {
        input.focus();
    });

    connect();
});
