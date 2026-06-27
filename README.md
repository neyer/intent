# Voluntas Intent

A shared work tree for multi-agent systems. Agents announce what they're doing, track their progress, and report completion — so you always know who is working on what, what's been done, and what's left to do.

---

## The Problem

When you run multiple AI agents in parallel, you lose visibility almost immediately.

- Which agent is handling which part of the work?
- Did agent A finish before agent B started? Or are they racing?
- What does "working on the auth system" actually mean right now — is it designing, implementing, or stuck?
- When something goes wrong, which agent's output caused it?

You end up asking agents to summarize their status, reading through long context windows, or just running one agent at a time to avoid the confusion. None of these scale.

---

## The Solution

Voluntas gives agents a shared work tree. Every agent reads the tree to find its assignment, writes its intent to the tree before starting, and marks progress as it goes. You can inspect the tree at any time to see exactly what every agent is doing.

The tree is hierarchical: a top-level goal breaks into requirements, requirements break into systems, systems break into concrete implementations. These levels can be nested arbitrarily deep. Agents work at the leaf level and mark things done as they go. The structure gives you a live dashboard of multi-agent progress without any polling or status requests.

![example of web ui](https://i.imgur.com/m3p4Ho4.png)

---

## How Agents Use It

Every agent gets access to four shell scripts:

```bash
./tools/intent-get.sh <id>          # read an intent and its children
./tools/intent-add.sh <text> <parent-id>  # create a new intent
./tools/intent-mark.sh start <id>   # claim work and signal start
./tools/intent-mark.sh done <id>    # signal completion
```

### At the start of a task

The agent explores the tree to find where its work belongs, creates a requirement intent, and signals that it has started:

```bash
# Find the right place in the tree
./tools/intent-get.sh 0             # start at root
./tools/intent-get.sh 42            # drill into a subtree

# Claim the work
REQ=$(./tools/intent-add.sh "Add OAuth2 login flow" 42 | jq -r '.id')
./tools/intent-mark.sh start $REQ
```

Any other agent or human watching the tree now sees: intent 87 is in progress, owned by whoever started it.

### During the task

The agent breaks the work down and marks each piece as it completes:

```bash
SYS=$(./tools/intent-add.sh "Token exchange and session handling" $REQ | jq -r '.id')
IMPL=$(./tools/intent-add.sh "Implement /auth/callback route" $SYS | jq -r '.id')

# ... do the actual work ...

./tools/intent-mark.sh done $IMPL
./tools/intent-mark.sh done $SYS
```

### At the end

```bash
./tools/intent-mark.sh done $REQ
```

The tree now shows the full record: what was planned, what was built, and that it's complete.

---

## What the Tree Looks Like

Running `./tools/intent-get.sh 0` against a live project might return:

```
0: "Rewrite authentication system" [in-progress]
├── 42: "Backend" [in-progress]
│   ├── 87: "Add OAuth2 login flow" [in-progress]  ← agent A is here
│   │   ├── 91: "Token exchange and session handling" [in-progress]
│   │   └── 92: "Implement /auth/callback route" [done]
│   └── 88: "Remove legacy session middleware" [done]
└── 43: "Frontend" [in-progress]
    └── 95: "Update login page to use OAuth redirect" [in-progress]  ← agent B is here
```

At a glance: two agents are running in parallel, one on the callback route (done) and one finishing token handling, the other updating the login page. No status requests needed.

---

## A Multi-Agent Example

Suppose you want three agents to work on a project in parallel. You set up the top-level structure and assign subtrees:

**Coordinator:**
```bash
ROOT=$(./tools/intent-add.sh "Ship v2.0 release" 0 | jq -r '.id')
BACKEND=$(./tools/intent-add.sh "Backend changes" $ROOT | jq -r '.id')
FRONTEND=$(./tools/intent-add.sh "Frontend changes" $ROOT | jq -r '.id')
TESTS=$(./tools/intent-add.sh "Integration tests" $ROOT | jq -r '.id')

echo "Agent A: work under intent $BACKEND"
echo "Agent B: work under intent $FRONTEND"
echo "Agent C: work under intent $TESTS"
```

Each agent is told its parent ID. From there, each one explores its subtree, adds its own implementation intents, and works independently. The coordinator (or a human) can check progress at any time:

```bash
./tools/intent-get.sh $ROOT    # see everything at once
./tools/intent-get.sh $BACKEND # drill into agent A's work
```

When agent C needs to know if agent A has finished the API it's testing against, it can check directly:

```bash
./tools/intent-get.sh $BACKEND   # look for the relevant impl intent
# if done: proceed; if in-progress: wait or work on something else
```

No inter-agent messaging. No coordinator polling. The tree is the coordination mechanism.

---

## Getting Started

### Prerequisites

- **Java 17+**
- A Unix-like shell (macOS, Linux, or WSL)
- The intent server running at `localhost:50051`

### Build and run

```bash
git clone https://github.com/neyer/intent.git
cd intent
./gradlew build
./tools/server.sh
```

### Try it

```bash
# See the current state of the tree
./tools/intent-get.sh 0

# Add a task
./tools/intent-add.sh "My first task" 0

# Start and complete it
./tools/intent-mark.sh start <id>
./tools/intent-mark.sh done <id>
```

### Visualize

```bash
./tools/visualize.sh
```

Then open `index.html` in a browser for an interactive view of the full intent tree.

---

## Token Tracking

Agents can also record how much compute they used on a task:

```bash
./tools/intent-set-tokens.sh $REQ <input_tokens> <output_tokens>
```

This writes token counts as fields on the requirement intent, so you can see the cost breakdown per task after the fact.

---

## Repository Structure

```
intent/
├── src/                        # Kotlin source (event-sourced runtime)
├── tools/                      # Shell scripts for agents and humans
│   ├── intent-get.sh           # Read an intent and its children
│   ├── intent-add.sh           # Create a new intent
│   ├── intent-mark.sh          # Mark start, done, etc.
│   ├── intent-set-tokens.sh    # Record token usage
│   ├── server.sh               # Start the intent server
│   ├── visualize.sh            # Generate the graph visualization
│   └── client.sh               # Interactive client
├── index.html                  # Browser-based tree visualization
├── voluntas_current.pb         # This project's own intent tree
└── Runtime.md                  # Implementation details
```

The `voluntas_current.pb` file is the intent tree for building Intent itself — the system is self-hosting.

---

## License

See the LICENSE file.
