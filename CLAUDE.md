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
