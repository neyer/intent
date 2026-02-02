package com.intentevolved

import com.intentevolved.com.intentevolved.IntentServiceImpl

/**
 * Generates a plan to build an HTML+JavaScript Tic Tac Toe game.
 */
fun main() {
    val service = IntentServiceImpl.new("Build an HTML+JavaScript Tic Tac Toe game")

    // Level 1: Major components
    val html = service.addIntent("Create the HTML structure for the game", parentId = 0)
    val css = service.addIntent("Style the game with CSS", parentId = 0)
    val gameLogic = service.addIntent("Implement the game logic in JavaScript", parentId = 0)
    val ui = service.addIntent("Implement UI interactions and display updates", parentId = 0)
    val extras = service.addIntent("Add finishing touches and polish", parentId = 0)

    // Level 2: HTML structure
    service.addIntent("Create index.html with DOCTYPE and basic HTML5 structure", parentId = html.id())
    service.addIntent("Add a title and header showing 'Tic Tac Toe'", parentId = html.id())
    service.addIntent("Create a 3x3 grid container for the game board", parentId = html.id())
    service.addIntent("Add 9 clickable cells inside the grid", parentId = html.id())
    service.addIntent("Add a status display showing whose turn it is or the winner", parentId = html.id())
    service.addIntent("Add a 'New Game' reset button", parentId = html.id())

    // Level 2: CSS styling
    service.addIntent("Style the page with a centered layout and nice background", parentId = css.id())
    service.addIntent("Style the game board as a 3x3 CSS grid with visible borders", parentId = css.id())
    service.addIntent("Style each cell with consistent size, hover effects, and cursor pointer", parentId = css.id())
    service.addIntent("Style X marks in one color (e.g., blue) and O marks in another (e.g., red)", parentId = css.id())
    service.addIntent("Style the status text and reset button", parentId = css.id())
    service.addIntent("Add a winning line highlight or animation", parentId = css.id())

    // Level 2: Game logic
    service.addIntent("Create game state variables: board array, current player, game active flag", parentId = gameLogic.id())
    service.addIntent("Implement function to check for a winner (8 winning combinations)", parentId = gameLogic.id())
    service.addIntent("Implement function to check for a draw (all cells filled, no winner)", parentId = gameLogic.id())
    service.addIntent("Implement function to handle a cell click and place mark", parentId = gameLogic.id())
    service.addIntent("Implement function to switch turns between X and O", parentId = gameLogic.id())
    service.addIntent("Implement function to reset the game to initial state", parentId = gameLogic.id())

    // Level 2: UI interactions
    service.addIntent("Add click event listeners to all cells", parentId = ui.id())
    service.addIntent("Update cell display when a mark is placed", parentId = ui.id())
    service.addIntent("Update status text to show current player's turn", parentId = ui.id())
    service.addIntent("Display winner message when game is won", parentId = ui.id())
    service.addIntent("Display draw message when game is a tie", parentId = ui.id())
    service.addIntent("Prevent clicks on already-filled cells or after game ends", parentId = ui.id())
    service.addIntent("Wire up the reset button to restart the game", parentId = ui.id())

    // Level 2: Polish
    service.addIntent("Add smooth transitions for mark placement", parentId = extras.id())
    service.addIntent("Highlight the winning cells when someone wins", parentId = extras.id())
    service.addIntent("Test the game works correctly in a browser", parentId = extras.id())

    // Write to file
    service.writeToFile("tic-tac-toe.pb")
    println("Generated tic-tac-toe.pb")
}
