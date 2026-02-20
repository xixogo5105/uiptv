# Test Data Example: xtreme_codes.txt

This file demonstrates the format for bulk importing multiple Xtreme accounts.

## Format Overview

Each account should be separated by one or more blank lines. Each account block should contain:
1. A URL (starting with http:// or https://)
2. Username (labeled or unlabeled)
3. Password (labeled or unlabeled)

## Supported Credential Labels

The parser supports various credential label formats:
- **Username**: `user`, `username`, `u`, `name`, `id`
- **Password**: `pass`, `password`, `p`, `pw`

## Example: Multiple Accounts on Same Server

```
http://192.168.1.100:2095
User : LoremUser01
Pass : IpsumPass01

http://192.168.1.100:2095
User : DolorUser02
Pass : SitPass02

http://192.168.1.100:2095
User : AmetUser03
Pass : ConsPass03
```

Result: All 3 accounts will be imported with unique names:
- `192.168.1.100` (first account)
- `192.168.1.100 (2)` (second account)
- `192.168.1.100 (3)` (third account)

## Example: Multiple Servers

```
http://192.168.1.100:2095
User : ServerAUser
Pass : ServerAPass

http://192.168.2.50:3000
User : ServerBUser
Pass : ServerBPass

http://192.168.3.200:8080
User : ServerCUser
Pass : ServerCPass
```

Result: All 3 accounts will be imported with their respective server names:
- `192.168.1.100`
- `192.168.2.50`
- `192.168.3.200`

## Example: Mixed Credential Label Formats

```
http://192.168.1.100:2095 username=User1 password=Pass1

http://192.168.2.50:3000 user=User2 pass=Pass2

http://192.168.3.200:8080 u=User3 pw=Pass3
```

Result: All 3 accounts will be imported with their credentials properly extracted.

## How to Use

1. Open the application
2. Go to "Parse Multiple Accounts" (usually in the UI menu)
3. Select "Xtreme" as the account type
4. Paste your accounts in the text field
5. Click "Save"
6. All accounts will be imported with unique names

## Notes

- Each account must have a URL, username, and password
- Blank lines separate account blocks
- Credentials can be on the same line as the URL or on separate lines
- The parser is case-insensitive for credential labels
- Account names are auto-generated from the server URL
- If multiple accounts share the same URL, they will be numbered automatically

