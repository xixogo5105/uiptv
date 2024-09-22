# UIPTV User Guide

## Table of Contents
1. [Introduction](#1-introduction)
2. [Installation](#2-installation)
3. [Configuration](#3-configuration)
4. [Managing Accounts](#4-managing-accounts)
5. [Favorites](#5-favorites)
6. [Starting and Stopping the Server](#6-starting-and-stopping-the-server)
7. [Advanced Features](#7-advanced-features)
8. [Troubleshooting and FAQs](#8-troubleshooting-and-faqs)

---

## 1. Introduction
UIPTV is a tool designed to manage and stream IPTV content efficiently. This guide walks you through the installation, configuration, and usage of UIPTV, enabling you to manage accounts, parse data, and customize your viewing experience.

---

## 2. Installation
Follow these steps to install UIPTV:

1. **Download the software** from the official repository: [UIPTV GitHub](https://github.com/xixogo5105/uiptv).
2. **Install Dependencies**: Ensure all required dependencies are installed by following the instructions in the developer's guide.
3. **Run UIPTV**: Start the application after installation.

---

## 3. Configuration

The **Configuration** panel allows you to customize your experience with UIPTV. Here's a breakdown of the settings:

### Player Paths
You can specify up to three different media player paths for playing IPTV channels:

- **Player 1**: Use the **Browse...** button to select the path for your primary media player.
- **Player 2**: Select the path for your second media player.
- **Player 3**: Configure a third media player if needed.

On the left of each player's path field, you'll see **radio buttons**. These buttons allow you to select which player will be used by default when you **double-click a channel** to start playback. Only one player can be selected at a time.

### Filtering Options:
- **Category Filter**: Enter a comma-separated list of categories. Channels from these categories will be excluded from the listing.
- **Channel Filter**: Enter a comma-separated list of specific channel names you want to exclude from the view.

### Font Settings:
- Customize the **font family**, **size**, and **weight** to adjust the look of the UI.
  
### Additional Options:
- **Use Dark Theme**: Toggle dark mode for better visibility in low-light environments.
- **Pause Filtering**: Temporarily stop applying the filters you’ve set.
- **Pause Caching**: Pause the caching of streamed content.
- **Clear Cache**: Manually clear cached data.

Once you’ve configured these options, click **Save** to apply your changes.

---

## 4. Managing Accounts

UIPTV allows users to configure four types of accounts. Here's how to manage each account type:

1. **Stalker Portal Account**: 
   - A portal-based service for IPTV.
   - Users need to enter the portal URL, username, and password in the account configuration.
   
2. **Xtream Codes API Account**: 
   - For Xtream-based services.
   - Users will need to input the API URL, username, password, and optional settings like the stream type.
   
3. **M3U8 Remote URL Account**:
   - Users can configure an M3U8 playlist from a remote URL.
   - Simply paste the URL in the account section, and the channels will be loaded from that online source.
   
4. **M3U8 Local File Account**:
   - For users who have an M3U8 playlist saved locally on their computer.
   - Browse and select the local file to add this playlist to UIPTV.

Each account type can be added, managed, and removed under the **Manage Account** section. You can switch between accounts, enable or disable them, and parse the account details by clicking **Parse Accounts**.

---

## 5. Favorites

The **Favorites** section allows you to manage and quickly access your preferred channels:

- **Adding Favorites**: Right-click a channel and select "Add to Favorites" to mark it for quick access.
- **Managing Favorites**: Use this tab to remove or reorder channels you've added to your favorites list.

---

## 6. Starting and Stopping the Server

UIPTV has a built-in server feature that you can control with the following options:

- **Start Server**: Launch the server by clicking this button. Optionally, you can specify the server port (e.g., 8080) in the provided text box before starting.
- **Stop Server**: Stop the server by clicking this button.

Ensure the server is running for uninterrupted IPTV streaming.

---

## 7. Advanced Features

### Player Selection for Channels
When you double-click a channel to play, the **selected player** (as configured in the **Player Paths** section) will be used for playback. You can switch between players by choosing one of the radio buttons next to each player’s path.

### Caching and Filtering: 
You can pause both caching and filtering options temporarily by enabling the relevant checkboxes in the configuration panel.

### Font and Theme Customization:
Adjust the UI appearance through font family, size, and theme settings.

---

## 8. Troubleshooting and FAQs

- **Issue: Channels not appearing**  
  **Solution**: Check your account configuration under **Manage Accounts** and ensure the correct account type is set up. Also, make sure you’re not filtering out channels unintentionally via the category or channel filters.

- **Issue: The server won’t start**  
  **Solution**: Ensure that the port number specified (e.g., 8080) is not in use by another application, and check if your firewall settings allow UIPTV to run the server.

- **Issue: Double-clicking a channel doesn’t start playback**  
  **Solution**: Verify that you’ve selected the correct media player using the radio buttons next to the player paths. Ensure the player paths are correctly configured, and the player is installed.

- **Issue: Slow performance or delays**  
  **Solution**: Clear the cache periodically by using the **Clear Cache** button in the configuration panel to improve performance.
