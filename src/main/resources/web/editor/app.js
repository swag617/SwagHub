/* SwagHub Web Editor — app.js
 * Vanilla JS, no frameworks. Uses fetch() for all API calls.
 *
 * Login is handled entirely by SwagAPI's shared session cookie — by the time this
 * page loads, SwagAPI has already authenticated the visitor (or auth is disabled).
 * See /swagapi/auth/status and /login?redirect=... for the shared auth contract
 * (mirrors SwagCore's app.js byte-for-byte on the redirect behavior).
 *
 * NOTE: this file deliberately avoids ES6 template literals (${...}), matching the
 * sibling SwagCore dashboard's own convention, even though this project's pom.xml
 * already excludes web/** from Maven's resource filtering (so it isn't strictly
 * required here) — kept for consistency across the Swag ecosystem's web editors.
 */

// Current directory this page was served from (e.g. "/swagapi/swaghub/"). Relative
// "api/..." paths below resolve against this so the editor works regardless of what
// prefix IWebService mounts it under.
var API_BASE = window.location.pathname.replace(/[^/]*$/, '');

// ─── Fetch helper ───────────────────────────────────────────────

function apiFetch(path, options) {
    var opts = options || {};
    opts.credentials = 'include';
    opts.headers = Object.assign({'Content-Type': 'application/json'}, opts.headers || {});
    return fetch(API_BASE + path, opts).then(function(r) {
        if (r.status === 401) {
            window.location = '/login?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
            throw new Error('Unauthorized');
        }
        if (r.status === 403) {
            throw new Error('You do not have permission to do this (requires swaghub.dashboard.view/edit).');
        }
        if (r.status === 204) {
            return {};
        }
        return r.text().then(function(text) {
            var data = {};
            if (text) {
                try { data = JSON.parse(text); } catch (e) { data = {}; }
            }
            if (!r.ok) {
                throw new Error(data.error || ('Request failed (HTTP ' + r.status + ')'));
            }
            return data;
        });
    });
}

// ─── Startup / who-am-i ─────────────────────────────────────────

document.addEventListener('DOMContentLoaded', function() {
    loadWhoAmI();
    loadStatus();
});

function loadWhoAmI() {
    fetch('/swagapi/auth/status', {credentials: 'include'}).then(function(r) {
        return r.json();
    }).then(function(d) {
        var el = document.getElementById('whoami');
        if (d && d.authenticated && d.username) {
            el.textContent = 'Signed in as ' + d.username;
        } else {
            el.textContent = '';
        }
    }).catch(function() {});
}

// ─── Tab navigation ──────────────────────────────────────────────

var TAB_LOADERS = {
    'tab-status': loadStatus,
    'tab-core': loadCore,
    'tab-scoreboard': loadScoreboard,
    'tab-tablist': loadTablist,
    'tab-announcements': loadAnnouncements
};

document.querySelectorAll('.tab-btn').forEach(function(btn) {
    btn.addEventListener('click', function() {
        activateTab(btn.getAttribute('data-tab'));
    });
});

function activateTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(function(b) {
        b.classList.toggle('active', b.getAttribute('data-tab') === tabId);
    });
    document.querySelectorAll('.tab-content').forEach(function(s) {
        var isActive = s.id === tabId;
        s.classList.toggle('active', isActive);
        s.classList.toggle('hidden', !isActive);
    });
    var loader = TAB_LOADERS[tabId];
    if (loader) loader();
}

// ─── Status ──────────────────────────────────────────────────────

function loadStatus() {
    apiFetch('api/status').then(function(d) {
        document.getElementById('status-summary').innerHTML =
            '<div>Version: <b>' + esc(d.version) + '</b></div>' +
            '<div>Server role: <b>' + esc(d.serverRole) + '</b></div>';

        var body = document.getElementById('status-modules-body');
        body.innerHTML = '';
        var modules = d.modules || [];
        if (!modules.length) {
            body.innerHTML = '<tr><td colspan="4" class="muted center">No modules registered.</td></tr>';
        }
        modules.forEach(function(m) {
            var tr = document.createElement('tr');
            var enabledBadge = m.enabled
                ? '<span class="badge badge-green">enabled</span>'
                : '<span class="badge badge-gray">disabled</span>';
            var yieldedCell = m.yieldedTo
                ? '<span class="badge badge-red">' + esc(m.yieldedTo) + '</span>'
                : '<span class="muted">—</span>';
            tr.innerHTML =
                '<td>' + esc(m.configKey) + '</td>' +
                '<td>' + enabledBadge + '</td>' +
                '<td><span class="badge badge-purple">' + esc(m.override) + '</span></td>' +
                '<td>' + yieldedCell + '</td>';
            body.appendChild(tr);
        });

        var proxy = document.getElementById('status-proxy');
        if (d.proxy && d.proxy.available) {
            proxy.innerHTML = 'Enabled: <b>' + (d.proxy.enabled ? 'yes' : 'no') + '</b> &mdash; '
                + 'Total online (network-wide): <b>' + d.proxy.totalOnline + '</b>';
        } else {
            proxy.innerHTML = '<span class="muted">Proxy service unavailable.</span>';
        }

        var update = document.getElementById('status-update');
        if (!d.update || !d.update.checked) {
            update.innerHTML = '<span class="muted">Update checking is disabled.</span>';
        } else if (d.update.available) {
            update.innerHTML = 'Update available: <b>' + esc(d.update.latestVersion)
                + '</b> (current ' + esc(d.update.currentVersion) + ')';
        } else {
            update.innerHTML = '<span class="muted">Up to date.</span>';
        }
    }).catch(function(e) {
        document.getElementById('status-summary').innerHTML = '<span class="muted">' + esc(e.message) + '</span>';
    });
}

// ─── Core Options ────────────────────────────────────────────────

var coreModel = null;

function loadCore() {
    apiFetch('api/config/core').then(function(d) {
        coreModel = d;
        document.getElementById('core-server-role').value = d.serverRole || 'hub';
        document.getElementById('core-hub-worlds').value = (d.hubWorlds || []).join('\n');
        document.getElementById('core-auto-yield').checked = !!(d.compatibility && d.compatibility.autoYield);

        var modules = d.modules || {};
        var overrides = (d.compatibility && d.compatibility.overrides) || {};
        renderModulesTable(modules);
        renderOverridesTable(modules, overrides);
    }).catch(function(e) {
        showSaveStatus('core-save-status', false, e.message);
    });
}

function renderModulesTable(modules) {
    var body = document.getElementById('core-modules-body');
    body.innerHTML = '';
    Object.keys(modules).sort().forEach(function(key) {
        var value = modules[key]; // true | false | null
        var tr = document.createElement('tr');
        var select = document.createElement('select');
        select.dataset.moduleKey = key;
        ['default', 'on', 'off'].forEach(function(opt) {
            var o = document.createElement('option');
            o.value = opt;
            o.textContent = opt;
            select.appendChild(o);
        });
        select.value = value === true ? 'on' : (value === false ? 'off' : 'default');
        var keyTd = document.createElement('td');
        keyTd.textContent = key;
        var selectTd = document.createElement('td');
        selectTd.appendChild(select);
        tr.appendChild(keyTd);
        tr.appendChild(selectTd);
        body.appendChild(tr);
    });
}

function renderOverridesTable(modules, overrides) {
    var body = document.getElementById('core-overrides-body');
    body.innerHTML = '';
    Object.keys(modules).sort().forEach(function(key) {
        var tr = document.createElement('tr');
        var select = document.createElement('select');
        select.dataset.moduleKey = key;
        ['auto', 'enabled', 'disabled'].forEach(function(opt) {
            var o = document.createElement('option');
            o.value = opt;
            o.textContent = opt;
            select.appendChild(o);
        });
        select.value = overrides[key] || 'auto';
        var keyTd = document.createElement('td');
        keyTd.textContent = key;
        var selectTd = document.createElement('td');
        selectTd.appendChild(select);
        tr.appendChild(keyTd);
        tr.appendChild(selectTd);
        body.appendChild(tr);
    });
}

document.getElementById('core-save-btn').addEventListener('click', function() {
    var modules = {};
    document.querySelectorAll('#core-modules-body select').forEach(function(sel) {
        var v = sel.value;
        modules[sel.dataset.moduleKey] = v === 'on' ? true : (v === 'off' ? false : null);
    });
    var overrides = {};
    document.querySelectorAll('#core-overrides-body select').forEach(function(sel) {
        overrides[sel.dataset.moduleKey] = sel.value;
    });
    var body = {
        serverRole: document.getElementById('core-server-role').value,
        hubWorlds: splitLines(document.getElementById('core-hub-worlds').value),
        modules: modules,
        compatibility: {
            autoYield: document.getElementById('core-auto-yield').checked,
            overrides: overrides
        }
    };
    apiFetch('api/config/core', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        coreModel = d;
        renderModulesTable(d.modules || {});
        renderOverridesTable(d.modules || {}, (d.compatibility && d.compatibility.overrides) || {});
        showSaveStatus('core-save-status', true, 'Saved & reloaded.');
        loadStatus();
    }).catch(function(e) {
        showSaveStatus('core-save-status', false, e.message);
    });
});

// ─── Shared world-model helpers (scoreboard/tablist/announcements) ─

function populateWorldSelect(selectId, worldsMap, currentKey) {
    var select = document.getElementById(selectId);
    select.innerHTML = '';
    Object.keys(worldsMap).sort().forEach(function(key) {
        var opt = document.createElement('option');
        opt.value = key;
        opt.textContent = key;
        select.appendChild(opt);
    });
    select.value = currentKey;
}

function updateYieldBanner(elId, data) {
    var el = document.getElementById(elId);
    if (data.yielded) {
        el.textContent = 'Managed by ' + data.yieldedTo + ' — this module is currently yielded and its content '
            + 'will not be shown until the conflict is resolved (or compatibility.overrides.<module>: enabled is '
            + 'set). Changes here still save to disk immediately.';
        el.classList.remove('hidden');
    } else if (!data.enabled) {
        el.textContent = 'This module is currently disabled (see Core Options). Changes here still save to disk '
            + 'but won\'t take effect until it\'s enabled.';
        el.classList.remove('hidden');
    } else {
        el.classList.add('hidden');
    }
}

// ─── Scoreboard ──────────────────────────────────────────────────

var sbModel = null;
var sbCurrentWorld = 'default';

function emptyScoreboardWorld() {
    return {title: {frames: [''], frameIntervalTicks: 10}, lines: []};
}

function loadScoreboard() {
    apiFetch('api/config/scoreboard').then(function(d) {
        sbModel = d;
        if (!sbModel.worlds) sbModel.worlds = {};
        sbCurrentWorld = sbModel.worlds['default'] ? 'default' : (Object.keys(sbModel.worlds)[0] || 'default');
        if (!sbModel.worlds[sbCurrentWorld]) sbModel.worlds[sbCurrentWorld] = emptyScoreboardWorld();
        document.getElementById('sb-update-interval').value = sbModel.updateIntervalTicks;
        updateYieldBanner('sb-yield-banner', d);
        populateWorldSelect('sb-world-select', sbModel.worlds, sbCurrentWorld);
        renderScoreboardWorld();
    }).catch(function(e) {
        showSaveStatus('sb-save-status', false, e.message);
    });
}

function renderScoreboardWorld() {
    var w = sbModel.worlds[sbCurrentWorld] || emptyScoreboardWorld();
    var title = w.title || {frames: [''], frameIntervalTicks: 0};
    document.getElementById('sb-title-frames').value = (title.frames || ['']).join('\n');
    document.getElementById('sb-title-interval').value = title.frameIntervalTicks || 0;
    document.getElementById('sb-lines').value = (w.lines || []).join('\n');
    document.getElementById('sb-delete-world-btn').classList.toggle('hidden', sbCurrentWorld === 'default');
}

function syncScoreboardFormIntoModel() {
    sbModel.worlds[sbCurrentWorld] = {
        title: {
            frames: splitLines(document.getElementById('sb-title-frames').value),
            frameIntervalTicks: parseInt(document.getElementById('sb-title-interval').value, 10) || 0
        },
        lines: splitLines(document.getElementById('sb-lines').value)
    };
}

document.getElementById('sb-world-select').addEventListener('change', function() {
    syncScoreboardFormIntoModel();
    sbCurrentWorld = this.value;
    renderScoreboardWorld();
});

document.getElementById('sb-add-world-btn').addEventListener('click', function() {
    var name = prompt('World name (must match the Bukkit world folder name exactly):');
    if (!name) return;
    syncScoreboardFormIntoModel();
    sbModel.worlds[name] = emptyScoreboardWorld();
    sbCurrentWorld = name;
    populateWorldSelect('sb-world-select', sbModel.worlds, sbCurrentWorld);
    renderScoreboardWorld();
});

document.getElementById('sb-delete-world-btn').addEventListener('click', function() {
    if (sbCurrentWorld === 'default') return;
    if (!confirm('Remove the scoreboard override for "' + sbCurrentWorld + '"? It will fall back to default.')) return;
    delete sbModel.worlds[sbCurrentWorld];
    sbCurrentWorld = 'default';
    if (!sbModel.worlds['default']) sbModel.worlds['default'] = emptyScoreboardWorld();
    populateWorldSelect('sb-world-select', sbModel.worlds, sbCurrentWorld);
    renderScoreboardWorld();
});

document.getElementById('sb-save-btn').addEventListener('click', function() {
    syncScoreboardFormIntoModel();
    var body = {
        updateIntervalTicks: parseInt(document.getElementById('sb-update-interval').value, 10) || 20,
        worlds: sbModel.worlds
    };
    apiFetch('api/config/scoreboard', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        sbModel = d;
        if (!sbModel.worlds[sbCurrentWorld]) sbCurrentWorld = 'default';
        updateYieldBanner('sb-yield-banner', d);
        populateWorldSelect('sb-world-select', sbModel.worlds, sbCurrentWorld);
        renderScoreboardWorld();
        showSaveStatus('sb-save-status', true, 'Saved & reloaded.');
    }).catch(function(e) {
        showSaveStatus('sb-save-status', false, e.message);
    });
});

// ─── Tablist ─────────────────────────────────────────────────────

var tlModel = null;
var tlCurrentWorld = 'default';

function emptyTablistWorld() {
    return {header: {frames: [''], frameIntervalTicks: 15}, footer: {frames: [''], frameIntervalTicks: 20}};
}

function loadTablist() {
    apiFetch('api/config/tablist').then(function(d) {
        tlModel = d;
        if (!tlModel.worlds) tlModel.worlds = {};
        tlCurrentWorld = tlModel.worlds['default'] ? 'default' : (Object.keys(tlModel.worlds)[0] || 'default');
        if (!tlModel.worlds[tlCurrentWorld]) tlModel.worlds[tlCurrentWorld] = emptyTablistWorld();
        document.getElementById('tl-update-interval').value = tlModel.updateIntervalTicks;
        updateYieldBanner('tl-yield-banner', d);
        populateWorldSelect('tl-world-select', tlModel.worlds, tlCurrentWorld);
        renderTablistWorld();
    }).catch(function(e) {
        showSaveStatus('tl-save-status', false, e.message);
    });
}

function renderTablistWorld() {
    var w = tlModel.worlds[tlCurrentWorld] || emptyTablistWorld();
    var header = w.header || {frames: [''], frameIntervalTicks: 0};
    var footer = w.footer || {frames: [''], frameIntervalTicks: 0};
    document.getElementById('tl-header-frames').value = (header.frames || ['']).join('\n');
    document.getElementById('tl-header-interval').value = header.frameIntervalTicks || 0;
    document.getElementById('tl-footer-frames').value = (footer.frames || ['']).join('\n');
    document.getElementById('tl-footer-interval').value = footer.frameIntervalTicks || 0;
    document.getElementById('tl-delete-world-btn').classList.toggle('hidden', tlCurrentWorld === 'default');
}

function syncTablistFormIntoModel() {
    tlModel.worlds[tlCurrentWorld] = {
        header: {
            frames: splitLines(document.getElementById('tl-header-frames').value),
            frameIntervalTicks: parseInt(document.getElementById('tl-header-interval').value, 10) || 0
        },
        footer: {
            frames: splitLines(document.getElementById('tl-footer-frames').value),
            frameIntervalTicks: parseInt(document.getElementById('tl-footer-interval').value, 10) || 0
        }
    };
}

document.getElementById('tl-world-select').addEventListener('change', function() {
    syncTablistFormIntoModel();
    tlCurrentWorld = this.value;
    renderTablistWorld();
});

document.getElementById('tl-add-world-btn').addEventListener('click', function() {
    var name = prompt('World name (must match the Bukkit world folder name exactly):');
    if (!name) return;
    syncTablistFormIntoModel();
    tlModel.worlds[name] = emptyTablistWorld();
    tlCurrentWorld = name;
    populateWorldSelect('tl-world-select', tlModel.worlds, tlCurrentWorld);
    renderTablistWorld();
});

document.getElementById('tl-delete-world-btn').addEventListener('click', function() {
    if (tlCurrentWorld === 'default') return;
    if (!confirm('Remove the tablist override for "' + tlCurrentWorld + '"? It will fall back to default.')) return;
    delete tlModel.worlds[tlCurrentWorld];
    tlCurrentWorld = 'default';
    if (!tlModel.worlds['default']) tlModel.worlds['default'] = emptyTablistWorld();
    populateWorldSelect('tl-world-select', tlModel.worlds, tlCurrentWorld);
    renderTablistWorld();
});

document.getElementById('tl-save-btn').addEventListener('click', function() {
    syncTablistFormIntoModel();
    var body = {
        updateIntervalTicks: parseInt(document.getElementById('tl-update-interval').value, 10) || 20,
        worlds: tlModel.worlds
    };
    apiFetch('api/config/tablist', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        tlModel = d;
        if (!tlModel.worlds[tlCurrentWorld]) tlCurrentWorld = 'default';
        updateYieldBanner('tl-yield-banner', d);
        populateWorldSelect('tl-world-select', tlModel.worlds, tlCurrentWorld);
        renderTablistWorld();
        showSaveStatus('tl-save-status', true, 'Saved & reloaded.');
    }).catch(function(e) {
        showSaveStatus('tl-save-status', false, e.message);
    });
});

// ─── Announcements ───────────────────────────────────────────────

var anModel = null;
var anCurrentWorld = 'default';

function emptyAnnouncementWorld() {
    return {rotation: 'SEQUENTIAL', intervalTicksOverride: null, entries: []};
}

function loadAnnouncements() {
    apiFetch('api/config/announcements').then(function(d) {
        anModel = d;
        if (!anModel.worlds) anModel.worlds = {};
        anCurrentWorld = anModel.worlds['default'] ? 'default' : (Object.keys(anModel.worlds)[0] || 'default');
        if (!anModel.worlds[anCurrentWorld]) anModel.worlds[anCurrentWorld] = emptyAnnouncementWorld();
        document.getElementById('an-check-interval').value = anModel.checkIntervalTicks;
        document.getElementById('an-default-interval').value = anModel.defaultIntervalTicks;
        updateYieldBanner('an-yield-banner', d);
        populateWorldSelect('an-world-select', anModel.worlds, anCurrentWorld);
        renderAnnouncementWorld();
    }).catch(function(e) {
        showSaveStatus('an-save-status', false, e.message);
    });
}

function renderAnnouncementWorld() {
    var w = anModel.worlds[anCurrentWorld] || emptyAnnouncementWorld();
    document.getElementById('an-rotation').value = w.rotation || 'SEQUENTIAL';
    document.getElementById('an-interval-override').value =
        (w.intervalTicksOverride === null || w.intervalTicksOverride === undefined) ? '' : w.intervalTicksOverride;
    document.getElementById('an-delete-world-btn').classList.toggle('hidden', anCurrentWorld === 'default');
    renderEntries(w.entries || []);
}

function renderEntries(entries) {
    var list = document.getElementById('an-entries-list');
    list.innerHTML = '';
    if (!entries.length) {
        list.innerHTML = '<div class="muted">No entries yet — nothing will be announced for this world.</div>';
        return;
    }
    entries.forEach(function(entry, idx) {
        var block = document.createElement('div');
        block.className = 'entry-block';

        var header = document.createElement('div');
        header.className = 'entry-block-header';
        var label = document.createElement('span');
        label.textContent = 'Entry #' + (idx + 1);
        var removeBtn = document.createElement('button');
        removeBtn.className = 'btn btn-danger btn-sm';
        removeBtn.textContent = 'Remove';
        removeBtn.addEventListener('click', function() {
            syncAnnouncementFormIntoModel();
            anModel.worlds[anCurrentWorld].entries.splice(idx, 1);
            renderEntries(anModel.worlds[anCurrentWorld].entries);
        });
        header.appendChild(label);
        header.appendChild(removeBtn);
        block.appendChild(header);

        var textarea = document.createElement('textarea');
        textarea.rows = 3;
        textarea.className = 'an-entry-actions';
        textarea.placeholder = '[message] Hello %player%!';
        textarea.value = (entry.actions || []).join('\n');
        block.appendChild(textarea);

        list.appendChild(block);
    });
}

function syncAnnouncementFormIntoModel() {
    var entries = [];
    document.querySelectorAll('#an-entries-list .an-entry-actions').forEach(function(ta) {
        entries.push({actions: splitLines(ta.value)});
    });
    var overrideRaw = document.getElementById('an-interval-override').value.trim();
    var overrideValue = null;
    if (overrideRaw !== '') {
        var parsed = parseInt(overrideRaw, 10);
        overrideValue = isNaN(parsed) ? null : parsed;
    }
    anModel.worlds[anCurrentWorld] = {
        rotation: document.getElementById('an-rotation').value,
        intervalTicksOverride: overrideValue,
        entries: entries
    };
}

document.getElementById('an-add-entry-btn').addEventListener('click', function() {
    syncAnnouncementFormIntoModel();
    anModel.worlds[anCurrentWorld].entries.push({actions: []});
    renderEntries(anModel.worlds[anCurrentWorld].entries);
});

document.getElementById('an-world-select').addEventListener('change', function() {
    syncAnnouncementFormIntoModel();
    anCurrentWorld = this.value;
    renderAnnouncementWorld();
});

document.getElementById('an-add-world-btn').addEventListener('click', function() {
    var name = prompt('World name (must match the Bukkit world folder name exactly):');
    if (!name) return;
    syncAnnouncementFormIntoModel();
    anModel.worlds[name] = emptyAnnouncementWorld();
    anCurrentWorld = name;
    populateWorldSelect('an-world-select', anModel.worlds, anCurrentWorld);
    renderAnnouncementWorld();
});

document.getElementById('an-delete-world-btn').addEventListener('click', function() {
    if (anCurrentWorld === 'default') return;
    if (!confirm('Remove the announcements override for "' + anCurrentWorld + '"? It will fall back to default.')) return;
    delete anModel.worlds[anCurrentWorld];
    anCurrentWorld = 'default';
    if (!anModel.worlds['default']) anModel.worlds['default'] = emptyAnnouncementWorld();
    populateWorldSelect('an-world-select', anModel.worlds, anCurrentWorld);
    renderAnnouncementWorld();
});

document.getElementById('an-save-btn').addEventListener('click', function() {
    syncAnnouncementFormIntoModel();
    var body = {
        checkIntervalTicks: parseInt(document.getElementById('an-check-interval').value, 10) || 20,
        defaultIntervalTicks: parseInt(document.getElementById('an-default-interval').value, 10) || 600,
        worlds: anModel.worlds
    };
    apiFetch('api/config/announcements', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        anModel = d;
        if (!anModel.worlds[anCurrentWorld]) anCurrentWorld = 'default';
        updateYieldBanner('an-yield-banner', d);
        populateWorldSelect('an-world-select', anModel.worlds, anCurrentWorld);
        renderAnnouncementWorld();
        showSaveStatus('an-save-status', true, 'Saved & reloaded.');
    }).catch(function(e) {
        showSaveStatus('an-save-status', false, e.message);
    });
});

// ─── Utilities ───────────────────────────────────────────────────

function splitLines(text) {
    return (text || '').split('\n')
        .map(function(s) { return s.trim(); })
        .filter(function(s) { return s.length > 0; });
}

function showSaveStatus(id, ok, message) {
    var el = document.getElementById(id);
    el.textContent = message || (ok ? 'Saved.' : 'Save failed.');
    el.classList.toggle('error', !ok);
    el.classList.add('show');
    setTimeout(function() { el.classList.remove('show'); }, 3000);
}

function esc(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
