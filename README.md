# Voluntas Intent Protocol

> **Goal:** Enable cooperative cognition at arbitrary scale.

---

## The Problem

Imagine you could look at any line of code and see exactly *why* it was written — tracing it up through the task that required it, the feature it served, the product goal behind that feature, and the business mission at the root of it all. Not as a comment. Not as a Jira ticket linked by a string of institutional memory. As structured, queryable, living data.

That doesn't exist. And its absence causes enormous friction.

Here are examples most people in tech recognize immediately:

- **Design documents drift from code.** The reasoning behind architectural decisions lives in someone's head, a stale wiki page, or a Slack thread that's long since scrolled away.
- **Merge hell and blocked developers.** Teams block on requirements that exist only as someone's intentions, never articulated as structured data the system can act on.
- **8,000 Slack channels, all blinking.** Status updates are requested by humans because the data exists in machines — just not in a form that maps back to *purpose*.
- **Task trackers that miss the point.** Most tools force a single level of granularity. Real work is hierarchically composed: every keystroke happens for a reason, every function call serves a purpose, every feature is one step toward something bigger.

These problems aren't unique to software. They show up in organizations, in politics, in any context where groups of people try to think and act together. Technology is just where they're most visible — and most tractable.

I am using Voluntas to build Voluntas. Checkout the [full text of my plans so far](https://github.com/neyer/intent/blob/master/voluntas_current.txt), or this screenshot of the web tool in action:

![example of web ui](https://i.imgur.com/m3p4Ho4.png)

In addition to using Voluntas to build itself, I run a separate instance at home to track all the projects I juggle as a husband, father, son, brother, investor, writer, &c &c.

---

## The Idea

**Voluntas** is a protocol and runtime for representing intentional thought as structured, composable data.

The core insight is that **everything said or done in a collaborative system is said or done for a reason** — and that reason is itself a kind of computation. If we represent intent explicitly, we can build tools that understand not just *what* is happening, but *why*.

### How It Works

**Streams of Intent Operations**

Thinking is modeled as a stream of `ops`. Each op is both an **intent** (something being expressed or decided) and a **relationship to prior thoughts**. The stream is append-only: edits, deletions, and revisions are themselves ops. A given stream deterministically produces a well-defined application state, but the full history is always preserved. If you don't need the history, there's a simple mapping from state to stream.

**Everything Is an Intent**

The structure of thought is represented as a collection of `Intent` nodes, each with:
- a **type**
- a **name**
- **properties**
- **relationships** to other intents (which are themselves intents)

Because relationships are first-class intents, collaborators can comment on, approve, question, or dispute any thought in the system — and those responses are themselves part of the structured record.

Check out Runtime.md for details on how it's implemented.

**A Protocol and a Language**

Thinkers can propose and modify type definitions and functions within the system. This makes Voluntas both a **protocol** for communication and a **programming language with a runtime**. Where Lisp eliminates the distinction between code and data, Voluntas aims to eliminate the distinction between *development* and *runtime*. Thinking is itself a mixture of planning and execution.

---

## The Vision

I want to build the infrastructure for a new kind of shared cognition.

The internet today functions largely as a shared amygdala: platforms drive behavior through emotional responses, optimized for engagement, not deliberation. We need that capacity — but we need something more. We need a **neocortex**: a tool for shared, deliberate thought that enables planning, impulse control, and coordinated action at scale.

Voluntas is my attempt to build that layer. It should work as well for one person tracking their own tasks as it does for thousands of people — and AI agents — cooperating on complex, long-horizon goals, with each agent understanding not just *what* it's doing but *why*.

I have too many intelligent friends who disagree on almost everything except one thing: that intelligent people desperately need better tools for working together without devolving into an elaborate version of monkeys throwing poop at each other.

I don't think we need to agree on what is sacred to work together better. I think we need the right linguistic and computational tooling.

Yes, it's a large goal. But that's what I'm aiming for.

---

## Getting Started

### Prerequisites

- **Java 17+** (required to run the Gradle build)
- **[Protocol Buffers CLI (`protoc`)](https://grpc.io/docs/protoc-installation/)** for working with `.pb` files
- A Unix-like shell (macOS or Linux; WSL works on Windows)

### Build

```bash
git clone https://github.com/neyer/intent.git
cd intent
./gradlew build
```

### Run the Server

```bash
./server.sh
```

The server starts a Voluntas runtime that maintains the intent stream state and handles ops from connected clients and workers.

### Run a Worker

```bash
./worker.sh
```

Workers process intent operations. The included `claude_worker.pb` is an example worker definition backed by an AI agent.

### Connect a Client

```bash
./client.sh
```

The client connects to the running server and allows you to submit intent ops and query the current state.

### Visualize the Intent Graph

```bash
./visualize.sh
```

This generates a visual representation of the current intent DAG. Open `index.html` in a browser to explore the graph interactively. Allows for the execution of ops with low latency, exploration of the tree.

### Inspect a Protobuf File

```bash
./print-pb-file.sh voluntas_current.pb
```

This prints a human-readable version of any `.pb` state file in the repo. The included example files (`tic-tac-toe.pb`, `visualize_timeline_plan.pb`, `web_server_plan.pb`, `voluntas_current.pb`) demonstrate different shapes of intent graphs.

### Mark an Intent Complete

```bash
./intent-mark.sh <intent-id>
```

Appends a completion op to the stream for the given intent. This is currently implemented by adding a 'do' field and then setting it to true.

---

## Repository Structure

```
intent/
├── src/                        # Kotlin source
├── gradle/                     # Gradle wrapper
├── build.gradle.kts            # Build configuration
├── server.sh                   # Start the Voluntas server
├── client.sh                   # Connect a client to the server
├── worker.sh                   # Start a worker process
├── visualize.sh                # Visualize the intent DAG
├── intent-mark.sh              # Mark an intent complete
├── print-pb-file.sh            # Print a .pb file as text
├── index.html                  # Browser-based graph visualization
├── voluntas_current.pb         # Current Voluntas project state (self-referential)
├── claude_worker.pb            # Example AI worker definition
├── tic-tac-toe.pb              # Example: simple game as an intent graph
├── web_server_plan.pb          # Example: web server project plan
└── visualize_timeline_plan.pb  # Example: timeline planning
```

---

## License

Copyright © 2025 Mark Neyer. All rights reserved.

This software is licensed under the **Voluntas Noncommercial License**.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to use, copy, modify, and distribute the Software **for noncommercial purposes only**, subject to the following conditions:

1. **Noncommercial use only.** The Software may not be used, in whole or in part, for any commercial purpose. "Commercial purpose" means any activity intended to generate revenue, profit, or commercial advantage, whether directly or indirectly.

2. **Attribution.** All copies or substantial portions of the Software must include this copyright notice and license text.

3. **Share-alike.** Any modified versions of the Software distributed for noncommercial purposes must be distributed under the same license terms.

4. **No commercial sublicensing.** You may not sublicense the Software for commercial use.

For commercial licensing inquiries, contact Mark Neyer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
