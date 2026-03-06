# Account Import Guides - Complete Reference

This collection contains comprehensive guides for importing all three account types supported by the application.

## What's New

- Bulk import guidance is aligned with the current parser behavior used by `Parse Multiple Accounts`.
- Stalker examples include current optional fields such as HTTP method, timezone, serial/device IDs, and signature handling.
- M3U guidance covers optional M3U-to-Xtreme conversion when compatible credentials are present.
- Cross-links in this file point to the current in-repo guides instead of version-specific release notes.

## 📚 Quick Navigation

### Choose Your Account Type:

| Account Type | Guide | Use Case | Features |
|--------------|-------|----------|----------|
| **Xtreme API** | [XTREME_IMPORT_GUIDE.md](XTREME_IMPORT_GUIDE.md) | IPTV/VOD Services with API | URL + Username/Password |
| **M3U Playlists** | [M3U_IMPORT_GUIDE.md](M3U_IMPORT_GUIDE.md) | Playlist Links & Streams | Playlist URLs ± Credentials |
| **Stalker Portal** | [STALKER_IMPORT_GUIDE.md](STALKER_IMPORT_GUIDE.md) | Legacy Portal Servers | URL + MAC Address ± Device Info |

---

## 🎯 Quick Decision Matrix

**Choose XTREME if:**
- ✅ You have credentials in username/password format
- ✅ You want to use modern IPTV API services
- ✅ Server provides `http://host:port/` access
- ✅ Each account needs unique credentials

**Choose M3U if:**
- ✅ You have playlist links (.m3u8 files)
- ✅ You want to stream from URLs directly
- ✅ You may have embedded credentials in URL parameters
- ✅ You can optionally convert to Xtreme if credentials present

**Choose STALKER PORTAL if:**
- ✅ You have MAC address-based authentication
- ✅ You're accessing legacy Stalker portal servers
- ✅ You need device identification parameters
- ✅ You have serial numbers or device IDs

---

## 📖 Guide Contents

### XTREME_IMPORT_GUIDE.md

**Format:** URL + Username + Password  
**Examples:** 7 detailed scenarios  
**Key Features:**
- Multiple accounts from same server (auto-numbered)
- Various credential label formats
- Mixed URL formats (HTTP/HTTPS, custom ports)
- Practical real-world examples
- Troubleshooting section

**Example:**
```
http://192.168.1.100:2095
User : LoremUser01
Pass : IpsumPass01

http://192.168.1.100:2095
User : DolorUser02
Pass : SitPass02
```

---

### M3U_IMPORT_GUIDE.md

**Format:** M3U URLs (with optional embedded credentials)  
**Examples:** 7 detailed scenarios  
**Key Features:**
- Simple playlist URLs (no credentials)
- Embedded Xtreme credentials in query parameters
- Smart conversion to Xtreme accounts (optional checkbox)
- Mixed convertible and non-convertible URLs
- Various parameter orders and formats
- HTTPS/custom port support

**Example:**
```
http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus

# Result WITHOUT conversion:
Type: M3U8_URL (full URL preserved)

# Result WITH conversion:
Type: XTREME_API
Username: loremuser
Password: lorempass
URL: http://cdn.lorem.com:8080/get.php
```

---

### STALKER_IMPORT_GUIDE.md

**Format:** Portal URL + MAC Address (+ optional device parameters + optional HTTP method + optional timezone)  
**Examples:** 9 detailed scenarios  
**Key Features:**
- Portal URL + MAC address authentication
- Multiple MAC address formats supported
- Optional device parameters (Serial, DeviceId1/2, Signature)
- **NEW:** Optional HTTP method (GET/POST)
- **NEW:** Optional timezone detection (case-insensitive contains matching)
- Multiple accounts per portal (different MACs)
- Mixed MAC formats in single import
- HTTPS/custom port support
- Group by MAC option

**Example:**
```
http://lorem.example.com/stalker_portal/
AA:BB:CC:DD:EE:01
Serial: SN-LOREM-001
POST
Europe/London

http://ipsum.example.com/stalker_portal/
BBCC.DDEE.FFAA
Serial: SN-IPSUM-002
GET
America/New_York
```

---

## 🔄 Comparison Table

| Aspect | Xtreme | M3U | Stalker |
|--------|--------|-----|---------|
| **Primary Identifier** | Username | URL | MAC Address |
| **Authentication** | Username/Password | URL (optional creds) | MAC Address |
| **Optional Parameters** | None | Query params | Serial, Device IDs, HTTP Method, Timezone |
| **Supports HTTPS** | ✅ | ✅ | ✅ |
| **Multiple Servers** | ✅ | ✅ | ✅ |
| **Multiple Per Server** | ✅ (auto-numbered) | ✅ | ✅ (per MAC) |
| **Conversion Feature** | N/A | M3U→Xtreme | N/A |
| **Device Tracking** | No | Limited | Yes (via DeviceId) |
| **HTTP Method Control** | No | No | ✅ (GET/POST) |
| **Timezone Support** | No | No | ✅ (Configurable) |
| **Legacy Support** | Modern API | Playlists | Portal Servers |

---

## 🚀 How to Use (General Steps)

1. **Open Application** → Navigate to Accounts menu
2. **Select "Parse Multiple Accounts"** → Opens bulk import dialog
3. **Choose Account Type:**
   - XTREME → For IPTV API services
   - M3U PLAYLISTS → For playlist URLs
   - STALKER PORTAL → For MAC-based portals
4. **Optional Settings:**
   - M3U: Enable "Convert M3U to Xtreme" if desired
   - Stalker: Enable "Group Accounts by MAC" if desired
5. **Paste Your Data** → Into the text field
6. **Click Save** → Accounts imported

---

## 💡 Common Scenarios

### Scenario 1: Bulk Import Multiple IPTV Services
→ Use: **XTREME_IMPORT_GUIDE.md**
- Import multiple providers
- Auto-numbered for same server
- Each provider has own credentials

### Scenario 2: Convert Playlist URLs to IPTV Accounts
→ Use: **M3U_IMPORT_GUIDE.md** with conversion enabled
- Have M3U URLs with embedded credentials
- Convert to XTREME_API type
- Extract credentials automatically

### Scenario 3: Setup Multiple Devices on Stalker Portal
→ Use: **STALKER_IMPORT_GUIDE.md**
- One portal, multiple devices
- Different MAC address per device
- Optional device identification parameters

### Scenario 4: Mix Playlists and IPTV
→ Use: **M3U_IMPORT_GUIDE.md** with mixed content
- Some URLs are pure playlists
- Some URLs have Xtreme credentials
- Conversion enabled for smart handling

---

## 📋 File Format Best Practices

### General Rules (All Types)
- One URL (+ associated data) per block
- Separate blocks with blank lines
- Case-insensitive for labels
- Whitespace is flexible

### Xtreme
```
URL on first line
Credentials on separate lines or same line
One blank line separates next account
```

### M3U
```
One URL per line OR space-separated on same line
Blank lines optional but recommended
One URL per "account" record
```

### Stalker Portal
```
Portal URL on first line
MAC address on second line
Optional parameters on additional lines
One blank line separates next account
```

---

## ✅ Validation & Error Handling

### Xtreme Validation
- ✅ URL must be valid (http/https)
- ✅ Must have username
- ✅ Must have password
- ❌ Invalid URLs skipped
- ❌ Missing credentials skipped

### M3U Validation
- ✅ URL must be valid (http/https)
- ✅ Can have optional credentials
- ✅ Conversion only if both username AND password present
- ❌ Invalid URLs skipped
- ⚠️ Incomplete conversions handled gracefully

### Stalker Validation
- ✅ URL must be valid (http/https)
- ✅ MAC must be valid format (12 hex chars)
- ✅ Optional device parameters flexible
- ❌ Invalid URLs skipped
- ❌ Invalid MAC addresses skipped

---

## 🔗 Integration Guide

### Import Workflow
```
1. Prepare account data in correct format
2. Open application → Parse Multiple Accounts
3. Select account type from dropdown
4. Configure optional settings (if available)
5. Paste formatted data
6. Click "Save"
7. Accounts appear in account list
```

### Data Organization Tips
- Keep accounts organized by provider/server
- Use consistent formatting throughout
- Store guides alongside account lists
- Document account purposes in comments
- Backup account data regularly

---

## 🐛 Troubleshooting Guide

### General Issues

**Problem:** Accounts not importing
- Check account format matches chosen type
- Verify all required fields present
- Look for invalid characters or encoding
- See specific guide's troubleshooting section

**Problem:** Some accounts imported, some not
- Check error log for details
- Each account validated individually
- Invalid accounts skipped silently
- Check format of skipped accounts

**Problem:** Credentials not extracted/used
- Verify credentials present in data
- Check label names recognized
- For M3U: Ensure conversion enabled
- Check for URL encoding issues

---

## 📞 Support Reference

For detailed help, refer to specific guide:

| Issue Type | Reference |
|------------|-----------|
| URL format questions | All guides have URL examples |
| Credential format | XTREME_IMPORT_GUIDE.md |
| Playlist conversion | M3U_IMPORT_GUIDE.md |
| MAC addresses | STALKER_IMPORT_GUIDE.md |
| Device parameters | STALKER_IMPORT_GUIDE.md |
| Multiple accounts | All guides have examples |
| Port numbers | All guides cover custom ports |
| HTTPS support | All guides mention HTTPS |

---

## 📈 Version History

**February 20, 2026** - Added HTTP Method & Timezone Support
- HTTP Method selection (GET/POST) for Stalker Portal accounts
- Timezone detection with case-insensitive contains matching
- Updated all guides with new examples and documentation
- Enhanced Examples 3a and 9 with complete feature set

**February 20, 2026** - Initial Release
- Complete guides for all three account types
- 22 comprehensive examples across all guides
- Detailed troubleshooting sections
- Comparison matrices and decision guides
- Best practices and recommendations

---

## 📝 Document License & Usage

These guides are designed for:
- ✅ End-user documentation
- ✅ Git repository inclusion
- ✅ Internal knowledge base
- ✅ Team onboarding
- ✅ Reference material
- ✅ Support documentation

**Free to use, modify, and distribute with the application.**

---

## 🎯 Quick Links

- **Xtreme Guide**: [XTREME_IMPORT_GUIDE.md](XTREME_IMPORT_GUIDE.md)
- **M3U Guide**: [M3U_IMPORT_GUIDE.md](M3U_IMPORT_GUIDE.md)
- **Stalker Guide**: [STALKER_IMPORT_GUIDE.md](STALKER_IMPORT_GUIDE.md)

**Choose the guide for your account type and start importing!**

---

*Last Updated: February 20, 2026*  
*Complete Reference for Account Import Features*
