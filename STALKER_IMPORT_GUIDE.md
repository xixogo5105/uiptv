# Stalker Portal Import Guide

This file demonstrates the format for bulk importing Stalker Portal accounts with MAC addresses and optional device parameters.

## Format Overview

Each Stalker Portal account requires:
1. A Portal URL (starting with http:// or https://)
2. A MAC Address (in format: XX:XX:XX:XX:XX:XX or XXXX.XXXX.XXXX)
3. Optional device parameters (Serial, Device ID 1/2, Signature)

Portal URLs and MAC addresses should be separated by blank lines for each account block. The parser will group them together.

## Supported MAC Address Formats

The parser supports multiple MAC address formats:
- **Standard Format**: `AA:BB:CC:DD:EE:FF` (colon-separated)
- **Dot Format**: `AABB.CCDD.EEFF` (dot-separated)
- **Case Insensitive**: `aa:bb:cc:dd:ee:ff` or `AA:BB:CC:DD:EE:FF`

## Supported Device Parameters

Optional parameters that can enhance Stalker accounts:
- **Serial Number**: Device serial identifier
- **Device ID 1**: Primary device identifier
- **Device ID 2**: Secondary device identifier
- **Signature**: Device signature/authentication
- **HTTP Method**: HTTP method for API requests (GET or POST) - *defaults to GET*
- **Timezone**: Timezone identifier for the device - *defaults to Europe/London*
- **Supported Labels**: `Serial`, `SERIALCUT`, `DeviceId1`, `DeviceId2`, `Signature` (case-insensitive)

## Example 1: Basic Stalker Portal Accounts

```
http://lorem.example.com/stalker_portal/
AA:BB:CC:DD:EE:01

http://ipsum.example.com/stalker_portal/
BB:CC:DD:EE:FF:02

http://dolor.example.com/stalker_portal/
CC:DD:EE:FF:AA:03

http://amet.example.com/stalker_portal/
DD:EE:FF:AA:BB:04
```

**Result**: 4 Stalker Portal accounts imported
- Account names: Generated from portal URL (e.g., `lorem.example.com`)
- Type: STALKER_PORTAL
- MAC Addresses: Each account has unique MAC
- Device parameters: None (optional)

## Example 2: Stalker Portal with Dot-Format MAC Addresses

```
http://lorem.example.com:8080/stalker/
AABB.CCDD.EEFF

http://ipsum.example.com:8080/stalker/
BBCC.DDEE.FFAA

http://dolor.example.com:8080/stalker/
CCDD.EEFF.AABB

http://amet.example.com:8080/stalker/
DDEE.FFAA.BBCC
```

**Result**: 4 Stalker Portal accounts with dot-format MAC addresses
- MAC addresses automatically recognized and accepted
- Both colon and dot formats work interchangeably

## Example 3: Stalker Portal with Device Serial Numbers

```
http://lorem.example.com:8080/stalker/
AA:BB:CC:DD:EE:01
Serial: SN-LOREM-2024-001

http://ipsum.example.com:8080/stalker/
BB:CC:DD:EE:FF:02
Serial: SN-IPSUM-2024-002

http://dolor.example.com:8080/stalker/
CC:DD:EE:FF:AA:03
Serial: SN-DOLOR-2024-003
```

**Result**: 3 Stalker Portal accounts with serial numbers
- MAC address: Set for each account
- Serial number: Extracted and stored
- Can be used for device identification

## Example 3a: Stalker Portal with HTTP Method and Timezone

```
http://lorem.example.com:8080/stalker/
AA:BB:CC:DD:EE:01
POST
Europe/London

http://ipsum.example.com:8080/stalker/
BB:CC:DD:EE:FF:02
GET
America/New_York

http://dolor.example.com:8080/stalker/
CC:DD:EE:FF:AA:03
POST
Asia/Tokyo
```

**Result**: 3 Stalker Portal accounts with HTTP method and timezone
- HTTP Method: Set to POST or GET (case-insensitive, defaults to GET)
- Timezone: Automatically detected from line content (case-insensitive)
- Timezone detection uses contains matching: works with various formats:
  - `Europe/London` (exact)
  - `timezone: Europe/London` (with label)
  - `Set Europe/London timezone` (embedded)
- Both parameters are optional and independent

## Example 4: Stalker Portal with All Device Parameters

```
http://lorem.example.com:8080/stalker/
AA:BB:CC:DD:EE:01
Serial: SN-LOREM-001
DeviceId1: DEVICE-LOREM-001
DeviceId2: SECONDARY-LOREM-001
Signature: sig_lorem_2024_01

http://ipsum.example.com:8080/stalker/
BB:CC:DD:EE:FF:02
Serial: SN-IPSUM-002
DeviceId1: DEVICE-IPSUM-002
DeviceId2: SECONDARY-IPSUM-002
Signature: sig_ipsum_2024_02

http://dolor.example.com:8080/stalker/
CC:DD:EE:FF:AA:03
Serial: SN-DOLOR-003
DeviceId1: DEVICE-DOLOR-003
DeviceId2: SECONDARY-DOLOR-003
Signature: sig_dolor_2024_03
```

**Result**: 3 Stalker Portal accounts with complete device information
- All device parameters extracted and stored
- Can be used for full device identification and authentication

## Example 5: Multiple Accounts Same Portal (Different MACs)

```
http://192.168.1.100:8080/stalker_portal/
AA:BB:CC:DD:EE:01

http://192.168.1.100:8080/stalker_portal/
BB:CC:DD:EE:FF:02

http://192.168.1.100:8080/stalker_portal/
CC:DD:EE:FF:AA:03

http://192.168.1.100:8080/stalker_portal/
DD:EE:FF:AA:BB:04
```

**Result**: 4 accounts on same portal with different MAC addresses
- All from same portal: `192.168.1.100`
- Each has unique MAC: Allows multiple devices on same portal
- Account names auto-numbered: `192.168.1.100`, `192.168.1.100 (2)`, `192.168.1.100 (3)`, `192.168.1.100 (4)`

## Example 6: Mixed MAC Address Formats

```
http://lorem.example.com/stalker_portal/
AA:BB:CC:DD:EE:01
Serial: SN-001

http://ipsum.example.com/stalker_portal/
BBCC.DDEE.FFAA
Serial: SN-002

http://dolor.example.com/stalker_portal/
CC:DD:EE:FF:AA:03
DeviceId1: DEVICE-003

http://amet.example.com/stalker_portal/
DDEE.FFAA.BBCC
DeviceId1: DEVICE-004
DeviceId2: SECONDARY-004
```

**Result**: 4 accounts with mixed MAC formats and various device parameters
- Both colon and dot MAC formats accepted
- Device parameters optional and flexible
- Each account can have different parameters

## Example 7: Stalker Portal with HTTPS and Custom Ports

```
https://secure.lorem.com:8443/stalker_portal/
AA:BB:CC:DD:EE:01

https://secure.ipsum.com:9000/stalker/
BB:CC:DD:EE:FF:02

http://legacy.dolor.com:8080/stalker_portal/
CC:DD:EE:FF:AA:03

https://cdn.amet.com:443/stalker_portal/
DD:EE:FF:AA:BB:04
```

**Result**: 4 accounts with various protocols and ports
- HTTPS protocol supported
- Custom ports supported (8443, 9000, 443, etc.)
- Localhost URLs also supported

## Example 8: Stalker Portal with Partial Device Information

```
http://lorem.example.com:8080/stalker_portal/
AA:BB:CC:DD:EE:01
Serial: SN-LOREM-001

http://ipsum.example.com:8080/stalker_portal/
BB:CC:DD:EE:FF:02
DeviceId1: DEVICE-IPSUM-002
DeviceId2: SECONDARY-IPSUM-002

http://dolor.example.com:8080/stalker_portal/
CC:DD:EE:FF:AA:03
Signature: sig_dolor_2024

http://amet.example.com:8080/stalker_portal/
DD:EE:FF:AA:BB:04
```

**Result**: 4 accounts with different combinations of device parameters
- Each account can have different subset of parameters
- Parser handles optional parameters gracefully
- Missing parameters left as null

## Example 9: Complete Stalker Portal with All Features

```
http://lorem.example.com:8080/stalker_portal/
AA:BB:CC:DD:EE:01
Serial: SN-LOREM-001
DeviceId1: DEVICE-LOREM-001
DeviceId2: SECONDARY-LOREM-001
Signature: sig_lorem_2024_01
POST
Europe/London

http://ipsum.example.com:8080/stalker_portal/
BBCC.DDEE.FFAA
Serial: SN-IPSUM-002
DeviceId1: DEVICE-IPSUM-002
POST
America/New_York

http://dolor.example.com:8080/stalker_portal/
CC:DD:EE:FF:AA:03
DeviceId1: DEVICE-DOLOR-003
DeviceId2: SECONDARY-DOLOR-003
GET
Asia/Tokyo

http://amet.example.com:8080/stalker_portal/
DD:EE:FF:AA:BB:04
Europe/Paris
```

**Result**: 4 Stalker Portal accounts with complete configuration
- MAC address: Always required, supports both formats
- Device parameters: Optional (Serial, DeviceId1/2, Signature)
- HTTP Method: Optional, defaults to GET
  - Set to POST if line contains "POST" (case-insensitive)
  - Otherwise defaults to GET
- Timezone: Optional, defaults to Europe/London
  - Automatically detected using case-insensitive contains matching
  - Works with any valid Java ZoneId identifier
  - Can appear anywhere in the line
- All features can be combined flexibly

## How to Use

1. Open the application
2. Go to "Parse Multiple Accounts" (usually in the Accounts menu)
3. Select **"Stalker Portal"** as the account type
4. Optional: Check **"Group Accounts by MAC"** for special grouping behavior
5. Paste your Stalker Portal accounts in the text field
6. Click "Save"
7. All accounts will be imported with their parameters

## Optional: Group Accounts by MAC

When enabled, accounts with the same MAC address on different portals are grouped together.

**Example:**
```
http://portal1.example.com/
AA:BB:CC:DD:EE:01

http://portal2.example.com/
AA:BB:CC:DD:EE:01
```

**With "Group Accounts by MAC" enabled**:
- These may be grouped as a single account with multiple portals
- Useful for managing the same device across multiple Stalker portals

## Supported Parameter Labels

The parser recognizes various label formats (case-insensitive):
- **Serial Number**: `Serial`, `SERIAL`, `Serial Number`, `SERIALCUT`
- **Device ID 1**: `DeviceId1`, `deviceid1`, `Device ID 1`, `DEVICE_ID_1`
- **Device ID 2**: `DeviceId2`, `deviceid2`, `Device ID 2`, `DEVICE_ID_2`
- **Signature**: `Signature`, `SIGNATURE`, `Sig`, `SIG`

## Notes

- Portal URLs should be complete and valid
- MAC address format is flexible (colon or dot separated)
- MAC addresses must be valid (12 hexadecimal characters)
- All parameters are optional except URL and MAC address
- Account names are auto-generated from portal URL
- If multiple accounts have same URL + MAC, they will still be stored
- Device parameters help with portal authentication
- Portal response times may vary depending on network

## Troubleshooting

**Issue**: Account not imported
- **Check**: Portal URL is valid and accessible
- **Check**: MAC address format is correct (12 hex chars)
- **Check**: Blank line separates each account block

**Issue**: MAC address not recognized
- **Check**: MAC format is correct (AA:BB:CC:DD:EE:FF or AABB.CCDD.EEFF)
- **Check**: All 12 hexadecimal characters present
- **Check**: No extra spaces or special characters

**Issue**: Device parameters not extracted
- **Check**: Parameter label is recognized (Serial, DeviceId1, etc.)
- **Check**: Parameter is on separate line or clearly separated
- **Check**: No special characters in parameter values

**Issue**: HTTP method not set correctly
- **Check**: Line contains only "POST" (case-insensitive) to set POST
- **Check**: Without POST keyword, defaults to GET
- **Check**: HTTP method line should be separate from other parameters

**Issue**: Timezone not detected
- **Check**: Valid Java ZoneId identifier (e.g., Europe/London, America/New_York)
- **Check**: Timezone can appear anywhere in the line due to contains matching
- **Check**: Case-insensitive (europe/london works same as Europe/London)
- **Check**: Use valid ZoneId format (Region/City, not abbreviations like EST)

**Issue**: Portal not connecting
- **Check**: Portal URL is correct and online
- **Check**: Network connectivity to portal
- **Check**: Portal credentials (MAC address) are valid
- **Check**: Portal allows connections from your network


