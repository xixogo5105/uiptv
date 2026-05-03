# Stalker Portal Import Guide

This guide documents the current bulk-import behavior for Stalker Portal accounts.

## What's New

- Stalker import now supports parser-accurate `serial`, `device id 1`, `device id 2`, `signature`, `HTTP method`, and `timezone` extraction.
- `DEVICE ID 1/2` input is treated as a combined value and is applied to both device-id fields.
- The **Group Account(s) by MAC Address** option is now device-aware:
  - MAC-only entries group together by portal URL
  - entries with extra device parameters stay separate unless a later import has the same extra-parameter identity
- After import, you can optionally launch verification/reload for the newly created accounts.

## Format Overview

Each Stalker account block contains:

1. Portal URL (`http://...` or `https://...`)
2. MAC address
3. Optional lines for:
   - `Serial`
   - `Device ID 1`
   - `Device ID 2`
   - `Signature`
   - `GET` or `POST`
   - timezone text containing a valid timezone id such as `Europe/London`

Blank lines are recommended between logical account blocks, but the parser mainly starts a new account when it sees a new MAC address.

## Supported MAC Address Formats

- `AA:BB:CC:DD:EE:FF`
- `AABB.CCDD.EEFF`
- case-insensitive input is accepted

## Supported Device Parameter Shapes

The current parser expects uppercase hex-like values for device-bound fields:

- **Serial Number**: 10-32 hex chars, for example `AABBCCDDEE11`
- **Device ID 1**: 32-64 hex chars
- **Device ID 2**: 10-64 hex chars
- **Signature**: 64 hex chars

Common accepted labels are flexible and case-insensitive:

- `serial`, `sn`, `serialcut`
- `device id 1`, `id 1`
- `device id 2`, `id 2`
- `signature`, `signature1`, `sig`

## Grouping Rules

When **Group Account(s) by MAC Address** is enabled:

1. **MAC-only entries**
   - Accounts with only portal URL + MAC address group together by portal URL.
   - Example result: one account with `macAddressList=MAC1,MAC2,MAC3`.

2. **Entries with extra params**
   - If `Serial`, `Device ID 1`, `Device ID 2`, or `Signature` is present, the account is treated as device-bound.
   - That account stays separate from MAC-only accounts on the same portal.

3. **Matching extra-param entries**
   - If a later import on the same portal has the same extra-parameter identity, its MAC is appended to that same separate account.

4. **Different extra-param entries**
   - Different `Serial` / `Device ID` / `Signature` values create a different separate account.

This behavior is intentional because Stalker extra parameters are usually tied to a specific device identity.

## Example 1: MAC-Only Grouping

```
http://portal.example/c
00:11:22:33:44:10
00:11:22:33:44:11
00:11:22:33:44:12
```

Result with grouping enabled:

- one account for `portal.example`
- `macAddressList=00:11:22:33:44:10,00:11:22:33:44:11,00:11:22:33:44:12`

## Example 2: Separate Device-Bound Account

```
http://portal.example/c
00:11:22:33:44:20
Serial: AABBCCDDEE11
Device ID 1: AABBCCDDEEFF001122334455667788AA
Device ID 2: AABBCCDDEEFF001122334455667788BB
Signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
POST
Europe/London
```

Result:

- one separate account for that device identity
- it does **not** merge into the MAC-only grouped account for the same portal

## Example 3: Same Extra Params, More MACs

Initial import:

```
http://portal.example/c
00:11:22:33:44:20
Serial: AABBCCDDEE11
Device ID 1: AABBCCDDEEFF001122334455667788AA
Device ID 2: AABBCCDDEEFF001122334455667788BB
Signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
```

Later import:

```
http://portal.example/c
00:11:22:33:44:21
Serial: AABBCCDDEE11
Device ID 1: AABBCCDDEEFF001122334455667788AA
Device ID 2: AABBCCDDEEFF001122334455667788BB
Signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
```

Result with grouping enabled:

- one separate device-bound account
- `macAddressList=00:11:22:33:44:20,00:11:22:33:44:21`

## Example 4: Different Extra Params, Separate Accounts

```
http://portal.example/c
00:11:22:33:44:30
Serial: AABBCCDDEE11
Device ID 1: AABBCCDDEEFF001122334455667788AA
Device ID 2: AABBCCDDEEFF001122334455667788BB
Signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899

http://portal.example/c
00:11:22:33:44:31
Serial: 112233445566
Device ID 1: 11223344556677889900AABBCCDDEEFF
Device ID 2: FFEEDDCCBBAA00998877665544332211
Signature: 99887766554433221100FFEEDDCCBBAA99887766554433221100FFEEDDCCBBAA
```

Result:

- two separate device-bound accounts for the same portal
- each keeps its own MAC/device identity

## Example 5: `DEVICE ID 1/2`

```
http://portal.example/c
00:11:22:33:44:40
DEVICE ID 1/2 : AABBCCDDEEFF001122334455667788AABBCCDDEEFF001122334455667788CC
```

Result:

- `deviceId1` and `deviceId2` are both set to the same parsed value
- because extra params are present, this account is treated as device-bound

## Example 6: Mixed MAC-Only and Device-Bound Imports

```
http://portal.example/c
00:11:22:33:44:10
00:11:22:33:44:11

http://portal.example/c
00:11:22:33:44:20
Serial: AABBCCDDEE11
Device ID 1: AABBCCDDEEFF001122334455667788AA
Device ID 2: AABBCCDDEEFF001122334455667788BB
Signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
```

Result with grouping enabled:

- one MAC-only grouped account for `00:11:22:33:44:10,00:11:22:33:44:11`
- one separate device-bound account for `00:11:22:33:44:20`

## How to Use

1. Open **Parse Multiple Accounts**
2. Select **Stalker Portal**
3. Paste the account blocks
4. Enable or disable **Group Account(s) by MAC Address**
5. Optional: keep **Start verification after parsing** enabled
6. Click **Save**

## Troubleshooting

**Issue: extra fields are ignored**
- Check that `Serial`, `Device ID`, and `Signature` values use hex-like characters only
- Check that the value lengths match the parser expectations above

**Issue: accounts merged unexpectedly**
- If you enabled grouping and the entries are MAC-only, grouping by portal URL is expected
- If device-bound accounts merged, check whether the extra-parameter identity is actually identical

**Issue: accounts did not group**
- MAC-only grouping only applies when the checkbox is enabled
- device-bound accounts only group when their extra parameters match exactly
