# M3U Playlist Import Guide

This file demonstrates the format for bulk importing M3U playlist links and converting them to Xtreme accounts when desired.

## Format Overview

Each M3U playlist URL should be on its own line or separated by spaces. The parser supports:
1. Simple M3U playlist URLs (no credentials)
2. M3U URLs with embedded Xtreme credentials (username/password in query parameters)
3. Optional conversion to Xtreme accounts (when "Convert M3U to Xtreme" is enabled)

## Supported URL Formats

The parser accepts:
- **HTTP URLs**: `http://example.com:8080/playlist.m3u8`
- **HTTPS URLs**: `https://example.com:8080/playlist.m3u8`
- **Custom Ports**: Any port number (8080, 9090, 3000, etc.)
- **Query Parameters**: Various parameter names and orders

## Example 1: Simple M3U Playlists (No Credentials)

```
http://lorem.example.com:8080/playlist.m3u8
http://ipsum.example.com:8080/playlist.m3u8
http://dolor.example.com:8080/playlist.m3u8
http://amet.example.com:8080/playlist.m3u8
http://consectetur.example.com:8080/playlist.m3u8
```

**Result**: All 5 accounts imported as M3U8_URL type
- Account names: `lorem.example.com`, `ipsum.example.com`, `dolor.example.com`, etc.
- Type: M3U8_URL (playlist link only, no conversion)
- Username/Password: Not set

## Example 2: M3U URLs with Xtreme Credentials (Without Conversion)

```
http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus&output=ts
http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass&type=m3u_plus&output=ts
http://cdn.dolor.com:8080/get.php?username=doloruser&password=dolorpass&type=m3u_plus&output=ts
http://cdn.amet.com:9090/get.php?username=ametuser&password=ametpass&type=m3u_plus
http://cdn.consectetur.com:3000/get.php?username=consecuser&password=consecpass&type=m3u_plus
```

**Result**: All 5 accounts imported as M3U8_URL type
- Account names: `cdn.lorem.com`, `cdn.ipsum.com`, `cdn.dolor.com`, etc.
- Type: M3U8_URL (conversion disabled)
- Full URL with credentials preserved
- Credentials NOT extracted

## Example 3: M3U to Xtreme Conversion (✓ Checkbox Enabled)

Same URLs as Example 2, but with **"Convert M3U to Xtreme"** checkbox enabled:

```
http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus&output=ts
http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass&type=m3u_plus&output=ts
http://cdn.dolor.com:8080/get.php?username=doloruser&password=dolorpass&type=m3u_plus&output=ts
http://cdn.amet.com:9090/get.php?username=ametuser&password=ametpass&type=m3u_plus
http://cdn.consectetur.com:3000/get.php?username=consecuser&password=consecpass&type=m3u_plus
```

**Result**: All 5 accounts converted to XTREME_API type
- Account names: `cdn.lorem.com`, `cdn.ipsum.com`, `cdn.dolor.com`, etc.
- Type: XTREME_API (converted!)
- Username: Extracted from `username=` parameter
- Password: Extracted from `password=` parameter
- URL: Base URL only (without query parameters)

**Conversion Details:**
```
Original: http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus
↓ Conversion (when enabled)
Username: loremuser
Password: lorempass
URL: http://cdn.lorem.com:8080/get.php
```

## Example 4: Mixed M3U URLs (Some Convertible, Some Not)

```
http://simple.example.com:8080/playlist.m3u8
http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass
http://another.example.com:8080/video.m3u8
http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass
http://cdn.dolor.com:8080/playlist.m3u8
```

**Result (WITH conversion enabled)**:
- 3 accounts as M3U8_URL type (no credentials): `simple.example.com`, `another.example.com`, `cdn.dolor.com`
- 2 accounts as XTREME_API type (with credentials): `cdn.lorem.com`, `cdn.ipsum.com`

The parser automatically decides which to convert based on presence of username/password parameters.

## Example 5: M3U URLs with Various Parameter Orders

The parser handles parameters in any order:

```
http://cdn.lorem.com:8080/get.php?username=user1&password=pass1&type=m3u_plus&output=ts
http://cdn.ipsum.com:8080/get.php?output=m3u&username=user2&password=pass2&type=m3u_plus
http://cdn.dolor.com:8080/get.php?type=m3u_plus&output=ts&username=user3&password=pass3
http://cdn.amet.com:8080/get.php?password=pass4&username=user4&type=m3u_plus
```

**Result (WITH conversion enabled)**:
All 4 converted to XTREME_API type regardless of parameter order
- Credentials correctly extracted: user1/pass1, user2/pass2, user3/pass3, user4/pass4

## Example 6: HTTPS M3U URLs with Custom Ports

```
https://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass
https://cdn.ipsum.com:9090/get.php?username=ipsumuser&password=ipsumpass
https://cdn.dolor.com:3000/get.php?username=doloruser&password=dolorpass
http://cdn.amet.com:8080/get.php?username=ametuser&password=ametpass
https://cdn.consectetur.com:8080/playlist.m3u8
```

**Result (WITH conversion enabled)**:
- 4 XTREME_API accounts (with credentials)
- 1 M3U8_URL account (no credentials)
- HTTPS protocol preserved
- Port numbers preserved (8080, 9090, 3000)

## Example 7: M3U URLs on Same Line (Space-Separated)

Multiple URLs can be on the same line, separated by spaces:

```
http://lorem.example.com:8080/playlist.m3u8 http://ipsum.example.com:8080/playlist.m3u8 http://dolor.example.com:8080/playlist.m3u8
https://cdn.lorem.com:8080/get.php?username=user1&password=pass1 https://cdn.ipsum.com:8080/get.php?username=user2&password=pass2
```

**Result**:
All 4 URLs parsed correctly, can be on same line or separate lines

## How to Use

1. Open the application
2. Go to "Parse Multiple Accounts" (usually in the Accounts menu)
3. Select **"M3U Playlists"** as the account type
4. Optional: Check **"Convert M3U to Xtreme"** to convert URLs with credentials
5. Paste your M3U URLs in the text field
6. Click "Save"
7. All accounts will be imported

## Conversion Reference

### When to Enable "Convert M3U to Xtreme"?

**Enable it if:**
- You have M3U URLs with embedded username/password parameters
- You want to use them as Xtreme API accounts instead of playlist links
- You need to extract and use the credentials separately

**Disable it if:**
- You want pure M3U playlist URLs
- You prefer to keep the full URL with credentials intact
- You don't need credential extraction

### Conversion Requirements

For M3U URLs to be convertible to Xtreme:
1. URL must contain `get.php?`
2. URL must have `username=` parameter
3. URL must have `password=` parameter

**All three** conditions must be met. If any is missing, the URL remains as M3U8_URL type.

## Examples of Convertible vs Non-Convertible URLs

### ✅ Convertible (Has username AND password)
```
http://cdn.example.com/get.php?username=user&password=pass
http://cdn.example.com/get.php?type=m3u_plus&username=user&password=pass
```

### ❌ Not Convertible (Missing username or password)
```
http://cdn.example.com/get.php?type=m3u_plus
http://cdn.example.com/get.php?username=user (missing password)
http://cdn.example.com/playlist.m3u8 (missing get.php)
```

## Notes

- Each URL should be valid and accessible
- URLs can be on separate lines or space-separated on the same line
- The parser is case-insensitive for query parameter names
- Account names are auto-generated from the hostname
- If multiple accounts share the same hostname, they will be numbered (hostname, hostname (2), hostname (3), etc.)
- Full URLs are preserved in account records for playback
- Conversion is optional and controlled by the checkbox
- The parser handles various parameter orders and combinations
- Special characters in credentials are handled correctly

## Troubleshooting

**Issue**: Account not imported
- **Check**: URL is valid and properly formatted
- **Check**: If conversion enabled, URL has both `username` and `password`

**Issue**: Conversion not working
- **Check**: "Convert M3U to Xtreme" checkbox is enabled
- **Check**: URL contains `get.php?` and query parameters
- **Check**: Both `username=` and `password=` parameters present

**Issue**: Wrong credentials extracted
- **Check**: Username and password values in query parameters
- **Check**: No URL encoding issues (%20, %3D, etc.)


