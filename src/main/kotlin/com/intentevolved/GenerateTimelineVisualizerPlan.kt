package com.intentevolved

import com.intentevolved.com.intentevolved.IntentServiceImpl

/**
 * Generates the timeline visualizer implementation plan as an intent stream file.
 */
fun main() {
    val service = IntentServiceImpl.new("Build a timeline visualizer that reads intent protocol files and generates an HTML/JS visualization")

    // Level 1: Major components
    val cliArgs = service.addIntent("Parse command-line arguments for input file and output file", parentId = 0)
    val fileReader = service.addIntent("Read and parse the intent protocol binary file", parentId = 0)
    val dataExtractor = service.addIntent("Extract timeline data from intents (timestamps, durations, hierarchy)", parentId = 0)
    val htmlGenerator = service.addIntent("Generate the HTML structure for the timeline", parentId = 0)
    val jsGenerator = service.addIntent("Generate JavaScript for interactive timeline rendering", parentId = 0)
    val cssGenerator = service.addIntent("Generate CSS styles for the timeline visualization", parentId = 0)
    val outputWriter = service.addIntent("Write the complete HTML file to disk", parentId = 0)

    // Level 2: CLI argument parsing
    service.addIntent("Define required argument: input .pb file path", parentId = cliArgs.id())
    service.addIntent("Define optional argument: output .html file path (default: input name + .html)", parentId = cliArgs.id())
    service.addIntent("Define optional argument: --open to auto-open in browser after generation", parentId = cliArgs.id())
    service.addIntent("Validate input file exists and is readable", parentId = cliArgs.id())
    service.addIntent("Print usage help if arguments are missing or invalid", parentId = cliArgs.id())

    // Level 2: File reading
    service.addIntent("Use protobuf library to parse IntentStream from binary file", parentId = fileReader.id())
    service.addIntent("Handle file not found and parse errors gracefully", parentId = fileReader.id())
    service.addIntent("Extract Header to get root intent description", parentId = fileReader.id())
    service.addIntent("Extract all Ops and replay them to build intent state", parentId = fileReader.id())

    // Level 2: Data extraction
    service.addIntent("Create TimelineEntry data class with: id, text, parentId, startTime, endTime, duration, done", parentId = dataExtractor.id())
    service.addIntent("For each intent, extract 'started' timestamp field if present", parentId = dataExtractor.id())
    service.addIntent("For each intent, extract 'completed' or 'finished' timestamp field if present", parentId = dataExtractor.id())
    service.addIntent("Calculate duration as (endTime - startTime) for each intent", parentId = dataExtractor.id())
    service.addIntent("Build parent-child relationships to show hierarchy", parentId = dataExtractor.id())
    service.addIntent("Sort entries by start time to determine execution order", parentId = dataExtractor.id())
    service.addIntent("Calculate relative timestamps from the earliest start time", parentId = dataExtractor.id())
    service.addIntent("Handle intents without timestamps (show as not-executed)", parentId = dataExtractor.id())

    // Level 2: HTML generation
    service.addIntent("Create HTML5 document structure with proper DOCTYPE and meta tags", parentId = htmlGenerator.id())
    service.addIntent("Add title based on root intent description", parentId = htmlGenerator.id())
    service.addIntent("Create container div for the timeline visualization", parentId = htmlGenerator.id())
    service.addIntent("Create sidebar div showing intent hierarchy as a tree", parentId = htmlGenerator.id())
    service.addIntent("Create main area div for the Gantt-style timeline chart", parentId = htmlGenerator.id())
    service.addIntent("Add summary statistics section (total time, task count, etc.)", parentId = htmlGenerator.id())
    service.addIntent("Embed the timeline data as a JSON object in a script tag", parentId = htmlGenerator.id())

    // Level 2: JavaScript generation
    service.addIntent("Create function to parse embedded timeline JSON data", parentId = jsGenerator.id())
    service.addIntent("Create function to render the Gantt-style timeline bars", parentId = jsGenerator.id())
    service.addIntent("Calculate appropriate time scale based on total duration", parentId = jsGenerator.id())
    service.addIntent("Draw time axis with appropriate tick marks (seconds, minutes, hours)", parentId = jsGenerator.id())
    service.addIntent("Render each task as a horizontal bar positioned by start time and sized by duration", parentId = jsGenerator.id())
    service.addIntent("Color-code bars by status: completed (green), in-progress (yellow), not-started (gray)", parentId = jsGenerator.id())
    service.addIntent("Add hover tooltips showing task details (name, start, end, duration)", parentId = jsGenerator.id())
    service.addIntent("Add click handler to show full task details in a panel", parentId = jsGenerator.id())
    service.addIntent("Implement zoom controls to focus on specific time ranges", parentId = jsGenerator.id())
    service.addIntent("Add collapse/expand functionality for parent tasks", parentId = jsGenerator.id())
    service.addIntent("Show execution order numbers on each bar", parentId = jsGenerator.id())

    // Level 2: CSS generation
    service.addIntent("Style the overall page layout with flexbox (sidebar + main)", parentId = cssGenerator.id())
    service.addIntent("Style the timeline container with horizontal scrolling for long timelines", parentId = cssGenerator.id())
    service.addIntent("Style task bars with rounded corners and subtle shadows", parentId = cssGenerator.id())
    service.addIntent("Define color scheme for task states (done, in-progress, pending)", parentId = cssGenerator.id())
    service.addIntent("Style the hierarchy tree in sidebar with indentation and expand/collapse icons", parentId = cssGenerator.id())
    service.addIntent("Style tooltips to appear on hover with task details", parentId = cssGenerator.id())
    service.addIntent("Add responsive styles for different screen sizes", parentId = cssGenerator.id())
    service.addIntent("Use a clean, modern color palette (consider dark mode support)", parentId = cssGenerator.id())

    // Level 2: Output writing
    service.addIntent("Combine HTML, CSS, and JS into a single self-contained HTML file", parentId = outputWriter.id())
    service.addIntent("Write the file to the specified output path", parentId = outputWriter.id())
    service.addIntent("Print success message with output file path", parentId = outputWriter.id())
    service.addIntent("If --open flag specified, launch default browser with the file", parentId = outputWriter.id())

    // Write to file
    service.writeToFile("visualize_timeline_plan.pb")
    println("Generated visualize_timeline_plan.pb")
}
