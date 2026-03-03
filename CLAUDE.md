# Intent Tools

The intent server tracks work as a tree of intents. Each intent has an integer ID, a text description, and optional field values (e.g. `done: true`). The root of the tree is always ID 0.

All scripts live in `tools/` and are run from the repo root. The server must be running (default `localhost:50051`).

## Reading intents

```
./tools/intent-get.sh <id> [server]
```

Returns the focal scope for `<id>`: the intent itself, its ancestry (parent chain back to root), and its direct children. Start at `0` to explore from the root.

The response is JSON. Key fields in each intent:
- `id`: integer ID
- `text`: description
- `fieldValues`: map of field name → value (e.g. `"done": {"boolValue": true}`)
- `isMeta`: if true, this is a system/structural intent — ignore it when exploring tasks

To traverse the tree, call `intent-get.sh` on child IDs found in the `children` array.

## Adding intents

```
./tools/intent-add.sh <text> [parent-id] [server]
```

Creates a new intent with the given text under `parent-id` (defaults to `0`, the root). Returns JSON with `id` of the new intent.

Example — add a task under intent 42:
```
./tools/intent-add.sh "Write unit tests" 42
```

## Marking intents done

```
./tools/intent-mark.sh done <id> [server]
```

Marks the intent as done (sets `done=true` and records a completion timestamp).

Example:
```
./tools/intent-mark.sh done 42
```

## Writing token counts

```
./tools/intent-set-tokens.sh <id> <input-tokens> <output-tokens> [server]
```

Writes `input_tokens` and `output_tokens` as INT64 fields on the intent.

---

## Workflow for every task prompt

When the user gives a task prompt, follow these steps if the intent server is reachable
(probe with `./tools/intent-get.sh 0` — skip all intent steps silently if it fails):

### 1. Find the right parent and create a requirement intent

**If the user specifies a parent** (e.g. "put this under intent 42"), use that ID directly.

**Otherwise, find the best match by exploring the tree.** Walk down from the root, reading
each level's children, until you find the most specific existing intent whose purpose
contains the current task. Stop when none of the children are more specific than the
current node. Use that node's ID as the parent.

```
# Explore to find the right parent
./tools/intent-get.sh 0      # start at root
./tools/intent-get.sh <id>   # drill into the most relevant child, repeat as needed
```

Then create the requirement under the chosen parent:

```
PARENT_ID=<chosen id>
REQUIREMENT_ID=$(./tools/intent-add.sh "<one-line summary of the task>" $PARENT_ID | jq -r '.id')
./tools/intent-mark.sh start $REQUIREMENT_ID
```

### 2. Add system intents for each major area of work

Break the task into its major sub-problems and add a system intent for each one under the
requirement. Do this before writing any code:

```
SYSTEM_ID=$(./tools/intent-add.sh "<what this part of the work achieves>" $REQUIREMENT_ID | jq -r '.id')
```

### 3. Add implementation intents for each concrete change

Under each system, add an implementation intent for every file or component you will touch:

```
IMPL_ID=$(./tools/intent-add.sh "<specific file/change description>" $SYSTEM_ID | jq -r '.id')
```

### 4. Mark implementations done as you complete them

After finishing each implementation:

```
./tools/intent-mark.sh done $IMPL_ID
```

### 5. After all work is done, write token counts and close out

Write the total token counts for the conversation turn on the requirement intent, then mark
it done. Use the `total_tokens` figure from the most recent `<usage>` block visible in
context; if no usage data is visible, write 0.

```
./tools/intent-set-tokens.sh $REQUIREMENT_ID <input_tokens> <output_tokens>
./tools/intent-mark.sh done $REQUIREMENT_ID
```

### Example

For a prompt like "Add a logout button to the nav bar":

```
# Explore tree to find right parent, e.g. found intent 99 "UI components"
REQUIREMENT_ID=$(./tools/intent-add.sh "Add logout button to nav bar" 99 | jq -r '.id')
./tools/intent-mark.sh start $REQUIREMENT_ID

SYS_ID=$(./tools/intent-add.sh "Nav bar UI change" $REQUIREMENT_ID | jq -r '.id')
IMPL_ID=$(./tools/intent-add.sh "Add LogoutButton to NavBar.tsx" $SYS_ID | jq -r '.id')

# ... do the work ...

./tools/intent-mark.sh done $IMPL_ID
./tools/intent-mark.sh done $SYS_ID
./tools/intent-set-tokens.sh $REQUIREMENT_ID 1200 340
./tools/intent-mark.sh done $REQUIREMENT_ID
```
