# Tala documentation

Start with the six specifications below. They share the offline native Android release scope and distinguish current code, target requirements and future cloud work. Version 1.0 was prepared on 11 October 2026; open product decisions are collected in the PRD.

| Document | Purpose |
|---|---|
| [01 · Product Requirements](specifications/01-product-requirements.md) | Features, user expectations, 18 sections, acceptance and open decisions |
| [02 · Technical Requirements](specifications/02-technical-requirements.md) | Architecture, durability, constraints, nonfunctional targets and release gates |
| [03 · Data Models & Schemas](specifications/03-data-models-and-schemas.md) | Current Room schema, ERD, target extensions and portable backup contract |
| [04 · User Flows & Wireframes](specifications/04-user-flows-and-wireframes.md) | Navigation, journeys, states and screen contracts |
| [05 · Technology Stack](specifications/05-technology-stack.md) | Declared versions, environments, tools and deployment boundaries |
| [06 · API & Integration Specs](specifications/06-api-and-integration-specifications.md) | Local interfaces, location/maps/files and future sync semantics |

Supporting artifacts:

- [Tala Registry Home implementation and agent handoff](design/HOME-REDESIGN-2026-10-21.md): approved design, live-data contracts, responsive behavior, source ownership and verification.
- [Portrait and landscape UI/UX implementation guide](design/UI-UX-IMPLEMENTATION-PLAN-2026-10-21.md): accepted recommendations, agent instructions, and verification checklist for the upcoming native UI changes.
- [Linked wireframe gallery](design/wireframes.html): 12 target screens, local/offline HTML.
- [Requirements traceability](traceability.md): features → screens → code/tests → remaining acceptance.
- [Native acceptance matrix](reference/native-acceptance-matrix.md): every PR/TR requirement mapped to implementation, tests and outstanding gates.
- [Field dictionary](schemas/questionnaire-field-dictionary.md): 18 sections and current browser field definitions.
- [Current questionnaire JSON](schemas/questionnaire-current.json) and [Room v1 SQLite DDL](schemas/room-v1.sql): generated implementation references.
- [Proposed backup schema](schemas/backup-v1.schema.json) and [fictional example](schemas/backup-v1.example.json): documented target; no importer is implemented yet.
- [System analysis](reviews/SYSTEM-ANALYSIS.md) and [reproduction evidence](reviews/SYSTEM-ANALYSIS-EVIDENCE.json): dated review, not a live completion checklist.
- [Reference notes](reference/README.md): earlier plans and browser revisions, retained for context.
- [Folder map and organization record](project-structure.md).

Generate implementation references with `rtk proxy node tools/generate-reference.cjs`; rebuild wireframes with `rtk proxy node tools/build-wireframes.cjs`; check documentation with `rtk proxy node tools/check-docs.cjs`, all from repository root. Edit specifications directly; generated artifacts name their generator. Do not treat proposed schemas as applied migrations.
