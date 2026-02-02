package com.intentevolved.visualizer

import com.intentevolved.com.intentevolved.IntentServiceImpl
import java.io.File

/**
 * Timeline Visualizer - Generates an HTML timeline from an intent protocol file.
 */

data class TimelineEntry(
    val id: Long,
    val text: String,
    val parentId: Long?,
    val startTime: Long?,
    val endTime: Long?,
    val duration: Long?,
    val done: Boolean,
    val depth: Int = 0
)

fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args) ?: run {
        printUsage()
        System.exit(1)
        return
    }

    println("Reading ${parsedArgs.inputFile}...")

    val entries = readAndExtractTimeline(parsedArgs.inputFile)
    if (entries.isEmpty()) {
        System.err.println("No intents found in file")
        System.exit(1)
        return
    }

    println("Found ${entries.size} intents, generating timeline...")

    val html = generateHtml(entries, parsedArgs.inputFile)

    File(parsedArgs.outputFile).writeText(html)
    println("Written to ${parsedArgs.outputFile}")

    if (parsedArgs.openBrowser) {
        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("linux") -> "xdg-open"
            os.contains("mac") -> "open"
            os.contains("win") -> "start"
            else -> null
        }
        cmd?.let {
            ProcessBuilder(it, parsedArgs.outputFile).start()
        }
    }
}

data class VisualizerArgs(
    val inputFile: String,
    val outputFile: String,
    val openBrowser: Boolean
)

fun parseArgs(args: Array<String>): VisualizerArgs? {
    var inputFile: String? = null
    var outputFile: String? = null
    var openBrowser = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--open", "-o" -> openBrowser = true
            "--output" -> {
                if (i + 1 >= args.size) return null
                outputFile = args[++i]
            }
            "--help", "-h" -> return null
            else -> {
                if (args[i].startsWith("-")) {
                    System.err.println("Unknown option: ${args[i]}")
                    return null
                }
                inputFile = args[i]
            }
        }
        i++
    }

    if (inputFile == null) return null

    if (!File(inputFile).exists()) {
        System.err.println("File not found: $inputFile")
        return null
    }

    if (outputFile == null) {
        outputFile = inputFile.removeSuffix(".pb") + ".html"
    }

    return VisualizerArgs(inputFile, outputFile, openBrowser)
}

fun printUsage() {
    println("""
        Timeline Visualizer - Generates an HTML timeline from an intent protocol file

        Usage: TimelineVisualizer <input.pb> [--output <output.html>] [--open]

        Arguments:
          <input.pb>              Input intent protocol binary file
          --output <file>         Output HTML file (default: input.html)
          --open, -o              Open in browser after generation
          --help, -h              Show this help

        Example:
          TimelineVisualizer claude_worker.pb --open
    """.trimIndent())
}

fun readAndExtractTimeline(filename: String): List<TimelineEntry> {
    val service = IntentServiceImpl.fromFile(filename)

    val entries = mutableListOf<TimelineEntry>()

    // Get all intents by traversing from root
    fun traverse(id: Long, depth: Int) {
        val scope = service.getFocalScope(id)

        if (id > 0) {
            val intent = scope.focus
            val values = intent.fieldValues()
            val startTime = values["started"] as? Long
            val endTime = (values["completed"] ?: values["finished"]) as? Long
            val done = values["done"] as? Boolean ?: false

            entries.add(TimelineEntry(
                id = intent.id(),
                text = intent.text(),
                parentId = intent.parent()?.id(),
                startTime = startTime,
                endTime = endTime,
                duration = if (startTime != null && endTime != null) endTime - startTime else null,
                done = done,
                depth = depth
            ))
        }

        scope.children.forEach { child ->
            traverse(child.id(), depth + 1)
        }
    }

    traverse(0, 0)
    return entries
}

fun generateHtml(entries: List<TimelineEntry>, sourceFile: String): String {
    val entriesWithTime = entries.filter { it.startTime != null }
    val minTime = entriesWithTime.minOfOrNull { it.startTime!! } ?: 0L
    val maxTime = entriesWithTime.maxOfOrNull { it.endTime ?: it.startTime!! } ?: 0L
    val totalDuration = maxTime - minTime

    val completedCount = entries.count { it.done }
    val totalCount = entries.size

    // Convert to JSON
    val jsonEntries = entries.map { e ->
        val relStart = e.startTime?.let { it - minTime }
        val relEnd = e.endTime?.let { it - minTime }
        """{"id":${e.id},"text":"${e.text.replace("\"", "\\\"").replace("\n", " ")}","parentId":${e.parentId ?: "null"},"startTime":${relStart ?: "null"},"endTime":${relEnd ?: "null"},"duration":${e.duration ?: "null"},"done":${e.done},"depth":${e.depth}}"""
    }.joinToString(",\n    ")

    return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Timeline: $sourceFile</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a2e; color: #eee;
        }
        .container { display: flex; height: 100vh; }
        .sidebar {
            width: 350px; background: #16213e; padding: 20px;
            overflow-y: auto; border-right: 1px solid #0f3460;
        }
        .main { flex: 1; padding: 20px; overflow: auto; }
        h1 { font-size: 1.2em; margin-bottom: 20px; color: #e94560; }
        .stats {
            background: #0f3460; padding: 15px; border-radius: 8px;
            margin-bottom: 20px; font-size: 0.9em;
        }
        .stats div { margin: 5px 0; }
        .stats span { color: #e94560; font-weight: bold; }
        .tree-item {
            padding: 6px 8px; margin: 2px 0; border-radius: 4px;
            cursor: pointer; font-size: 0.85em; white-space: nowrap;
            overflow: hidden; text-overflow: ellipsis;
        }
        .tree-item:hover { background: #0f3460; }
        .tree-item.done { border-left: 3px solid #00d9a5; }
        .tree-item.pending { border-left: 3px solid #666; }
        .timeline { position: relative; min-height: 400px; }
        .time-axis {
            height: 30px; background: #0f3460; margin-bottom: 10px;
            border-radius: 4px; position: relative;
        }
        .time-tick {
            position: absolute; top: 0; height: 100%;
            border-left: 1px solid #333; padding-left: 5px;
            font-size: 0.75em; color: #888;
        }
        .task-row {
            display: flex; align-items: center; margin: 4px 0;
            height: 28px;
        }
        .task-label {
            width: 250px; font-size: 0.8em; padding-right: 10px;
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
        }
        .task-bar-container { flex: 1; position: relative; height: 100%; }
        .task-bar {
            position: absolute; height: 20px; top: 4px;
            border-radius: 4px; min-width: 4px;
            display: flex; align-items: center; padding: 0 6px;
            font-size: 0.7em; color: #fff; cursor: pointer;
            transition: transform 0.1s, box-shadow 0.1s;
        }
        .task-bar:hover {
            transform: scaleY(1.2);
            box-shadow: 0 2px 8px rgba(0,0,0,0.5);
            z-index: 10;
        }
        .task-bar.done { background: linear-gradient(135deg, #00d9a5, #00a67d); }
        .task-bar.pending { background: linear-gradient(135deg, #666, #444); }
        .task-bar.no-time { background: #333; width: 100%; opacity: 0.3; }
        .tooltip {
            position: fixed; background: #16213e; border: 1px solid #0f3460;
            padding: 12px; border-radius: 8px; font-size: 0.85em;
            max-width: 400px; z-index: 100; display: none;
            box-shadow: 0 4px 12px rgba(0,0,0,0.5);
        }
        .tooltip h3 { color: #e94560; margin-bottom: 8px; }
        .tooltip .detail { margin: 4px 0; color: #aaa; }
        .tooltip .detail span { color: #fff; }
        .zoom-controls { margin-bottom: 15px; }
        .zoom-controls button {
            background: #0f3460; border: none; color: #fff;
            padding: 8px 16px; margin-right: 8px; border-radius: 4px;
            cursor: pointer;
        }
        .zoom-controls button:hover { background: #e94560; }
    </style>
</head>
<body>
    <div class="container">
        <div class="sidebar">
            <h1>Intent Timeline</h1>
            <div class="stats">
                <div>Source: <span>$sourceFile</span></div>
                <div>Total tasks: <span>$totalCount</span></div>
                <div>Completed: <span>$completedCount</span></div>
                <div>Total time: <span id="total-time"></span></div>
            </div>
            <div id="tree"></div>
        </div>
        <div class="main">
            <div class="zoom-controls">
                <button onclick="zoomIn()">Zoom In</button>
                <button onclick="zoomOut()">Zoom Out</button>
                <button onclick="resetZoom()">Reset</button>
            </div>
            <div class="time-axis" id="time-axis"></div>
            <div class="timeline" id="timeline"></div>
        </div>
    </div>
    <div class="tooltip" id="tooltip"></div>

    <script>
    const entries = [
    $jsonEntries
    ];

    const totalDuration = $totalDuration;
    let zoomLevel = 1;
    let panOffset = 0;

    function formatDuration(nanos) {
        if (nanos == null) return 'N/A';
        const ms = nanos / 1000000;
        if (ms < 1000) return ms.toFixed(1) + ' ms';
        const sec = ms / 1000;
        if (sec < 60) return sec.toFixed(2) + ' s';
        const min = sec / 60;
        return min.toFixed(2) + ' min';
    }

    function formatTime(nanos) {
        if (nanos == null) return 'N/A';
        const ms = nanos / 1000000;
        return ms.toFixed(0) + ' ms';
    }

    document.getElementById('total-time').textContent = formatDuration(totalDuration);

    function renderTree() {
        const tree = document.getElementById('tree');
        tree.innerHTML = entries.map(e =>
            '<div class="tree-item ' + (e.done ? 'done' : 'pending') + '" ' +
            'style="padding-left: ' + (e.depth * 16 + 8) + 'px" ' +
            'onclick="scrollToTask(' + e.id + ')" ' +
            'title="' + e.text + '">' +
            e.id + '. ' + e.text + '</div>'
        ).join('');
    }

    function renderTimeline() {
        const timeline = document.getElementById('timeline');
        const timeAxis = document.getElementById('time-axis');
        const width = timeline.clientWidth - 260;
        const scale = width / (totalDuration / zoomLevel);

        // Time axis
        const tickCount = 10;
        const tickInterval = totalDuration / zoomLevel / tickCount;
        let axisHtml = '';
        for (let i = 0; i <= tickCount; i++) {
            const time = i * tickInterval;
            const left = (time * scale);
            axisHtml += '<div class="time-tick" style="left: ' + left + 'px">' + formatDuration(time) + '</div>';
        }
        timeAxis.innerHTML = axisHtml;

        // Task bars
        let html = '';
        entries.forEach((e, idx) => {
            const label = e.id + '. ' + e.text.substring(0, 40) + (e.text.length > 40 ? '...' : '');
            let barHtml;

            if (e.startTime != null) {
                const left = e.startTime * scale;
                const barWidth = Math.max(4, (e.duration || 1000000) * scale);
                barHtml = '<div class="task-bar ' + (e.done ? 'done' : 'pending') + '" ' +
                    'style="left: ' + left + 'px; width: ' + barWidth + 'px" ' +
                    'data-idx="' + idx + '" ' +
                    'onmouseenter="showTooltip(event, ' + idx + ')" ' +
                    'onmouseleave="hideTooltip()">' +
                    (idx + 1) + '</div>';
            } else {
                barHtml = '<div class="task-bar no-time">Not executed</div>';
            }

            html += '<div class="task-row" id="task-' + e.id + '">' +
                '<div class="task-label" title="' + e.text + '">' + label + '</div>' +
                '<div class="task-bar-container">' + barHtml + '</div></div>';
        });
        timeline.innerHTML = html;
    }

    function showTooltip(event, idx) {
        const e = entries[idx];
        const tooltip = document.getElementById('tooltip');
        tooltip.innerHTML = '<h3>' + e.text + '</h3>' +
            '<div class="detail">ID: <span>' + e.id + '</span></div>' +
            '<div class="detail">Status: <span>' + (e.done ? 'Completed' : 'Pending') + '</span></div>' +
            '<div class="detail">Start: <span>' + formatTime(e.startTime) + '</span></div>' +
            '<div class="detail">End: <span>' + formatTime(e.endTime) + '</span></div>' +
            '<div class="detail">Duration: <span>' + formatDuration(e.duration) + '</span></div>';
        tooltip.style.display = 'block';
        tooltip.style.left = (event.pageX + 10) + 'px';
        tooltip.style.top = (event.pageY + 10) + 'px';
    }

    function hideTooltip() {
        document.getElementById('tooltip').style.display = 'none';
    }

    function scrollToTask(id) {
        document.getElementById('task-' + id)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    function zoomIn() { zoomLevel *= 1.5; renderTimeline(); }
    function zoomOut() { zoomLevel /= 1.5; renderTimeline(); }
    function resetZoom() { zoomLevel = 1; renderTimeline(); }

    renderTree();
    renderTimeline();
    window.addEventListener('resize', renderTimeline);
    </script>
</body>
</html>"""
}
