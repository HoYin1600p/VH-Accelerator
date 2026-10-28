# BuildingGadgets template compaction

`compatibility.compactBuildingGadgetsTemplates` in the common configuration is
**true by default**. It requires a restart, the common optimization master
switch on and Compare Mode off. The settings screen lists it under Memory;
the Experimental button deliberately leaves it off.

The first supported scope is dedicated servers with the exact
BuildingGadgets `3.13.2-build.21+mc1.18.2` archive and supported Guava layout.
Clients and integrated servers retain the original implementation. Unsupported
versions, changed methods, conflicting transformations, inaccessible fields,
unsupported maps or keys, and failed validation retain the original path.

The optimization targets only privately constructed maps owned by supported
BuildingGadgets templates. It does not globally change Guava maps, delete empty
templates, share separate templates, or change the saved-data format.

**Packed positions (templates of 8 or more blocks).** The template's private
position map is replaced by a one-entry marker map whose single value holds the
entries as arrays: one `long` per block position and one palette index per
block (1, 2 or 4 bytes, by palette size), plus a palette of the template's
block-data objects. A loaded template then costs about 9 bytes per block
instead of about 58 (entry, position object and lookup table). Every read of
that field is only ever iterated (`serialize`, `rotate`, `mirror`, `replace`,
`normalize`: one `entrySet().iterator()` call each) or handed to a build view
(`createViewInContext`). The iterating methods are redirected to stream the
entries straight from the arrays: same entries, same order, equal positions and
the very same block-data objects, nothing rebuilt. The build view gets a light
read-only map over the arrays, so a paste does not rebuild the template either.
`withName`, `withNameAndAuthor` and `clearMaterials` pass the field value on
unchanged, so renamed copies share the packed arrays. The application check
proves that exactly these calls are redirected and that nothing else in
`Template` changed; if it fails, no template is compacted.

What changes for readers: block-position objects are created fresh for each
read of a packed template (equal and with equal hash codes, but not the same
objects), and the streamed entry object is reused between elements, which is
safe because every redirected method consumes each entry before asking for the
next. Block-data objects keep their identity. Measured on a real server with
the 6.4-million-block save, the whole `TemplateSave` serialization takes the
same time with the setting on as off (median about 1.0 times, run to run noise
about 10 percent).

Where a map cannot be packed (fewer than 8 blocks, a key that is not a plain
block position, or coordinates outside the lossless packing range), it keeps
its original form, and a JDK-backed one still gets the lookup-only index below.

**Lookup-only index (fallback).** It keeps the original immutable map object,
ordered entries, block-position objects and block-data references. Where that
map uses a JDK-backed lookup table, it replaces only the private lookup
delegate with a smaller primitive index.

The supported deserialization and fresh normalization, rotation and mirror
results are checked before publication: a packed map is compared with its
source entry by entry (coordinates and value identity) before the template is
switched, on a low-priority background thread for larger templates. Arbitrary
caller-provided constructor maps and unproven creation paths remain original.
Already compacted maps retain their identities when shared by header copies or
views. The existing original construction still happens, so this reduces
retained memory rather than its initial allocation peak.

For the lookup-only index: normal position queries use the index. Unusual query classes use a temporary
reconstruction of the original lookup table, preserving their original query
behavior without retaining that table. This exceptional path can temporarily
allocate substantial memory for a large template. Entries, key/value views,
iteration, equality, hashing and serialization continue using the original
ordered data.

Enable only on a separately backed-up disposable world until semantic save,
reload, placement and rollback checks pass. Keep the raw original saved data,
mod archive, configuration and full recovery backup. Restore the original
configuration or disable the setting and restart for compatibility rollback;
also rehearse full-backup restoration independently. No successful unit or
offline comparison substitutes for those server checks.

The [offline inventory tool](../tools/bg-template-inventory/README.md) records
the complete typed saved data using original registry-free codecs. Its equality
scope retains unknown states, properties, tile data, numeric types and list
order. Full `Template`/`BlockData` decoding requires the complete pack registry
and remains a separate server gate. `getTemplate` must never be used merely to
enumerate the save: it updates timestamps and marks it dirty.

Memory comparisons must count the full original and compact lookup graphs,
including table slack and retained entries/keys; report conversion peak and
temporary fallback materialization separately. The harness task
`bgadgetsCompactHarness` measures the original, lookup-only and packed graphs on
a copy of a real template save and proves the packed maps byte-identical through
the raw-tag save model. On the 59,366-template, 6.38-million-block test copy
the retained position data went from 690 MB (original) and 375 MB (lookup-only)
to 57 MB (packed).
