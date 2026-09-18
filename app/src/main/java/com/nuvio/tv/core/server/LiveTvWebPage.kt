package com.nuvio.tv.core.server

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LiveTvWebPage {

    fun getHtml(baseContext: Context): String {
        val tag = baseContext.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        val context = if (!tag.isNullOrEmpty()) {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            baseContext.createConfigurationContext(config)
        } else baseContext

        val isVi = context.resources.configuration.locales.get(0).language == "vi"

        val titleText = if (isVi) "Cài đặt Live TV từ Điện thoại" else "Live TV Setup from Phone"
        val subtitleText = if (isVi) "Nhập danh sách phát M3U và cấu hình tài khoản IPTV cho TV" else "Configure M3U playlists and IPTV accounts for your TV"
        val tabPlaylists = if (isVi) "Danh sách phát M3U" else "M3U Playlists"
        val tabXtream = if (isVi) "Tài khoản Xtream" else "Xtream Codes"
        val tabStalker = if (isVi) "Cổng Stalker" else "Stalker Portal"
        val tabSettings = if (isVi) "Cài đặt" else "Settings"
        val addPlaylistTitle = if (isVi) "Thêm danh sách phát (Link URL)" else "Add Playlist (URL)"
        val uploadPlaylistTitle = if (isVi) "Hoặc tải tệp M3U từ điện thoại" else "Or Upload M3U File from Phone"
        val playlistNameLabel = if (isVi) "Tên danh sách phát" else "Playlist Name"
        val playlistNamePlaceholder = if (isVi) "vd: Kênh Việt Nam, Thể thao..." else "e.g. Sports, News, IPTV"
        val playlistUrlLabel = if (isVi) "Đường dẫn URL (.m3u, .m3u8)" else "Playlist URL (.m3u, .m3u8)"
        val playlistUrlPlaceholder = "https://example.com/playlist.m3u"
        val btnAddPlaylist = if (isVi) "+ Thêm link URL" else "+ Add URL Playlist"
        val btnUploadPlaylist = if (isVi) "📁 Chọn tệp .m3u từ máy" else "📁 Choose .m3u file from device"
        val existingPlaylistsTitle = if (isVi) "Danh sách phát hiện có trên TV" else "Existing Playlists on TV"
        val emptyPlaylistsText = if (isVi) "Chưa có danh sách phát nào. Thêm một link hoặc tệp phía trên!" else "No playlists yet. Add one above!"
        val xtreamTitle = if (isVi) "Cấu hình Xtream Codes API" else "Xtream Codes API Configuration"
        val xtreamDesc = if (isVi) "Kết nối với nhà cung cấp IPTV hỗ trợ chuẩn Xtream Codes" else "Connect with an Xtream Codes compatible IPTV provider"
        val serverUrlLabel = if (isVi) "Địa chỉ máy chủ (Server URL)" else "Server URL"
        val usernameLabel = if (isVi) "Tên đăng nhập (Username)" else "Username"
        val passwordLabel = if (isVi) "Mật khẩu (Password)" else "Password"
        val btnSaveXtream = if (isVi) "Lưu tài khoản Xtream" else "Save Xtream Account"
        val btnClearXtream = if (isVi) "Xóa cấu hình Xtream" else "Clear Xtream Config"
        val stalkerTitle = if (isVi) "Cấu hình Stalker Portal (MAG)" else "Stalker Portal (MAG) Configuration"
        val stalkerDesc = if (isVi) "Kết nối đến cổng Stalker Middleware bằng địa chỉ MAC" else "Connect to Stalker middleware portal with MAC address"
        val portalUrlLabel = if (isVi) "Địa chỉ Portal URL" else "Portal URL"
        val macAddressLabel = if (isVi) "Địa chỉ MAC" else "MAC Address"
        val btnSaveStalker = if (isVi) "Lưu Stalker Portal" else "Save Stalker Portal"
        val btnClearStalker = if (isVi) "Xóa cấu hình Stalker" else "Clear Stalker Config"
        val navToggleLabel = if (isVi) "Hiển thị Live TV trên thanh menu TV" else "Show Live TV in TV Sidebar"
        val navToggleDesc = if (isVi) "Hiển thị lối tắt Live TV trên thanh điều hướng chính của TV" else "Display shortcut in the main TV navigation drawer"
        val dollar = "$"

        return """
<!DOCTYPE html>
<html lang="${if (isVi) "vi" else "en"}">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>$titleText - NuvioTV</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
<style>
  :root {
    --bg-main: #0c0e14;
    --bg-card: #151821;
    --bg-card-hover: #1c202c;
    --bg-input: #10121a;
    --primary: #6366f1;
    --primary-hover: #4f46e5;
    --primary-glow: rgba(99, 102, 241, 0.25);
    --accent: #06b6d4;
    --danger: #ef4444;
    --text-primary: #f3f4f6;
    --text-secondary: #9ca3af;
    --text-muted: #6b7280;
    --border: #232734;
    --border-focus: #6366f1;
    --radius: 12px;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
    background-color: var(--bg-main);
    color: var(--text-primary);
    min-height: 100vh;
    padding-bottom: 40px;
  }
  .container {
    max-width: 540px;
    margin: 0 auto;
    padding: 16px 20px;
  }
  header {
    text-align: center;
    padding: 24px 0 16px;
  }
  .badge {
    display: inline-block;
    padding: 4px 10px;
    background: rgba(99, 102, 241, 0.15);
    color: #818cf8;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 8px;
    border: 1px solid rgba(99, 102, 241, 0.3);
  }
  h1 {
    font-size: 22px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 6px;
  }
  header p {
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.4;
  }
  /* Tab Bar */
  .tabs {
    display: flex;
    background: var(--bg-card);
    border-radius: var(--radius);
    padding: 4px;
    margin: 16px 0 20px;
    border: 1px solid var(--border);
    gap: 4px;
  }
  .tab-btn {
    flex: 1;
    padding: 10px 4px;
    background: transparent;
    border: none;
    color: var(--text-secondary);
    font-size: 13px;
    font-weight: 600;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    text-align: center;
  }
  .tab-btn.active {
    background: var(--primary);
    color: #fff;
    box-shadow: 0 2px 8px var(--primary-glow);
  }
  /* Tab Panels */
  .tab-panel {
    display: none;
  }
  .tab-panel.active {
    display: block;
  }
  /* Cards */
  .card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 18px;
    margin-bottom: 16px;
  }
  .card-title {
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .card-desc {
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 14px;
    line-height: 1.4;
  }
  /* Form Elements */
  .form-group {
    margin-bottom: 14px;
  }
  .form-group:last-child {
    margin-bottom: 0;
  }
  label {
    display: block;
    font-size: 12px;
    font-weight: 500;
    color: var(--text-secondary);
    margin-bottom: 6px;
  }
  input[type="text"], input[type="password"], input[type="url"] {
    width: 100%;
    background: var(--bg-input);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px 14px;
    color: #fff;
    font-size: 14px;
    outline: none;
    transition: border 0.2s, box-shadow 0.2s;
  }
  input[type="text"]:focus, input[type="password"]:focus, input[type="url"]:focus {
    border-color: var(--border-focus);
    box-shadow: 0 0 0 3px var(--primary-glow);
  }
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    padding: 12px 16px;
    background: var(--primary);
    color: #fff;
    border: none;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
    gap: 6px;
    box-shadow: 0 2px 10px var(--primary-glow);
  }
  .btn:hover, .btn:active {
    background: var(--primary-hover);
  }
  .btn-secondary {
    background: transparent;
    border: 1px solid var(--border);
    color: var(--text-secondary);
    box-shadow: none;
    margin-top: 8px;
  }
  .btn-secondary:hover, .btn-secondary:active {
    background: var(--bg-card-hover);
    color: #fff;
  }
  .btn-danger {
    background: transparent;
    border: 1px solid rgba(239, 68, 68, 0.4);
    color: var(--danger);
    box-shadow: none;
    margin-top: 8px;
  }
  .btn-danger:hover, .btn-danger:active {
    background: rgba(239, 68, 68, 0.1);
  }
  /* Playlist items */
  .playlist-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    background: var(--bg-input);
    border: 1px solid var(--border);
    border-radius: 8px;
    margin-bottom: 8px;
    gap: 10px;
  }
  .playlist-info {
    flex: 1;
    min-width: 0;
  }
  .playlist-name {
    font-size: 13px;
    font-weight: 600;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .playlist-url {
    font-size: 11px;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .btn-del {
    background: transparent;
    border: none;
    color: var(--danger);
    cursor: pointer;
    padding: 6px 10px;
    font-size: 14px;
    border-radius: 6px;
  }
  .btn-del:active {
    background: rgba(239, 68, 68, 0.15);
  }
  /* Switch toggle */
  .toggle-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 0;
  }
  .switch {
    position: relative;
    display: inline-block;
    width: 48px;
    height: 26px;
  }
  .switch input { opacity: 0; width: 0; height: 0; }
  .slider {
    position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
    background-color: #2e3344; transition: .3s; border-radius: 26px;
  }
  .slider:before {
    position: absolute; content: ""; height: 20px; width: 20px; left: 3px; bottom: 3px;
    background-color: white; transition: .3s; border-radius: 50%;
  }
  input:checked + .slider { background-color: var(--primary); }
  input:checked + .slider:before { transform: translateX(22px); }
  /* Toast Notification */
  #toast {
    visibility: hidden;
    min-width: 250px;
    background-color: #10b981;
    color: #fff;
    text-align: center;
    border-radius: 8px;
    padding: 12px 16px;
    position: fixed;
    z-index: 1000;
    left: 50%;
    bottom: 30px;
    transform: translateX(-50%);
    font-size: 14px;
    font-weight: 600;
    box-shadow: 0 4px 16px rgba(0,0,0,0.5);
  }
  #toast.show {
    visibility: visible;
    animation: fadein 0.3s, fadeout 0.3s 2.5s;
  }
  @keyframes fadein { from { bottom: 0; opacity: 0; } to { bottom: 30px; opacity: 1; } }
  @keyframes fadeout { from { bottom: 30px; opacity: 1; } to { bottom: 0; opacity: 0; } }
</style>
</head>
<body>
<div class="container">
  <header>
    <div class="badge">NuvioTV Connect</div>
    <h1>$titleText</h1>
    <p>$subtitleText</p>
  </header>

  <div class="tabs">
    <button class="tab-btn active" onclick="switchTab('tab-playlists')">$tabPlaylists</button>
    <button class="tab-btn" onclick="switchTab('tab-xtream')">$tabXtream</button>
    <button class="tab-btn" onclick="switchTab('tab-stalker')">$tabStalker</button>
    <button class="tab-btn" onclick="switchTab('tab-settings')">$tabSettings</button>
  </div>

  <!-- TAB 1: PLAYLISTS -->
  <div id="tab-playlists" class="tab-panel active">
    <div class="card">
      <div class="card-title">➕ $addPlaylistTitle</div>
      <div class="card-desc">${if (isVi) "Dán link M3U / M3U8 từ điện thoại để truyền thẳng lên TV" else "Paste your M3U / M3U8 link from phone to TV"}</div>
      
      <div class="form-group">
        <label for="pl-name">$playlistNameLabel</label>
        <input type="text" id="pl-name" placeholder="$playlistNamePlaceholder">
      </div>
      <div class="form-group">
        <label for="pl-url">$playlistUrlLabel</label>
        <input type="url" id="pl-url" placeholder="$playlistUrlPlaceholder">
      </div>
      <button class="btn" onclick="addPlaylist()">$btnAddPlaylist</button>

      <div style="text-align: center; margin: 16px 0 10px; font-size: 12px; color: var(--text-muted);">--- HOẶC ---</div>

      <input type="file" id="pl-file-input" accept=".m3u,.m3u8,text/*" style="display: none;" onchange="handleFileUpload(event)">
      <button class="btn btn-secondary" onclick="document.getElementById('pl-file-input').click()">$btnUploadPlaylist</button>
    </div>

    <div class="card">
      <div class="card-title">📋 $existingPlaylistsTitle</div>
      <div id="playlist-list">
        <div style="color: var(--text-muted); font-size: 13px; text-align: center; padding: 16px;">
          $emptyPlaylistsText
        </div>
      </div>
    </div>
  </div>

  <!-- TAB 2: XTREAM CODES -->
  <div id="tab-xtream" class="tab-panel">
    <div class="card">
      <div class="card-title">⚡ $xtreamTitle</div>
      <div class="card-desc">$xtreamDesc</div>

      <div class="form-group">
        <label for="xtream-server">$serverUrlLabel</label>
        <input type="url" id="xtream-server" placeholder="http://server.address:8080">
      </div>
      <div class="form-group">
        <label for="xtream-user">$usernameLabel</label>
        <input type="text" id="xtream-user" placeholder="username">
      </div>
      <div class="form-group">
        <label for="xtream-pass">$passwordLabel</label>
        <input type="password" id="xtream-pass" placeholder="••••••••">
      </div>
      <button class="btn" onclick="saveXtream()">$btnSaveXtream</button>
      <button class="btn btn-danger" onclick="clearXtream()">$btnClearXtream</button>
    </div>
  </div>

  <!-- TAB 3: STALKER PORTAL -->
  <div id="tab-stalker" class="tab-panel">
    <div class="card">
      <div class="card-title">🛰️ $stalkerTitle</div>
      <div class="card-desc">$stalkerDesc</div>

      <div class="form-group">
        <label for="stalker-url">$portalUrlLabel</label>
        <input type="url" id="stalker-url" placeholder="http://portal.address/c/">
      </div>
      <div class="form-group">
        <label for="stalker-mac">$macAddressLabel</label>
        <input type="text" id="stalker-mac" placeholder="00:1A:79:XX:XX:XX">
      </div>
      <button class="btn" onclick="saveStalker()">$btnSaveStalker</button>
      <button class="btn btn-danger" onclick="clearStalker()">$btnClearStalker</button>
    </div>
  </div>

  <!-- TAB 4: SETTINGS -->
  <div id="tab-settings" class="tab-panel">
    <div class="card">
      <div class="card-title">⚙️ $tabSettings</div>
      
      <div class="toggle-row">
        <div>
          <div style="font-size: 13px; font-weight: 600; color: #fff;">$navToggleLabel</div>
          <div style="font-size: 11px; color: var(--text-muted);">$navToggleDesc</div>
        </div>
        <label class="switch">
          <input type="checkbox" id="nav-toggle" onchange="toggleNavigation(this.checked)">
          <span class="slider"></span>
        </label>
      </div>
    </div>
  </div>
</div>

<div id="toast">✅ Đã lưu lên TV thành công!</div>

<script>
  let currentConfig = { playlists: [], xtream: null, stalker: null, isNavigationEnabled: true };

  function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.remove('active'));
    event.target.classList.add('active');
    document.getElementById(tabId).classList.add('active');
  }

  function showToast(msg) {
    const toast = document.getElementById("toast");
    toast.innerText = msg;
    toast.className = "show";
    setTimeout(() => { toast.className = toast.className.replace("show", ""); }, 3000);
  }

  async function loadConfig() {
    try {
      const res = await fetch('/api/livetv/config');
      if (res.ok) {
        currentConfig = await res.json();
        renderConfig();
      }
    } catch (e) {
      console.error('Failed to load config', e);
    }
  }

  function renderConfig() {
    // Render playlists
    const listEl = document.getElementById('playlist-list');
    if (!currentConfig.playlists || currentConfig.playlists.length === 0) {
      listEl.innerHTML = '<div style="color: var(--text-muted); font-size: 13px; text-align: center; padding: 16px;">$emptyPlaylistsText</div>';
    } else {
      listEl.innerHTML = currentConfig.playlists.map(p => {
        const pName = p.name || 'Playlist';
        const pUrl = p.isLocal ? 'Tệp cục bộ' : (p.url || '');
        const pId = p.id || p.url;
        return '<div class="playlist-item">' +
          '<div class="playlist-info">' +
            '<div class="playlist-name">' + pName + '</div>' +
            '<div class="playlist-url">' + pUrl + '</div>' +
          '</div>' +
          '<button class="btn-del" onclick="deletePlaylist(\'' + pId + '\')">🗑️</button>' +
        '</div>';
      }).join('');
    }

    // Render Xtream
    if (currentConfig.xtream) {
      document.getElementById('xtream-server').value = currentConfig.xtream.serverUrl || '';
      document.getElementById('xtream-user').value = currentConfig.xtream.username || '';
      document.getElementById('xtream-pass').value = currentConfig.xtream.password || '';
    }

    // Render Stalker
    if (currentConfig.stalker) {
      document.getElementById('stalker-url').value = currentConfig.stalker.portalUrl || '';
      document.getElementById('stalker-mac').value = currentConfig.stalker.macAddress || '';
    }

    // Render Settings
    document.getElementById('nav-toggle').checked = !!currentConfig.isNavigationEnabled;
  }

  async function addPlaylist() {
    const name = document.getElementById('pl-name').value.trim();
    const url = document.getElementById('pl-url').value.trim();
    if (!url) {
      alert('${if (isVi) "Vui lòng nhập đường dẫn Playlist URL!" else "Please enter a Playlist URL!"}');
      return;
    }
    try {
      const res = await fetch('/api/livetv/playlist/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name || url, url: url })
      });
      if (res.ok) {
        document.getElementById('pl-name').value = '';
        document.getElementById('pl-url').value = '';
        showToast('${if (isVi) "✅ Đã thêm playlist lên TV!" else "✅ Playlist added to TV!"}');
        loadConfig();
      } else {
        alert('Error adding playlist');
      }
    } catch (e) {
      alert('Network error: ' + e);
    }
  }

  function handleFileUpload(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async function(e) {
      const content = e.target.result;
      const fileName = file.name;
      const playlistName = fileName.replace(/\.[^/.]+$/, "");
      try {
        const res = await fetch('/api/livetv/playlist/upload', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: playlistName, fileName: fileName, content: content })
        });
        if (res.ok) {
          showToast('${if (isVi) "✅ Đã tải tệp M3U lên TV!" else "✅ M3U file uploaded to TV!"}');
          loadConfig();
        } else {
          alert('Error uploading playlist file');
        }
      } catch (err) {
        alert('Network error: ' + err);
      }
    };
    reader.readAsText(file);
  }

  async function deletePlaylist(id) {
    if (!confirm('${if (isVi) "Bạn có chắc muốn xóa danh sách phát này?" else "Are you sure you want to delete this playlist?"}')) return;
    try {
      const res = await fetch('/api/livetv/playlist/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: id })
      });
      if (res.ok) {
        showToast('${if (isVi) "✅ Đã xóa playlist trên TV!" else "✅ Playlist deleted!"}');
        loadConfig();
      }
    } catch (e) {
      alert('Error: ' + e);
    }
  }

  async function saveXtream() {
    const serverUrl = document.getElementById('xtream-server').value.trim();
    const username = document.getElementById('xtream-user').value.trim();
    const password = document.getElementById('xtream-pass').value.trim();

    try {
      const res = await fetch('/api/livetv/xtream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ serverUrl, username, password, isEnabled: true })
      });
      if (res.ok) {
        showToast('${if (isVi) "✅ Đã lưu Xtream Codes lên TV!" else "✅ Xtream Codes saved to TV!"}');
        loadConfig();
      }
    } catch (e) {
      alert('Error: ' + e);
    }
  }

  async function clearXtream() {
    if (!confirm('${if (isVi) "Xóa thông tin tài khoản Xtream trên TV?" else "Clear Xtream Codes configuration?"}')) return;
    try {
      const res = await fetch('/api/livetv/xtream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ serverUrl: '', username: '', password: '', isEnabled: false })
      });
      if (res.ok) {
        document.getElementById('xtream-server').value = '';
        document.getElementById('xtream-user').value = '';
        document.getElementById('xtream-pass').value = '';
        showToast('${if (isVi) "✅ Đã xóa cấu hình Xtream!" else "✅ Xtream cleared!"}');
        loadConfig();
      }
    } catch (e) {
      alert('Error: ' + e);
    }
  }

  async function saveStalker() {
    const portalUrl = document.getElementById('stalker-url').value.trim();
    const macAddress = document.getElementById('stalker-mac').value.trim();

    try {
      const res = await fetch('/api/livetv/stalker', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ portalUrl, macAddress, isEnabled: true })
      });
      if (res.ok) {
        showToast('${if (isVi) "✅ Đã lưu Stalker Portal lên TV!" else "✅ Stalker Portal saved to TV!"}');
        loadConfig();
      }
    } catch (e) {
      alert('Error: ' + e);
    }
  }

  async function clearStalker() {
    if (!confirm('${if (isVi) "Xóa cấu hình Stalker Portal trên TV?" else "Clear Stalker Portal configuration?"}')) return;
    try {
      const res = await fetch('/api/livetv/stalker', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ portalUrl: '', macAddress: '', isEnabled: false })
      });
      if (res.ok) {
        document.getElementById('stalker-url').value = '';
        document.getElementById('stalker-mac').value = '';
        showToast('${if (isVi) "✅ Đã xóa Stalker Portal!" else "✅ Stalker Portal cleared!"}');
        loadConfig();
      }
    } catch (e) {
      alert('Error: ' + e);
    }
  }

  async function toggleNavigation(enabled) {
    try {
      await fetch('/api/livetv/navigation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isEnabled: enabled })
      });
      showToast(enabled ? '${if (isVi) "✅ Đã bật Live TV trên menu TV" else "✅ Live TV enabled in TV sidebar"}' : '${if (isVi) "✅ Đã tắt Live TV trên menu TV" else "✅ Live TV disabled in TV sidebar"}');
    } catch (e) {
      console.error(e);
    }
  }

  // Load config on startup
  loadConfig();
</script>
</body>
</html>
        """.trimIndent()
    }
}
