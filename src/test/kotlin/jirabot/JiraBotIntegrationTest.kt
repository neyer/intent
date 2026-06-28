package jirabot

import com.apxhard.voluntas.jirabot.*
import com.apxhard.voluntas.worker.WorkerGrpcClient
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import voluntas.v1.*
import java.nio.file.Path

class JiraBotIntegrationTest {

    // =========================================================================
    // Config and connection (2798)
    // =========================================================================

    // 2816
    @Test
    fun `config loads successfully from a valid JSON file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("jira-bot.json").toFile()
        file.writeText("""
            {
              "jiraBaseUrl": "https://example.atlassian.net",
              "jiraUserEmail": "user@example.com",
              "jiraApiToken": "token123",
              "jiraProjectKey": "PROJ"
            }
        """.trimIndent())
        val config = Gson().fromJson(file.readText(), JiraConfig::class.java)
        assertEquals("https://example.atlassian.net", config.jiraBaseUrl)
        assertEquals("user@example.com", config.jiraUserEmail)
        assertEquals("token123", config.jiraApiToken)
        assertEquals("PROJ", config.jiraProjectKey)
    }

    // 2816
    @Test
    fun `malformed config JSON causes Gson to throw a parseable exception`() {
        val bad = "{ this is not valid json }"
        assertThrows(Exception::class.java) {
            Gson().fromJson(bad, JiraConfig::class.java)
        }
    }

    // 2819
    @Test
    fun `validate rejects config missing jiraBaseUrl`() {
        val errors = JiraConfig(jiraBaseUrl = "", jiraUserEmail = "a@b.com",
            jiraApiToken = "tok", jiraProjectKey = "P").validate()
        assertTrue(errors.any { it.contains("jiraBaseUrl") },
            "expected jiraBaseUrl error, got: $errors")
    }

    // 2819
    @Test
    fun `validate rejects config missing jiraUserEmail`() {
        val errors = JiraConfig(jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "",
            jiraApiToken = "tok", jiraProjectKey = "P").validate()
        assertTrue(errors.any { it.contains("jiraUserEmail") },
            "expected jiraUserEmail error, got: $errors")
    }

    // 2819
    @Test
    fun `validate rejects config missing jiraApiToken`() {
        val errors = JiraConfig(jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "a@b.com",
            jiraApiToken = "", jiraProjectKey = "P").validate()
        assertTrue(errors.any { it.contains("jiraApiToken") },
            "expected jiraApiToken error, got: $errors")
    }

    // 2819
    @Test
    fun `validate rejects config missing jiraProjectKey`() {
        val errors = JiraConfig(jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "a@b.com",
            jiraApiToken = "tok", jiraProjectKey = "").validate()
        assertTrue(errors.any { it.contains("jiraProjectKey") },
            "expected jiraProjectKey error, got: $errors")
    }

    // 2819
    @Test
    fun `validate returns no errors for a fully-specified config`() {
        val errors = JiraConfig(jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "a@b.com",
            jiraApiToken = "tok", jiraProjectKey = "PROJ").validate()
        assertTrue(errors.isEmpty(), "expected no errors, got: $errors")
    }

    // 2825
    @Test
    fun `voluntas credentials can be supplied directly in config`() {
        val config = JiraConfig(
            jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "a@b.com",
            jiraApiToken = "tok", jiraProjectKey = "P",
            voluntasUser = "mybot", voluntasToken = "secret"
        )
        assertEquals("mybot", config.voluntasUser)
        assertEquals("secret", config.voluntasToken)
    }

    // 2825 — the fallback to env vars is wired in JiraBot.main(); verify the field defaults to blank
    // so the calling code knows to read from the environment instead.
    @Test
    fun `voluntas credential fields default to blank to signal env-var fallback`() {
        val config = JiraConfig(jiraBaseUrl = "https://x.atlassian.net", jiraUserEmail = "a@b.com",
            jiraApiToken = "tok", jiraProjectKey = "P")
        assertEquals("", config.voluntasUser)
        assertEquals("", config.voluntasToken)
    }

    // =========================================================================
    // Tree walking (2801)
    // =========================================================================

    // 2828, 2831, 2837
    @Test
    fun `tree walker starts at rootIntentId, excludes meta intents, and assigns correct depth`() {
        val grpc = mockGrpc()
        val root   = intent(0, "Root")
        val child  = intent(1, "Real Child")
        val meta   = intent(2, "Meta Child", isMeta = true)
        val grand  = intent(3, "Grandchild")

        whenever(grpc.getFocalScope(0)).thenReturn(scope(root, child, meta))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(child, grand))
        whenever(grpc.getFocalScope(3)).thenReturn(scope(grand))

        val tree = TreeWalker(grpc).buildTree(rootId = 0)

        assertEquals(1, tree.size, "only one non-meta child of root")
        assertEquals("Real Child", tree[0].intent.text)
        assertEquals(1, tree[0].depth, "direct children of root are depth 1")
        assertEquals(1, tree[0].children.size)
        assertEquals("Grandchild", tree[0].children[0].intent.text)
        assertEquals(2, tree[0].children[0].depth, "grandchildren are depth 2")
    }

    // 2828 — custom rootIntentId
    @Test
    fun `tree walker honours a non-zero rootIntentId`() {
        val grpc  = mockGrpc()
        val root  = intent(42, "Custom Root")
        val child = intent(43, "Child of 42")

        whenever(grpc.getFocalScope(42)).thenReturn(scope(root, child))
        whenever(grpc.getFocalScope(43)).thenReturn(scope(child))

        val tree = TreeWalker(grpc).buildTree(rootId = 42)

        assertEquals(1, tree.size)
        assertEquals("Child of 42", tree[0].intent.text)
        verify(grpc, never()).getFocalScope(0)
    }

    // 2834
    @Test
    fun `tree walker excludes done intents when skipDone is true`() {
        val grpc   = mockGrpc()
        val root   = intent(0, "Root")
        val active = intent(1, "Active")
        val done   = intent(2, "Done", done = true)

        whenever(grpc.getFocalScope(0)).thenReturn(scope(root, active, done))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(active))

        val tree = TreeWalker(grpc).buildTree(rootId = 0, skipDone = true)

        assertEquals(1, tree.size)
        assertEquals("Active", tree[0].intent.text)
    }

    // 2834 — done intents are included when skipDone is false (the default)
    @Test
    fun `tree walker includes done intents when skipDone is false`() {
        val grpc = mockGrpc()
        val root = intent(0, "Root")
        val done = intent(1, "Done", done = true)

        whenever(grpc.getFocalScope(0)).thenReturn(scope(root, done))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(done))

        val tree = TreeWalker(grpc).buildTree(rootId = 0, skipDone = false)

        assertEquals(1, tree.size)
        assertEquals("Done", tree[0].intent.text)
    }

    // =========================================================================
    // Issue creation (2804)
    // =========================================================================

    // 2840, 2846, 2855
    @Test
    fun `sync maps depth to issue type, passes intent ID in description, and sleeps 150ms per call`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic", "2" to "Story"))
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenReturn("PROJ-1").thenReturn("PROJ-2")

        val epic  = intent(10, "My Epic")
        val story = intent(11, "My Story")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(10)).thenReturn(scope(epic, story))
        whenever(grpc.getFocalScope(11)).thenReturn(scope(story))

        val start = System.currentTimeMillis()
        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()
        val elapsed = System.currentTimeMillis() - start

        // 2840 — issue types come from depthToIssueType
        verify(jira).createIssue(any(), eq("Epic"),  anyOrNull(), any(), anyOrNull(), any())
        verify(jira).createIssue(any(), eq("Story"), anyOrNull(), any(), anyOrNull(), any())

        // 2846 — intent IDs are passed so JiraClient can embed them in the description
        verify(jira).createIssue(any(), any(), anyOrNull(), any(), eq(10L), any())
        verify(jira).createIssue(any(), any(), anyOrNull(), any(), eq(11L), any())

        // 2855 — two API calls → at least 300 ms of sleep
        assertTrue(elapsed >= 300,
            "expected ≥ 300ms for two calls with 150ms pauses each, got ${elapsed}ms")
    }

    // 2843
    @Test
    fun `sync passes the full intent text to JiraClient (truncation to 255 is JiraClient's responsibility)`() {
        val longText = "A".repeat(300)
        val config   = testConfig(depthToIssueType = mapOf("1" to "Epic"))
        val grpc     = mockGrpc()
        val jira     = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")

        val node = intent(1, longText)
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), node))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(node))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        val captor = argumentCaptor<String>()
        verify(jira).createIssue(captor.capture(), any(), anyOrNull(), any(), anyOrNull(), any())
        assertEquals(longText, captor.firstValue,
            "SyncEngine must forward the raw text; truncation happens inside JiraClient.createIssue")
    }

    // 2846 — field values are included in the description notes
    @Test
    fun `sync includes non-jira field values in the description notes`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"))
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")

        val node = intent(1, "Epic With Fields", stringFields = mapOf("priority" to "high"))
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), node))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(node))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        val captor = argumentCaptor<String>()
        verify(jira).createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), captor.capture())
        assertTrue(captor.firstValue.contains("priority"),
            "field name 'priority' should appear in fieldNotes, got: '${captor.firstValue}'")
        assertTrue(captor.firstValue.contains("high"),
            "field value 'high' should appear in fieldNotes, got: '${captor.firstValue}'")
    }

    // 2849
    @Test
    fun `depth-2 intent uses epicLinkField when configured, depth-1 uses parentLinkField`() {
        val config = testConfig(
            depthToIssueType = mapOf("1" to "Epic", "2" to "Story"),
            epicLinkField    = "customfield_10014"
        )
        val grpc = mockGrpc()
        val jira = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenReturn("PROJ-1").thenReturn("PROJ-2")

        val epic  = intent(1, "Epic")
        val story = intent(2, "Story")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, story))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(story))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        // depth-1 epic: no parent, uses default parentLinkField
        verify(jira).createIssue(eq("Epic"), any(), isNull(), eq("parent"), any(), any())
        // depth-2 story: parent is epic, uses epicLinkField
        verify(jira).createIssue(eq("Story"), any(), eq("PROJ-1"), eq("customfield_10014"), any(), any())
    }

    // 2849 — without epicLinkField, both depths use parentLinkField
    @Test
    fun `without epicLinkField all depths use parentLinkField`() {
        val config = testConfig(
            depthToIssueType = mapOf("1" to "Epic", "2" to "Story"),
            epicLinkField    = null
        )
        val grpc = mockGrpc()
        val jira = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenReturn("PROJ-1").thenReturn("PROJ-2")

        val epic  = intent(1, "Epic")
        val story = intent(2, "Story")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, story))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(story))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        verify(jira).createIssue(eq("Story"), any(), eq("PROJ-1"), eq("parent"), any(), any())
    }

    // 2852
    @Test
    fun `sync retries with alternate link field then falls back to orphan when both fail`() {
        val config = testConfig(
            depthToIssueType = mapOf("1" to "Epic", "2" to "Story"),
            epicLinkField    = "customfield_10014"
        )
        val grpc = mockGrpc()
        val jira = mock<JiraClient>()

        val epic  = intent(1, "Epic")
        val story = intent(2, "Story")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, story))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(story))

        // Epic succeeds; story fails on both link fields, then succeeds without parent
        whenever(jira.createIssue(eq("Epic"),  any(), isNull(),      any(),                       any(), any())).thenReturn("PROJ-1")
        whenever(jira.createIssue(eq("Story"), any(), eq("PROJ-1"),  eq("customfield_10014"),     any(), any())).thenThrow(RuntimeException("epic link rejected"))
        whenever(jira.createIssue(eq("Story"), any(), eq("PROJ-1"),  eq("parent"),                any(), any())).thenThrow(RuntimeException("parent link also rejected"))
        whenever(jira.createIssue(eq("Story"), any(), isNull(),      any(),                       any(), any())).thenReturn("PROJ-2")

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(2, result.issuesCreated, "both issues should be created despite link failures")
    }

    // =========================================================================
    // Deep intents as comments (2807)
    // =========================================================================

    // 2858, 2861
    @Test
    fun `intents beyond maxIssueDepth become a comment on their nearest ancestor issue`() {
        // maxIssueDepth = 1: only depth-1 nodes become issues; deeper ones become comments
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")
        whenever(jira.addComment(any(), any())).thenReturn("c1")

        val epic  = intent(1, "Epic Item")
        val deep  = intent(2, "Deep Child")
        val grand = intent(3, "Deep Grandchild")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, deep))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(deep, grand))
        whenever(grpc.getFocalScope(3)).thenReturn(scope(grand))

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(1, result.issuesCreated)
        assertEquals(1, result.commentsAdded)

        // 2861 — comment text is an indented bullet list with intent IDs
        val commentCaptor = argumentCaptor<String>()
        verify(jira).addComment(eq("PROJ-1"), commentCaptor.capture())
        val text = commentCaptor.firstValue
        assertTrue(text.contains("[#2]"),  "comment should include deep child ID #2")
        assertTrue(text.contains("Deep Child"), "comment should include deep child text")
        assertTrue(text.contains("[#3]"),  "comment should include grandchild ID #3")
        assertTrue(text.contains("Deep Grandchild"), "comment should include grandchild text")
        val childLine = text.lines().first { it.contains("[#2]") }
        val grandLine = text.lines().first { it.contains("[#3]") }
        assertFalse(childLine.startsWith("  "),  "child line should not be indented")
        assertTrue(grandLine.startsWith("  "),   "grandchild line should be indented under child")
    }

    // 2861 — field values appear in comment text
    @Test
    fun `comment subtree text includes field values alongside intent IDs`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")
        whenever(jira.addComment(any(), any())).thenReturn("c1")

        val epic = intent(1, "Epic Item")
        val deep = intent(2, "Deep Child", stringFields = mapOf("status" to "pending"))
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, deep))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(deep))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        val captor = argumentCaptor<String>()
        verify(jira).addComment(any(), captor.capture())
        val text = captor.firstValue
        assertTrue(text.contains("status"), "field name should appear in comment")
        assertTrue(text.contains("pending"), "field value should appear in comment")
    }

    // 2864
    @Test
    fun `new children under an already-commented intent become additional comments to the same ancestor`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.addComment(any(), any())).thenReturn("c1")

        // Epic already has jira_key; deep child already has jira_comment_ancestor;
        // new grandchild is unseen and should become a fresh comment on PROJ-1.
        val epicSynced  = intent(1, "Epic",          jiraKey          = "PROJ-1")
        val deepSynced  = intent(2, "Deep Child",    commentAncestor  = "PROJ-1")
        val newGrand    = intent(3, "New Grandchild")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epicSynced))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epicSynced, deepSynced))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(deepSynced, newGrand))
        whenever(grpc.getFocalScope(3)).thenReturn(scope(newGrand))

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(0, result.issuesCreated,  "no new issues — epic is already synced")
        assertEquals(1, result.commentsAdded,  "new grandchild should appear as a new comment")
        verify(jira).addComment(eq("PROJ-1"), any())
    }

    // =========================================================================
    // State persistence (2810)
    // =========================================================================

    // 2867
    @Test
    fun `sync records jira_key as a STRING field on the intent after issue creation`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"))
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-99")

        val node = intent(5, "My Epic")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), node))
        whenever(grpc.getFocalScope(5)).thenReturn(scope(node))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        verify(grpc).addField(eq(5L), eq("jira_key"), eq(FieldType.FIELD_TYPE_STRING), anyOrNull())
        verify(grpc).setStringField(eq(5L), eq("jira_key"), eq("PROJ-99"))
    }

    // 2867
    @Test
    fun `sync records jira_comment_ancestor as a STRING field after adding a comment`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")
        whenever(jira.addComment(any(), any())).thenReturn("c1")

        val epic = intent(1, "Epic")
        val deep = intent(7, "Deep Child")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, deep))
        whenever(grpc.getFocalScope(7)).thenReturn(scope(deep))

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        verify(grpc).addField(eq(7L), eq("jira_comment_ancestor"), eq(FieldType.FIELD_TYPE_STRING), anyOrNull())
        verify(grpc).setStringField(eq(7L), eq("jira_comment_ancestor"), eq("PROJ-1"))
    }

    // 2870
    @Test
    fun `intents with jira_key already set are skipped without re-creating an issue`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"))
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()

        val alreadySynced = intent(1, "Epic", jiraKey = "PROJ-1")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), alreadySynced))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(alreadySynced))

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(0, result.issuesCreated)
        verify(jira, never()).createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())
    }

    // 2870
    @Test
    fun `intents with jira_comment_ancestor already set are skipped without re-adding a comment`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()

        val epicSynced = intent(1, "Epic",  jiraKey         = "PROJ-1")
        val deepSynced = intent(2, "Deep",  commentAncestor = "PROJ-1")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epicSynced))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epicSynced, deepSynced))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(deepSynced))

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(0, result.issuesCreated)
        assertEquals(0, result.commentsAdded)
        verify(jira, never()).addComment(any(), any())
    }

    // 2873 — state is written to the stream immediately after each sync, verified by the
    //         ordering of createIssue → setStringField within a single syncOnce() call.
    @Test
    fun `state is written to the stream immediately after each item is synced`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic", "2" to "Story"))
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .thenReturn("PROJ-1").thenReturn("PROJ-2")

        val epic  = intent(1, "Epic")
        val story = intent(2, "Story")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, story))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(story))

        val callOrder = mutableListOf<String>()
        whenever(jira.createIssue(eq("Epic"),  any(), anyOrNull(), any(), any(), any())).thenAnswer {
            callOrder += "createEpic"; "PROJ-1"
        }
        whenever(jira.createIssue(eq("Story"), any(), anyOrNull(), any(), any(), any())).thenAnswer {
            callOrder += "createStory"; "PROJ-2"
        }
        whenever(grpc.setStringField(eq(1L), eq("jira_key"), any())).thenAnswer {
            callOrder += "persistEpic"
            SubmitOpResponse.newBuilder().setSuccess(true).build()
        }
        whenever(grpc.setStringField(eq(2L), eq("jira_key"), any())).thenAnswer {
            callOrder += "persistStory"
            SubmitOpResponse.newBuilder().setSuccess(true).build()
        }

        SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        val epicCreate   = callOrder.indexOf("createEpic")
        val epicPersist  = callOrder.indexOf("persistEpic")
        val storyCreate  = callOrder.indexOf("createStory")
        val storyPersist = callOrder.indexOf("persistStory")
        assertTrue(epicCreate  < epicPersist,  "epic state must be persisted before story is created")
        assertTrue(storyCreate < storyPersist, "story state must be persisted right after story is created")
    }

    // =========================================================================
    // Operational modes (2813)
    // =========================================================================

    // 2876
    @Test
    fun `one-shot syncOnce returns counts of issues created and comments added`() {
        val config = testConfig(depthToIssueType = mapOf("1" to "Epic"), maxIssueDepth = 1)
        val grpc   = mockGrpc()
        val jira   = mock<JiraClient>()
        whenever(jira.createIssue(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn("PROJ-1")
        whenever(jira.addComment(any(), any())).thenReturn("c1")

        val epic = intent(1, "Epic")
        val deep = intent(2, "Deep")
        whenever(grpc.getFocalScope(0)).thenReturn(scope(intent(0, "Root"), epic))
        whenever(grpc.getFocalScope(1)).thenReturn(scope(epic, deep))
        whenever(grpc.getFocalScope(2)).thenReturn(scope(deep))

        val result = SyncEngine(config, jira, TreeWalker(grpc), grpc).syncOnce()

        assertEquals(1, result.issuesCreated)
        assertEquals(1, result.commentsAdded)
        assertEquals("1 issue(s), 1 comment(s)", result.toString())
    }

    // 2879
    @Test
    fun `SyncResult hasChanges is true when items were created and false when nothing changed`() {
        assertTrue(SyncResult(1, 0).hasChanges)
        assertTrue(SyncResult(0, 1).hasChanges)
        assertTrue(SyncResult(3, 2).hasChanges)
        assertFalse(SyncResult(0, 0).hasChanges)
    }

    // 2885
    @Test
    fun `init mode produces a valid JSON config file that round-trips through Gson`(@TempDir tempDir: Path) {
        val gson    = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val example = JiraConfig(
            serverAddress    = "localhost:50051",
            jiraBaseUrl      = "https://your-org.atlassian.net",
            jiraUserEmail    = "you@example.com",
            jiraApiToken     = "your-jira-api-token",
            jiraProjectKey   = "PROJ",
            depthToIssueType = mapOf("1" to "Epic", "2" to "Story", "3" to "Sub-task")
        )
        val file = tempDir.resolve("jira-bot.json.example").toFile()
        file.writeText(gson.toJson(example))

        val loaded = gson.fromJson(file.readText(), JiraConfig::class.java)
        assertEquals(example.jiraBaseUrl,    loaded.jiraBaseUrl)
        assertEquals(example.jiraProjectKey, loaded.jiraProjectKey)
        assertEquals(example.depthToIssueType, loaded.depthToIssueType)
        assertTrue(loaded.validate().isEmpty(), "example config must pass validation")
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun testConfig(
        depthToIssueType: Map<String, String> = mapOf("1" to "Epic", "2" to "Story", "3" to "Sub-task"),
        maxIssueDepth: Int = 3,
        epicLinkField: String? = null
    ) = JiraConfig(
        jiraBaseUrl      = "https://test.atlassian.net",
        jiraUserEmail    = "test@test.com",
        jiraApiToken     = "test-token",
        jiraProjectKey   = "TEST",
        depthToIssueType = depthToIssueType,
        maxIssueDepth    = maxIssueDepth,
        epicLinkField    = epicLinkField
    )

    private fun mockGrpc(): WorkerGrpcClient {
        val grpc = mock<WorkerGrpcClient>()
        val ok   = SubmitOpResponse.newBuilder().setSuccess(true).build()
        whenever(grpc.addField(any(), any(), any(), anyOrNull())).thenReturn(ok)
        whenever(grpc.setStringField(any(), any(), any())).thenReturn(ok)
        return grpc
    }

    private fun intent(
        id: Long,
        text: String,
        isMeta: Boolean = false,
        done: Boolean = false,
        jiraKey: String? = null,
        commentAncestor: String? = null,
        stringFields: Map<String, String> = emptyMap()
    ): IntentProto {
        val b = IntentProto.newBuilder().setId(id).setText(text).setIsMeta(isMeta)
        if (done)            b.putFieldValues("done",
            FieldValueProto.newBuilder().setBoolValue(true).build())
        if (jiraKey != null) b.putFieldValues("jira_key",
            FieldValueProto.newBuilder().setStringValue(jiraKey).build())
        if (commentAncestor != null) b.putFieldValues("jira_comment_ancestor",
            FieldValueProto.newBuilder().setStringValue(commentAncestor).build())
        stringFields.forEach { (k, v) ->
            b.putFieldValues(k, FieldValueProto.newBuilder().setStringValue(v).build())
        }
        return b.build()
    }

    private fun scope(focus: IntentProto, vararg children: IntentProto): GetFocalScopeResponse =
        GetFocalScopeResponse.newBuilder()
            .setFound(true)
            .setFocus(focus)
            .addAllChildren(children.toList())
            .build()
}
