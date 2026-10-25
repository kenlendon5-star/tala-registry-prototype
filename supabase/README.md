# Supabase development setup

The Supabase workspace is at the repository root, alongside `android-native/`.
The CLI configuration uses local project ID `Capstone` and PostgreSQL 17.
The hosted project selected for this application is `lylsjfmxhrynwgpdttim`
(`https://lylsjfmxhrynwgpdttim.supabase.co`). The local project ID is a Docker
resource identifier, not the hosted project reference.

## Installed tools on this computer

- Supabase CLI 2.120.0: `C:\Users\kenle\.local\bin\supabase.exe`.
- That directory is already on the Windows user PATH.
- Android Studio 2026.2.1 has Supabase Toolkit 1.0.3 installed.
- Docker Engine was reachable during setup.

The CLI executable came from the official `supabase/cli` GitHub release and its
download was checked against the release SHA-256 checksum.

## Android Studio

Restart Android Studio after setup, then open **Settings > Tools > Supabase Toolkit**.
The following paths were written to this computer's `supaToolkit.xml` settings:

| Setting | Value |
| --- | --- |
| Supabase CLI executable | `C:\Users\kenle\.local\bin\supabase.exe` |
| Path to supabase/config.toml | `C:\Users\kenle\Documents\ChatGPT\Capstone\supabase\config.toml` |

Open **View > Tool Windows > Supabase** and refresh the project. The explicit
config path allows the plugin to use this workspace when either the repository
root or `android-native/` is open. This is an IDE-wide setting: update or clear
the config path when working on a different Supabase project. If Android Studio
overwrites the settings while closing, enter these two paths in the settings UI.

The plugin's installed description lists **Supabase Cloud projects, SQL console,
and table data browsing/editing** as Pro features, with a seven-day trial. Cloud
access also requires a personal access token entered directly in the plugin's
settings, where it uses JetBrains PasswordSafe. CLI login and Codex MCP OAuth
are separate from that plugin credential. Setup does not activate a trial or
purchase a license.

## Hosted project login and linking

From the repository root in a terminal:

```powershell
rtk proxy supabase login --output-format text --agent no
rtk proxy supabase link --project-ref lylsjfmxhrynwgpdttim --output-format text --agent no
rtk proxy supabase whoami
```

Complete the browser login and enter its short verification code in the terminal.
Linking stores machine-local connection metadata under ignored `supabase/.temp/`.
It does not apply application migrations. Never put personal access tokens,
database passwords, or service-role keys in the Android application or Git.

## Local development

The local containers have not been started by this setup. To run a local database
and API, keep Docker Desktop running and use the plugin's **Start** action or:

```powershell
rtk proxy supabase start
rtk proxy supabase status
```

The first start downloads container images. Refresh the Supabase tool window
after startup. Local endpoints use API port `54321`, database port `54322`, and
Studio port `54323`. Local containers are separate from the hosted project.
Stop them when finished using `rtk proxy supabase stop` (local data is preserved).

## Android account setup

The app's optional cloud account flow is implemented with `supabase-kt`. Before
running an Android build, copy the ignored template and add the project's
**publishable** key (never a secret or service-role key):

```powershell
Copy-Item android-native/supabase.properties.example android-native/supabase.properties
```

Set `SUPABASE_PUBLISHABLE_KEY` in the copied file from **Supabase Dashboard >
Project Settings > API**. The build embeds this publishable client identifier;
it is safe only because all database and Storage access is enforced by RLS.

Hosted Auth keeps email confirmation enabled and allow-lists
`tala://auth-callback`, which is also declared by the Android activity. Configure
custom SMTP before testing signup with arbitrary enumerator addresses; the built-in
Supabase sender is rate-limited and is not a production mail service.

## Android integration

The hosted project has the tracked migrations applied: authenticated enumerator
profiles, revision-guarded owner-scoped household snapshots, attachment metadata,
and a private attachment bucket. Android queues selected households in Room before
network I/O, uploads referenced attachments, retries pending work, and requires an
explicit confirmation before replacing a newer cloud revision. See
[the cloud-sync guide](../planning/docs/supabase-cloud-sync.md) before changing
the schema or client contract. Create versioned schema changes with
`rtk proxy supabase migration new <name>` and review them before applying them
to the hosted database.

References: [CLI getting started](https://supabase.com/docs/guides/local-development/cli/getting-started),
[CLI releases](https://github.com/supabase/cli/releases/tag/v2.120.0).
