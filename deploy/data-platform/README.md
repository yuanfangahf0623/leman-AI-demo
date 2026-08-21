# Single-tenant data platform deployment

This directory contains the production layout used on `192.168.19.239`.

- Application: Java 17 systemd service on `127.0.0.1:48080`
- UI: nginx on port 80
- Metadata database: Ubuntu MySQL 8 service on `127.0.0.1:3307`
- Warehouse: existing Doris FE on `127.0.0.1:9030`
- Synchronization: existing SeaTunnel 2.3.13 installation, submitted through a local-mode wrapper
- Tenancy: disabled in the UI; platform tables are single-tenant and contain no tenant column

Secrets are generated on the server and stored below
`/opt/data-platform/shared/config` with mode `0600`. They must never be added to this repository.

Releases live in `/opt/data-platform/releases/<timestamp>`. The
`/opt/data-platform/current` symlink makes application and UI rollback atomic.

## Factory Daren ERP synchronization

`factory-daren-sync.sh` triggers data-platform jobs through the authenticated
admin API, so every scheduled execution is recorded in the platform run log.
Install the script under `/opt/data-platform/shared/bin` and the accompanying
service/timer units under `/etc/systemd/system`.

- Rolling incremental jobs run every two hours and re-read the most recent
  three business days. Doris unique keys make the overlap idempotent.
- Tables without a reliable business-date watermark are fully refreshed every
  night.
- Incremental tables receive a full reconciliation every Sunday so late edits
  and source-side deletions are eventually reflected.
- All schedules share one `flock` lock and use four workers by default, avoiding
  overlapping ERP scans. Set `FACTORY_DAREN_SYNC_PARALLELISM` in the protected
  production environment to override this value.

## Business data dictionary

The `数据字典` menu scans JDBC metadata into `dp_metadata_table` and
`dp_metadata_field`. A refresh updates structural information but preserves
definitions already confirmed by a user. Source comments are preferred when
available; otherwise the platform creates an explicitly unconfirmed business
name suggestion. Sensitivity labels are conservative candidates and must be
reviewed by a data owner.

For Factory Daren, the scan excludes the same 14 technical log tables used by
the synchronization inventory, links every discovered table to its Doris ODS
table, and marks the watermark columns of active incremental jobs as increment
field candidates.

The review workflow exports a UTF-8 TSV file containing source coordinates,
business definitions, classification, sensitivity, increment flags, review
status, and definition source. Import accepts at most 50,000 rows and only
applies rows explicitly marked `CONFIRMED`; every update is also scoped to the
selected data source and an existing field ID. Definitions are tagged as
`RULE`, `SOURCE`, `ERP_CONFIG`, `MANUAL`, or `IMPORT` so generated suggestions
remain distinguishable from evidence-backed or reviewed content.

Factory Daren also reads non-empty definitions from `dbo.HrmFormulaField`.
Configured aliases are accepted only when they resolve to a verified physical
column in the same table; unresolved configuration is ignored rather than
being promoted to a confirmed definition.
