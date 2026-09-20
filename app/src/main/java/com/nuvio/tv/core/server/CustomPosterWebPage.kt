package com.nuvio.tv.core.server

import android.content.Context
import android.content.res.Configuration
import com.nuvio.tv.R
import java.util.Locale

internal object CustomPosterWebPage {

    fun html(rawContext: Context?): String {
        val context = rawContext?.let { base ->
            val tag = base.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                .getString("locale_tag", null)
            if (!tag.isNullOrEmpty()) {
                val config = Configuration(base.resources.configuration)
                config.setLocale(Locale.forLanguageTag(tag))
                base.createConfigurationContext(config)
            } else base
        }

        val appName = context?.getString(R.string.app_name) ?: "NuvioTV"
        val pageTitle = context?.getString(R.string.web_custom_poster_title) ?: "Custom Poster Source"
        val pageSubtitle = context?.getString(R.string.web_custom_poster_subtitle)
            ?: "Paste a poster URL pattern below and press Save."
        val labelPattern = context?.getString(R.string.web_custom_poster_label) ?: "URL Pattern"
        val placeholder = context?.getString(R.string.web_custom_poster_placeholder)
            ?: "https://example.com/poster?id={id}&id_type={id_type}&type={type}"
        val saveAction = context?.getString(R.string.web_custom_poster_save) ?: "Save"
        val clearAction = context?.getString(R.string.web_custom_poster_clear) ?: "Clear"
        val savedMsg = context?.getString(R.string.web_custom_poster_saved) ?: "Saved!"
        val clearedMsg = context?.getString(R.string.web_custom_poster_cleared) ?: "Cleared"
        val errorEmpty = context?.getString(R.string.web_custom_poster_error_empty) ?: "Enter a URL pattern"
        val errorSave = context?.getString(R.string.web_custom_poster_error_save) ?: "Save failed"
        val errorClear = context?.getString(R.string.web_custom_poster_error_clear) ?: "Clear failed"
        val errorConnection = context?.getString(R.string.web_custom_poster_error_connection) ?: "Connection error"
        val currentLabel = context?.getString(R.string.web_custom_poster_current) ?: "Current:"
        val noPatternLabel = context?.getString(R.string.web_custom_poster_none) ?: "No pattern set"
        val loadingLabel = context?.getString(R.string.web_custom_poster_loading) ?: "Loading..."
        val placeholdersInfo = context?.getString(R.string.web_custom_poster_placeholders_info)
            ?: "Supported placeholders: {id} {id_type} {typed_id} {type} {shape} {imdb_id} {tmdb_id} and more. Use {imdb_id?} for optional. Use {imdb_id|tmdb_id} to declare supported ID types."

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$appName - $pageTitle</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
background:#0d0d0d;color:#e0e0e0;display:flex;justify-content:center;
align-items:center;min-height:100vh;padding:16px}
.card{background:#1a1a1a;border-radius:16px;padding:32px;max-width:560px;width:100%}
h1{font-size:20px;font-weight:600;margin-bottom:4px;color:#fff}
.subtitle{font-size:13px;color:#888;margin-bottom:24px}
label{display:block;font-size:13px;font-weight:500;color:#aaa;margin-bottom:6px}
textarea{width:100%;min-height:100px;padding:12px;border-radius:10px;border:1px solid #333;
background:#111;color:#e0e0e0;font-family:monospace;font-size:13px;resize:vertical;
outline:none;transition:border-color .2s}
textarea:focus{border-color:#7c5cfc}
textarea::placeholder{color:#555}
.actions{display:flex;gap:10px;margin-top:16px}
button{flex:1;padding:12px;border:none;border-radius:10px;font-size:14px;
font-weight:600;cursor:pointer;transition:opacity .15s}
button:active{opacity:.7}
.save{background:#7c5cfc;color:#fff}
.clear{background:#2a2a2a;color:#aaa}
.status{margin-top:12px;font-size:13px;text-align:center;min-height:20px;
transition:color .3s}
.status.ok{color:#4caf50}
.status.err{color:#ef5350}
.current{margin-top:20px;padding:12px;background:#111;border-radius:8px;
font-size:12px;font-family:monospace;color:#888;word-break:break-all;
max-height:60px;overflow:auto}
.current.empty{font-style:italic;color:#555}
.info{margin-top:16px;font-size:11px;color:#666;line-height:1.5}
</style>
</head>
<body>
<div class="card">
  <h1>$pageTitle</h1>
  <p class="subtitle">$pageSubtitle</p>
  <label for="pattern">$labelPattern</label>
  <textarea id="pattern" placeholder="${placeholder.replace("&", "&amp;")}" spellcheck="false"></textarea>
  <div class="actions">
    <button class="save" onclick="save()">$saveAction</button>
    <button class="clear" onclick="clear_()">$clearAction</button>
  </div>
  <div id="status" class="status"></div>
  <div id="current" class="current empty">$loadingLabel</div>
  <div class="info">$placeholdersInfo</div>
</div>
<script>
const ${'$'}s=document.getElementById('status');
const ${'$'}c=document.getElementById('current');
const ${'$'}t=document.getElementById('pattern');
function msg(t,ok){${'$'}s.textContent=t;${'$'}s.className='status '+(ok?'ok':'err');
setTimeout(()=>{${'$'}s.textContent='';${'$'}s.className='status'},3000)}
async function load(){try{const r=await fetch('/api/pattern');const d=await r.json();
if(d.pattern){${'$'}t.value=d.pattern;${'$'}c.textContent=d.pattern;${'$'}c.classList.remove('empty')}
else{${'$'}c.textContent='$noPatternLabel';${'$'}c.classList.add('empty')}}catch(e){${'$'}c.textContent='Error'}}
async function save(){const p=${'$'}t.value.trim();if(!p){msg('$errorEmpty',false);return}
try{const r=await fetch('/api/pattern',{method:'POST',headers:{'Content-Type':'application/json'},
body:JSON.stringify({pattern:p})});if(r.ok){msg('$savedMsg',true);load()}else{msg('$errorSave',false)}}
catch(e){msg('$errorConnection',false)}}
async function clear_(){try{const r=await fetch('/api/clear',{method:'POST'});
if(r.ok){msg('$clearedMsg',true);${'$'}t.value='';load()}else{msg('$errorClear',false)}}
catch(e){msg('$errorConnection',false)}}
load();
</script>
</body>
</html>
""".trimIndent()
    }
}
