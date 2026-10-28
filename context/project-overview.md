# Project overview

## What it is

A proposal and a working MVP for **customer statement generation on T24 R21** (Temenos core banking on Oracle, TAFJ layout).

A bank asked for statements that are faster and more reliable than the standard T24 route (`STMT.ENT.BOOK` plus T24 statement printing). They must cover individual and corporate customers, handle large volumes and reuse existing infrastructure where possible.

The proposed answer is a separate **Statement Service**: one Java application (API, workers and scheduler roles) that runs beside T24. It reads T24's tables by primary key from a read-only copy of the database (Active Data Guard standby), builds statements, and delivers them through the bank's existing channels. Nothing is installed on T24 and nothing is written to it.

## The two deliverables

| Deliverable | Where | Audience |
|---|---|---|
| Solution design ("Customer Statement Generation Solution — T24 R21") | Claude Doc (master), exported to [docs/solution-design.md](../docs/solution-design.md) | The bank's executive management and its IT, T24, DBA, network and security teams |
| MVP: a working read-only statement service | This repo; deployed on the dev server, port 8097 | Internal demo, and the evidence behind "Already tested" in the doc |

## Scope decisions (made by the user, keep them)

- **One environment only** is sized in the doc: UAT (also used for development and the Phase 1 benchmark). It is one application on 1 VM with 8 vCPU / 16 GB. Production, DR and other environments are sized after the benchmark. A second production copy is mentioned only as an option for availability.
- **One service**, never split into separate API and worker deployables, in text or diagrams.
- **Solution design only.** No team, effort, price, running cost, commercial terms or support contracts. The company and its CEO handle those separately.
- **Phase 1 is what the bank approves now:** a UAT server, a read-only database user and a side-by-side benchmark. The full build follows only if the benchmark meets the pass mark.
- The benchmark pass mark has a **"___ seconds" blank**. The user or the CEO fills it in; never fill it.
- **Later phases:** bank file formats for corporates, SFTP and SWIFT delivery (MT940/942, camt.053). T24 keeps sending its own MT940/942 until then.

## Test data

The dev server holds a T24 **model bank** (test data only, no real customers). Statement history starts on 17 Mar 2025. Test accounts and customers are in the [README](../README.md).
