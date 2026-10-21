# ADR 0001: OOXML (.docx/.xlsx/.pptx) format layer

**Status:** Accepted (2026-10-18). Own ZIP + XML layer in `core:ooxml`, decided by spike.
**Date:** 2026-10-18 (proposed) · 2026-10-18 (accepted)

## Context

ZiyOffice must read and write `.docx`, `.xlsx` and `.pptx`. These are ZIP archives of XML
(plus binary parts) defined by the OOXML specifications. Rendering and layout are ours by
design; this ADR is only about the *parsing/serialisation layer*: going from file bytes to
a document model and back.

Requirements:

- Runs on Android without compiled native code or proprietary blobs.
- Reasonable APK size impact (target: < 8 MB added).
- Licenses compatible with GPL-3.0 distribution through F-Droid.
- Does not pull in the network or telemetry of any kind.
- Supports a deliberate, documented subset first; expanding over time.

## Options considered

**A. Apache POI (OOXML parts: XWPF/XSSF/XSLF)**
Mature, well-documented, Apache-2.0. Downsides: built for JVM/desktop, has known Android
packaging friction (XMLBeans, reflection, class-path size), and its own model is heavyweight.

**B. Own ZIP + XML layer (`core:ooxml`)**
Implement the subset ourselves: unzip with `java.util.zip`, parse XML with the platform's
XML pull parser, map to our model, serialise back with a small writer. Upsides: total
control, tiny footprint, testable on the JVM, no license surprises, and we understand every
byte. Downsides: more work per supported feature; risk of subtle spec misses, mitigated by
the round-trip test corpus (LibreOffice used as a *test oracle* on the developer machine,
never shipped).

**C. docx4j / other JVM libraries**
Pulls in JAXB-style stacks that are painful or unusable on Android (even with Jakarta
packaging shims). Rejected on packaging risk.

## Spike results (timeboxed, 2026-10-18)

Option A was tested to the point of decisive evidence:

1. **Android has no StAX.** `android.jar` for platform 36 contains **zero** classes under
   `javax/xml/stream` (verified: `unzip -l android.jar | grep -c javax/xml/stream` → 0).
2. **POI's schema binder cannot work without it.** `xmlbeans` 5.2.1 (POI's required
   dependency, 1033 classes) references `javax.xml.stream` heavily:
   `XMLInputFactory`, `XMLEventFactory`, `XMLOutputFactory`, `XMLStreamReader`, `Location`,
   `XMLStreamConstants`, `XMLStreamException`, events/util helpers. `poi-ooxml` 5.2.5
   itself (875 classes) references `XMLStreamReader` and `XMLStreamException` directly.
3. **Runtime consequence:** instantiating `XWPFDocument`/`XSSFWorkbook`/`XMLSlideShow` on
   Android fails with `NoClassDefFoundError: javax.xml.stream.XMLStreamReader`. The
   community "POI for Android" ports are unmaintained forks of a much older POI.
4. **Weight:** the resolved stack (`poi` ~3 MB, `poi-ooxml` 2.0 MB, `xmlbeans` 2.2 MB, plus
   commons-compress, commons-io, log4j-api, SparseBitSet) is roughly **15 MB of jars
   before dexing**, already over the < 8 MB budget, and it would still need a fork to run.

Option B was validated with a proof of concept in the same timebox:

- `:core:ooxml` now implements `DocxReader` (ZIP scan for `word/document.xml` + pull-parser
  walk of `w:p`/`w:r`/`w:t`/`w:tab`/`w:br`) with 8 unit tests covering: paragraph extraction,
  run joining, tabs and breaks, empty paragraphs, ignoring `w:instrText`, namespace-prefix
  independence, unrelated ZIP entries, and the missing-part failure path.
- **Zero new runtime dependencies**: the parser uses the platform's `org.xmlpull` (Android
  API 1+); `kxml2` is a JVM-only `compileOnly`/test dependency and is not bundled.
- Verified on device: the editor shell opens a real `.docx` and renders its paragraphs.
## Decision

**Adopt option B: our own ZIP + XML layer in `core:ooxml`, for both reading and writing.**

- The read path grows first (docx text → document model → rendering), milestone by milestone.
- The write path follows for each format, with metadata scrubbing (M5) as a first-class,
  documented guarantee that no third-party library would give us for free.
- The supported subset is documented per format and expanded deliberately.
- Fidelity is measured, not guessed: a round-trip test corpus (semantic comparison;
  LibreOffice as a developer-machine-only oracle) grows with each milestone.
- `kxml2` is permitted only as a JVM test dependency. Any new runtime dependency for this
  layer requires a superseding ADR.

## Consequences

- ~~The `engine` interfaces (`DocumentImporter`, `DocumentExporter`) keep this decision
  reversible without touching UI code.~~ Superseded: no separate engine interface layer was shipped; the UI calls `DocxReader`/`DocxWriter` (and `XlsxReader`/`XlsxWriter`/`PptxReader`/`PptxWriter`) directly.
- Metadata handling (scrubbing on export, M5) is a first-class concern of whichever writer
  we ship. With our own writer it is a guarantee rather than a hope.
- We carry the maintenance load for the supported subset. The fidelity corpus is the
  objective measure of "does it look right", replacing guesswork with a score that grows
  release by release.
- APK stays small and the permission story stays simple: nothing in this layer can touch
  the network even in principle.
