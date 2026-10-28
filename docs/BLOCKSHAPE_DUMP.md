# Block registry shape dump

Run `/vha dump blockshape` from the server console or a player account with
permission level 4. It writes a new compressed JSON file under
`logs/vhaccelerator/blockshape-<timestamp>-<unique>.json.gz` and reports its
location and totals. The command captures registry metadata; it does not
change blocks, worlds, settings or optimizations. WorldEdit is not required.

The dump runs once on the server thread. Use it during a planned diagnostic
session when collecting the property layout of a large modpack. It reads each
block's state-count metadata rather than visiting every possible block state.
Existing files are never overwritten; an unsuccessful write is reported as a
failure and its newly created partial file is removed.

## Format

The gzip payload is UTF-8 JSON with `schemaVersion: 1`, source
`minecraft-block-registry`, a UTC generation time and Minecraft version.
`blocks` preserves registry encounter order. Each block records its index,
registry ID, runtime block class, original state count, ordered properties and
ordered default values.

A property records its name, property class, declared value class and possible
values in their native encounter order. Each possible value has an index,
canonical property name, `toString` text and runtime value class. These two
string forms may differ. Default values include the matching domain index so
consumers can recover their position without sorting or parsing display text.

`totals` contains block types, states, properties and domain values. It is
written after the block array to allow streaming without retaining a second
copy of the registry. JSON member order outside arrays is not a contract.
Counts are checked for overflow; missing or ambiguous defaults fail the dump.

## Using the data

Keep the file with the exact mod/config version used to collect it. It is a
fixture for offline state-layout and memory experiments, not a registry cache
or a file that the game loads. A consumer must validate its schema and totals
and preserve array order, value classes and default indices.

Native registry shape alone does not prove another mod's property-object
identities, adapter behavior, native ID mapping or full platform lifecycle.
An offline reconstruction must state which of those are surrogates and test
the actual original classes separately. Heap, startup and command-latency
measurements remain distinct from a successful metadata dump.