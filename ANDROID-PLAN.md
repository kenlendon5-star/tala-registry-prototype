# Tala Android implementation plan

Planning baseline: September 24, 2026. This is a proposed roadmap, not an implemented Android build. Deadline, team size, target devices, and demo-versus-pilot scope remain unconfirmed.

## Recommendation

Start with a short Capacitor feasibility build using fictional records. It can package the existing HTML/CSS/JavaScript and connect it to native Android features. Keep the existing UI initially; evaluate the result on an actual target phone before committing to the full build. [Capacitor documentation](https://capacitorjs.com/docs)

The earlier BUILD-PLAN.md proposes React Native + Expo for production. That remains an alternative if native UI requirements or team expertise justify rebuilding the screens. A real deployment does not inherently require a rewrite: either approach needs durable storage, authorization, verified forms, and reliable synchronization.

| Approach | Reuse from this repository | Main tradeoff | Choose when |
| --- | --- | --- | --- |
| Capacitor | Most HTML, CSS, JavaScript, and Leaflet UI | WebView behavior and native plugins require device testing | Deliver an Android version of the existing app efficiently |
| React Native + Expo | Field definitions, validation concepts, workflows, visual design | DOM renderers, CSS layouts, and Leaflet integration need replacement or adaptation | Native UI is a firm requirement and the team can support a larger migration |
| Kotlin + Jetpack Compose | Specification and behavior primarily | Largest rewrite and a different implementation language | The project explicitly requires an Android-native stack |

## Current baseline

- `index.html`, `style.css`, and `app.js` provide the shell and visual design.
- `forms.js` defines 18 illustrative sections with household/member scope and conditional questions.
- `workflow.js` combines DOM rendering, validation, navigation, local storage, signatures, review, and exports.
- `geomapping.js` uses Leaflet, online OpenStreetMap tiles, browser geolocation, and Google Maps links.
- `server.cjs` is a local static development server. It is not a backend and should not run on the phone.
- Data currently lives in `localStorage`; selected-record context uses `sessionStorage`. There is no account system or server synchronization.
- Existing browser checks are useful regression coverage, but do not establish Android behavior. They were not rerun for this planning task.

## Milestone 1: agree on the first release

For the initial demo, preserve registration, household/member editing, all current form sections, conditional validation, save/resume, review, callbacks, maps, signatures, summaries, and JSON export. Use fictional data.

For a field pilot, additionally require verified questionnaire definitions, an approved collection workflow, individual accounts, assigned-record access, encrypted device storage, backup/recovery, synchronization, supervisor review, and an audit history. Decide when the notice/consent or refusal flow must occur; do not assume the current final consent section is suitable for deployment.

Confirm the target Android phones, installation method, deadline, developer availability, expected record volume, hosting budget, and who maintains the system. Play Store distribution is optional for the first internal demonstration.

**Done when:** the required workflows and target devices are recorded, and demo versus pilot scope is explicit.

## Milestone 2: prove Android packaging

1. Set up Android Studio, Android SDK, and the Node/JDK versions supported by the chosen Capacitor release. Pin compatible dependencies at implementation time.
2. Add a repeatable build step that copies only runtime assets into `www/` or `dist/`. Preserve the existing script order initially; exclude server scripts, tests, screenshots, and documentation.
3. Initialize Capacitor, select a stable application ID, add Android, and set `webDir` to that asset directory.
4. Bundle the web assets inside the app. Do not depend on the laptop's localhost server or configure a production build to use a development server URL.
5. Install a debug APK on a physical phone and test cold startup in airplane mode.

Capacitor requires a separate web-asset directory containing `index.html`; its sync step copies built assets into the native project. [Installation guide](https://capacitorjs.com/docs/getting-started)

**Done when:** the APK installs, opens without the laptop or internet, and supports one complete household interview using fictional data. Decide whether to keep Capacitor based on this device trial.

## Milestone 3: adapt the phone experience

- Remove the simulated status bar and desktop phone frame in the Android build. Handle system insets, keyboard resizing, and large font settings.
- Implement Android Back behavior for dialogs, interview navigation, and the home screen.
- Introduce small platform adapters for location, file export/share, app lifecycle, and storage, preserving a browser implementation for development.
- Replace browser location capture with native permission-aware capture. Retain accuracy, timestamp, manual pin correction, and protection against late results updating the wrong household. Test denied permission and approximate location. [Geolocation plugin](https://capacitorjs.com/docs/apis/geolocation)
- Replace browser-only Blob download handling with a verified Android save/share flow. Keep exports deliberate and scoped to the selected records.
- Test drawn and typed signatures, date controls, long forms, touch targets, background/resume, and external Google Maps links.

**Done when:** these workflows pass on the smallest/lowest-powered target phone and a second device or emulator configuration.

## Milestone 4: make local records durable

Create a repository layer so renderers no longer directly own persistence. Move record storage to SQLite through a maintained Capacitor-compatible integration, selected after a device proof of persistence, transactions, migrations, and encryption support. Do not assume every SQLite plugin includes encryption or secure key handling. Capacitor's storage guidance distinguishes lightweight settings from database workloads. [Storage guide](https://capacitorjs.com/docs/guides/storage)

Introduce stable UUIDs for households, residents, memberships, visits, and queued operations. Keep human-readable household numbers as display values. Add schema versions and transactional migrations. Convert validation and progress calculations into functions that can be tested without the DOM; retain existing behavior while doing so.

For pilot records, protect encryption keys with an Android-backed secure credential mechanism, define account switching/logout behavior, and provide a tested recovery strategy. Replace immediate permanent removal with an explicit archival/deletion workflow and, when synchronized, server-visible deletion markers.

Browser records do not automatically transfer into the installed app. If demo continuity matters, add an explicit validated JSON import; check format/version, resolve IDs, and back up before migration. Avoid silently importing fictional seed data into a pilot environment.

**Done when:** edits survive force-stop and reboot; an app update migrates existing data; failed writes do not appear as successfully saved; export/restore or the agreed recovery path is tested.

## Milestone 5: add backend and synchronization for a pilot

Use the existing BUILD-PLAN.md proposal of a PostgreSQL-backed service, with Supabase as a candidate, subject to hosting and team constraints. Implement server-enforced barangay/assignment authorization. Never put privileged server credentials in the APK.

Build one complete path first: enumerator signs in, obtains an assignment, edits offline, submits on reconnect, and a supervisor reviews it. Then extend it across all modules.

- Save local edits and an outgoing operation in one database transaction.
- Give each operation a unique ID and base record version so retries cannot create duplicate changes.
- Pull server changes incrementally; represent removals explicitly.
- Separate Draft / Pending upload / Synced / Conflict from Submitted / Approved / Returned.
- Preserve both versions when records conflict; provide a review workflow instead of silently overwriting identity data.
- Start with reliable foreground/manual sync. Treat background sync as a later enhancement with Android-specific testing.
- Build a minimal supervisor interface for assignments, review, conflict resolution, and authorized exports.

**Done when:** interrupted connections and repeated requests do not lose or duplicate records; two-device conflicts are recoverable; unauthorized users cannot read or change another assignment through the API.

## Milestone 6: validate maps, test, and release

Keep saved coordinates and forms usable when the basemap is unavailable. The current `tile.openstreetmap.org` service prohibits offline tile downloads; an offline basemap needs an appropriate provider or self-hosted solution. [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/)

Replace the approximate Cogon rectangle with an authoritative boundary before using location checks to determine jurisdiction. Until then, clearly identify it as a working extent.

Run existing browser regression suites after shared-code changes. Add focused tests for form branching, migrations, atomic saves, sync retries/conflicts, and access control. Run device scenarios for process death, airplane mode, storage failure, keyboard overlap, GPS denial, font scaling, app upgrades, and realistic household volume.

For release, configure the icon/name, versioning, signing, and backup handling; retain the signing key securely. Produce a signed APK for approved direct installation, or an Android App Bundle for Play distribution. Verify current store requirements at release time if using Google Play.

**Demo gate:** installable APK, all agreed fictional-data workflows, offline startup, reliable local saves, working location/export behavior, and documented limitations.

**Pilot gate:** all demo criteria plus verified forms, accounts, authorization, encrypted storage, tested synchronization/recovery, supervisor workflow, and an observed field trial.

## Planning allowance and immediate backlog

Provisional effort ranges for one developer comfortable with JavaScript, working consistently: 1–3 days for scope decisions; 2–5 days for packaging feasibility; 1–2 weeks for device adaptation; 1–2 weeks for durable local storage; another 3–6+ weeks for backend/sync/supervisor work; and 1–2 weeks for pilot/release testing. These are budgeting estimates, not commitments. Learning time, part-time availability, approved-form changes, and hosting decisions can extend them substantially.

The next implementation slice should be small and reviewable:

1. Confirm demo versus pilot scope and target phone.
2. Add the runtime-asset build and Capacitor Android project.
3. Install the first debug APK and test one complete household flow offline.
4. Record device issues and make the architecture decision before expanding implementation.

If React Native is selected instead, replace milestones 2–3 with an Expo/TypeScript project and native screen migration. Preserve the storage, synchronization, and acceptance requirements. Expo's SQLite supports SQLCipher through a native build; this does not work in Expo Go. [Expo SQLite documentation](https://docs.expo.dev/versions/latest/sdk/sqlite/)
