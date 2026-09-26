# Contract: Canonical form and fingerprint

Shared between the server and the Android client. Both test suites must include the vectors
below. A change to this file is a contract change and needs a new version prefix.

## Canonical string (version `v1`)

Seven fields joined with `|`, encoded as UTF-8:

```
v1|<opType>|<entityId>|<len>:<workerId>|<len>:<blockId>|<quantity>|<timestamp>
```

| Field | Normalisation |
|-------|---------------|
| `opType` | As sent; `CREATE` in the POC. |
| `entityId` | Lower-case hyphenated UUID (8-4-4-4-12). |
| `workerId`, `blockId` | `<byte length of UTF-8>:<bytes>`. No trimming, no case folding, no Unicode normalisation. |
| `quantity` | Decimal integer, no sign, no leading zeros. |
| `timestamp` | Parsed as an ISO-8601 instant (any offset), rendered in UTC as `YYYY-MM-DDTHH:MM:SS.mmmZ`, fractional seconds truncated (not rounded) to milliseconds. |

Excluded on purpose: `opId`, `schemaVersion`, `hlc`, `baseVersion`, device id, arrival time.

## Fingerprint

SHA-256 of the canonical string's UTF-8 bytes. Stored as 32 raw bytes; shown in logs and tests
as lower-case hex.

## Test vectors

All vectors use `opType` `CREATE`.

| # | Input (entityId / workerId / blockId / quantity / timestamp) | Canonical string | SHA-256 |
|---|--------------------------------------------------------------|------------------|---------|
| V1 | `01924b1e-3a2c-7d10-9f3e-aaaaaaaaaaaa` / `W-17` / `B-3` / `12` / `2026-09-25T06:40:12Z` | `v1\|CREATE\|01924b1e-3a2c-7d10-9f3e-aaaaaaaaaaaa\|4:W-17\|3:B-3\|12\|2026-09-25T06:40:12.000Z` | `a0334053178d1531d3f15953711435d35cfa25b682d12d50e0b03b1df1e06d35` |
| V2 | `01924B1E-3A2C-7D10-9F3E-AAAAAAAAAAAA` / `W-17` / `B-3` / `12` / `2026-09-25T06:40:12.000+00:00` | same as V1 | same as V1 |
| V3 | as V1 but quantity `13` | `...\|3:B-3\|13\|2026-09-25T06:40:12.000Z` | `190a3f135e37718f5e059490dfabf9bca0c5ceb728215d38af78ed8050ec5062` |
| V4 | as V1 but workerId `W-17 ` (trailing space) | `...\|5:W-17 \|3:B-3\|12\|...` | `0a445b0ac9aec839a6da3075df002c82aec7a0608101c61224108115bb5025df` |
| V5 | as V1 but blockId `Bloque-Ñ` | `...\|4:W-17\|9:Bloque-Ñ\|12\|...` (9 bytes: `Ñ` is two) | `238d425f5a838b546ad996255d5b252180823dfbfcf4e4929179196a6b73b803` |
| V6 | as V1 but timestamp `2026-09-25T06:40:12.123456Z` | `...\|12\|2026-09-25T06:40:12.123Z` | `ffcba1f6fe01f3e77457d5748f3f44e762180d4ae07342d76241c4e94e4a0577` |
| V7 | as V1 but timestamp `2026-09-25T08:40:12+02:00` | same as V1 | same as V1 |

Expected equalities: V1 = V2 = V7. Everything else distinct. Vectors were generated on
2026-09-25 with an independent implementation (Python `hashlib`) so the Kotlin implementations
on both sides are checked against a third party, not against each other.

## Client-side note

The Android client does not compute fingerprints today; the server is the only consumer in the
POC. The vectors are published now so that if the client ever needs to predict a
`OP_CONTENT_MISMATCH` locally (v1 edit flows), it implements the same rule.
