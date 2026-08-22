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
    'tab-announcements': loadAnnouncements,
    'tab-worldprotection': loadWorldProtection,
    'tab-joinspawn': loadJoinSpawn,
    'tab-chatcontrols': loadChatControls,
    'tab-network': loadNetwork,
    'tab-messages': loadMessages
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

// ─── World Protection ────────────────────────────────────────────

var wpModel = null;

var WP_BOOL_FIELDS = [
    'denyBlockBreak', 'denyBlockPlace', 'disableHunger', 'disableFallDamage',
    'disableAllDamage', 'disablePvp', 'lockWeather', 'clearWeather', 'lockTime',
    'denyMobSpawning', 'denyItemDrop', 'denyItemPickup', 'denyLeafDecay',
    'denyFireSpread', 'denyBlockBurn', 'denyTnt'
];

function wpFieldId(key) {
    // denyBlockBreak -> wp-deny-block-break (mirrors the Java-side camelToKebab helper).
    return 'wp-' + key.replace(/([A-Z])/g, function(m) { return '-' + m.toLowerCase(); });
}

function loadWorldProtection() {
    apiFetch('api/config/world-protection').then(function(d) {
        wpModel = d;
        WP_BOOL_FIELDS.forEach(function(key) {
            document.getElementById(wpFieldId(key)).checked = !!d[key];
        });
        document.getElementById('wp-fixed-time').value = (d.fixedTime === undefined || d.fixedTime === null) ? 6000 : d.fixedTime;
        updateYieldBanner('wp-yield-banner', d);
        renderZones(d.pvpZones || []);
    }).catch(function(e) {
        showSaveStatus('wp-save-status', false, e.message);
    });
}

function renderZones(zones) {
    var list = document.getElementById('wp-zones-list');
    list.innerHTML = '';
    if (!zones.length) {
        list.innerHTML = '<div class="muted">No PvP zones configured — disable-pvp (if on) applies everywhere in hub worlds.</div>';
        return;
    }
    zones.forEach(function(zone, idx) {
        var block = document.createElement('div');
        block.className = 'entry-block wp-zone-block';

        var header = document.createElement('div');
        header.className = 'entry-block-header';
        var label = document.createElement('span');
        label.textContent = 'Zone #' + (idx + 1);
        var removeBtn = document.createElement('button');
        removeBtn.className = 'btn btn-danger btn-sm';
        removeBtn.textContent = 'Remove';
        removeBtn.addEventListener('click', function() {
            syncZonesFromForm();
            wpModel.pvpZones.splice(idx, 1);
            renderZones(wpModel.pvpZones);
        });
        header.appendChild(label);
        header.appendChild(removeBtn);
        block.appendChild(header);

        var fields = document.createElement('div');
        fields.className = 'zone-fields';
        fields.appendChild(zoneTextField('zone-field-name', 'name', zone.name || ''));
        fields.appendChild(zoneTextField('zone-field-world', 'world', zone.world || ''));
        ['corner1', 'corner2'].forEach(function(corner) {
            ['x', 'y', 'z'].forEach(function(axis) {
                var c = zone[corner] || {};
                fields.appendChild(zoneNumberField(corner + '-' + axis, corner + '.' + axis, c[axis]));
            });
        });
        block.appendChild(fields);

        list.appendChild(block);
    });
}

function zoneTextField(cssClass, label, value) {
    var wrap = document.createElement('div');
    wrap.className = 'zone-field ' + cssClass;
    var lbl = document.createElement('label');
    lbl.textContent = label;
    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'wp-zone-' + cssClass;
    input.value = value;
    wrap.appendChild(lbl);
    wrap.appendChild(input);
    return wrap;
}

function zoneNumberField(dataKey, label, value) {
    var wrap = document.createElement('div');
    wrap.className = 'zone-field';
    var lbl = document.createElement('label');
    lbl.textContent = label;
    var input = document.createElement('input');
    input.type = 'number';
    input.className = 'wp-zone-coord';
    input.dataset.key = dataKey;
    input.value = (value === undefined || value === null) ? 0 : value;
    wrap.appendChild(lbl);
    wrap.appendChild(input);
    return wrap;
}

function syncZonesFromForm() {
    var blocks = document.querySelectorAll('#wp-zones-list .wp-zone-block');
    var zones = [];
    blocks.forEach(function(block) {
        var name = block.querySelector('.wp-zone-zone-field-name').value;
        var world = block.querySelector('.wp-zone-zone-field-world').value;
        var coords = {};
        block.querySelectorAll('.wp-zone-coord').forEach(function(inp) {
            coords[inp.dataset.key] = parseFloat(inp.value) || 0;
        });
        zones.push({
            name: name,
            world: world,
            corner1: {x: coords['corner1.x'], y: coords['corner1.y'], z: coords['corner1.z']},
            corner2: {x: coords['corner2.x'], y: coords['corner2.y'], z: coords['corner2.z']}
        });
    });
    wpModel.pvpZones = zones;
}

document.getElementById('wp-add-zone-btn').addEventListener('click', function() {
    syncZonesFromForm();
    wpModel.pvpZones.push({
        name: 'zone-' + (wpModel.pvpZones.length + 1),
        world: 'world',
        corner1: {x: 0, y: 0, z: 0},
        corner2: {x: 0, y: 0, z: 0}
    });
    renderZones(wpModel.pvpZones);
});

document.getElementById('wp-save-btn').addEventListener('click', function() {
    syncZonesFromForm();
    var body = {fixedTime: parseInt(document.getElementById('wp-fixed-time').value, 10) || 0, pvpZones: wpModel.pvpZones};
    WP_BOOL_FIELDS.forEach(function(key) {
        body[key] = document.getElementById(wpFieldId(key)).checked;
    });
    apiFetch('api/config/world-protection', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        wpModel = d;
        WP_BOOL_FIELDS.forEach(function(key) {
            document.getElementById(wpFieldId(key)).checked = !!d[key];
        });
        document.getElementById('wp-fixed-time').value = d.fixedTime;
        updateYieldBanner('wp-yield-banner', d);
        renderZones(d.pvpZones || []);
        showSaveStatus('wp-save-status', true, 'Saved & reloaded.');
        loadStatus();
    }).catch(function(e) {
        showSaveStatus('wp-save-status', false, e.message);
    });
});

// ─── Join & Spawn ────────────────────────────────────────────────

var joinSpawnModel = null;

function loadJoinSpawn() {
    apiFetch('api/config/join-spawn').then(function(d) {
        joinSpawnModel = d;
        renderJoinSpawn(d);
    }).catch(function(e) {
        showSaveStatus('js-save-status', false, e.message);
    });
}

function renderJoinSpawn(d) {
    var js = d.joinSettings || {};
    document.getElementById('js-clear-inventory').checked = !!js.clearInventory;
    document.getElementById('js-set-gamemode').checked = !!js.setGamemode;
    document.getElementById('js-gamemode').value = js.gamemode || 'ADVENTURE';
    document.getElementById('js-heal-and-feed').checked = !!js.healAndFeed;
    document.getElementById('js-join-firework').checked = !!js.joinFirework;
    document.getElementById('js-first-join-actions').value = (js.firstJoinActions || []).join('\n');
    updateYieldBanner('js-yield-banner', js);

    var sp = d.spawn || {};
    document.getElementById('sp-teleport-delay').value = (sp.lobbyTeleportDelayTicks === undefined) ? 60 : sp.lobbyTeleportDelayTicks;
    document.getElementById('sp-cancel-on-move').checked = !!sp.cancelOnMove;
    document.getElementById('sp-spawn-on-join').checked = !!sp.spawnOnJoin;
    document.getElementById('sp-spawn-on-void-fall').checked = !!sp.spawnOnVoidFall;
    document.getElementById('sp-spawn-on-respawn').checked = !!sp.spawnOnRespawn;
    updateYieldBanner('sp-yield-banner', sp);

    var dj = d.doubleJump || {};
    document.getElementById('dj-power').value = (dj.power === undefined) ? 1.4 : dj.power;
    document.getElementById('dj-height').value = (dj.height === undefined) ? 1.2 : dj.height;
    document.getElementById('dj-particle').value = dj.particle || 'CLOUD';
    document.getElementById('dj-sound').value = dj.sound || 'ENTITY_BAT_TAKEOFF';
    document.getElementById('dj-bedrock').checked = !!dj.bedrock;
    updateYieldBanner('dj-yield-banner', dj);
    renderRegions(dj.regions || []);
}

// Deliberately NOT reusing the World Protection tab's zoneTextField/zoneNumberField
// helpers here even though the two-corner-cuboid shape is identical — those helpers
// hardcode a "wp-zone-" class prefix, and every tab's markup lives in the DOM
// simultaneously (only hidden via CSS), so reusing them verbatim would stamp
// confusing "wp-zone-*" classes onto Double Jump's own inputs. Parallel structure,
// own "dj-region-" prefix instead — see the World Protection block above for the
// shape this mirrors.
function djRegionTextField(fieldName, label, value, extraWrapClass) {
    var wrap = document.createElement('div');
    wrap.className = 'zone-field' + (extraWrapClass ? ' ' + extraWrapClass : '');
    var lbl = document.createElement('label');
    lbl.textContent = label;
    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'dj-region-' + fieldName;
    input.value = value;
    wrap.appendChild(lbl);
    wrap.appendChild(input);
    return wrap;
}

function djRegionNumberField(dataKey, label, value) {
    var wrap = document.createElement('div');
    wrap.className = 'zone-field';
    var lbl = document.createElement('label');
    lbl.textContent = label;
    var input = document.createElement('input');
    input.type = 'number';
    input.className = 'dj-region-coord';
    input.dataset.key = dataKey;
    input.value = (value === undefined || value === null) ? 0 : value;
    wrap.appendChild(lbl);
    wrap.appendChild(input);
    return wrap;
}

function renderRegions(regions) {
    var list = document.getElementById('dj-regions-list');
    list.innerHTML = '';
    if (!regions.length) {
        list.innerHTML = '<div class="muted">No regions configured — the double-jump launch effect works everywhere in hub worlds.</div>';
        return;
    }
    regions.forEach(function(region, idx) {
        var block = document.createElement('div');
        block.className = 'entry-block dj-region-block';

        var header = document.createElement('div');
        header.className = 'entry-block-header';
        var label = document.createElement('span');
        label.textContent = 'Region #' + (idx + 1);
        var removeBtn = document.createElement('button');
        removeBtn.className = 'btn btn-danger btn-sm';
        removeBtn.textContent = 'Remove';
        removeBtn.addEventListener('click', function() {
            syncRegionsFromForm();
            joinSpawnModel.doubleJump.regions.splice(idx, 1);
            renderRegions(joinSpawnModel.doubleJump.regions);
        });
        header.appendChild(label);
        header.appendChild(removeBtn);
        block.appendChild(header);

        var fields = document.createElement('div');
        fields.className = 'zone-fields';
        fields.appendChild(djRegionTextField('name', 'name', region.name || '', 'zone-field-name'));
        fields.appendChild(djRegionTextField('world', 'world', region.world || '', 'zone-field-world'));
        ['corner1', 'corner2'].forEach(function(corner) {
            ['x', 'y', 'z'].forEach(function(axis) {
                var c = region[corner] || {};
                fields.appendChild(djRegionNumberField(corner + '-' + axis, corner + '.' + axis, c[axis]));
            });
        });
        block.appendChild(fields);

        list.appendChild(block);
    });
}

function syncRegionsFromForm() {
    var blocks = document.querySelectorAll('#dj-regions-list .dj-region-block');
    var regions = [];
    blocks.forEach(function(block) {
        var name = block.querySelector('.dj-region-name').value;
        var world = block.querySelector('.dj-region-world').value;
        var coords = {};
        block.querySelectorAll('.dj-region-coord').forEach(function(inp) {
            coords[inp.dataset.key] = parseFloat(inp.value) || 0;
        });
        regions.push({
            name: name,
            world: world,
            corner1: {x: coords['corner1.x'], y: coords['corner1.y'], z: coords['corner1.z']},
            corner2: {x: coords['corner2.x'], y: coords['corner2.y'], z: coords['corner2.z']}
        });
    });
    if (joinSpawnModel && joinSpawnModel.doubleJump) {
        joinSpawnModel.doubleJump.regions = regions;
    }
}

document.getElementById('dj-add-region-btn').addEventListener('click', function() {
    syncRegionsFromForm();
    joinSpawnModel.doubleJump.regions.push({
        name: 'region-' + (joinSpawnModel.doubleJump.regions.length + 1),
        world: 'world',
        corner1: {x: 0, y: 0, z: 0},
        corner2: {x: 0, y: 0, z: 0}
    });
    renderRegions(joinSpawnModel.doubleJump.regions);
});

document.getElementById('js-save-btn').addEventListener('click', function() {
    syncRegionsFromForm();
    var body = {
        joinSettings: {
            clearInventory: document.getElementById('js-clear-inventory').checked,
            setGamemode: document.getElementById('js-set-gamemode').checked,
            gamemode: document.getElementById('js-gamemode').value,
            healAndFeed: document.getElementById('js-heal-and-feed').checked,
            joinFirework: document.getElementById('js-join-firework').checked,
            firstJoinActions: splitLines(document.getElementById('js-first-join-actions').value)
        },
        spawn: {
            lobbyTeleportDelayTicks: parseInt(document.getElementById('sp-teleport-delay').value, 10) || 0,
            cancelOnMove: document.getElementById('sp-cancel-on-move').checked,
            spawnOnJoin: document.getElementById('sp-spawn-on-join').checked,
            spawnOnVoidFall: document.getElementById('sp-spawn-on-void-fall').checked,
            spawnOnRespawn: document.getElementById('sp-spawn-on-respawn').checked
        },
        doubleJump: {
            power: parseFloat(document.getElementById('dj-power').value) || 0,
            height: parseFloat(document.getElementById('dj-height').value) || 0,
            particle: document.getElementById('dj-particle').value.trim().toUpperCase(),
            sound: document.getElementById('dj-sound').value.trim().toUpperCase(),
            bedrock: document.getElementById('dj-bedrock').checked,
            regions: (joinSpawnModel.doubleJump && joinSpawnModel.doubleJump.regions) || []
        }
    };
    apiFetch('api/config/join-spawn', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        joinSpawnModel = d;
        renderJoinSpawn(d);
        showSaveStatus('js-save-status', true, 'Saved & reloaded.');
        loadStatus();
    }).catch(function(e) {
        showSaveStatus('js-save-status', false, e.message);
    });
});

// ─── Chat Controls ───────────────────────────────────────────────

var chatControlsModel = null;

function loadChatControls() {
    apiFetch('api/config/chat-controls').then(function(d) {
        chatControlsModel = d;
        renderChatControls(d);
    }).catch(function(e) {
        showSaveStatus('cc-save-status', false, e.message);
    });
}

function renderChatControls(d) {
    var lc = d.lockchat || {};
    document.getElementById('lc-cooldown').value = (lc.cooldownSeconds === undefined) ? 0 : lc.cooldownSeconds;
    document.getElementById('lc-blocker-mode').value = lc.commandBlockerMode || 'blacklist';
    document.getElementById('lc-blocker-commands').value = (lc.commandBlockerCommands || []).join('\n');

    var cc = d.clearchat || {};
    document.getElementById('cc-lines').value = (cc.lines === undefined) ? 100 : cc.lines;
    document.getElementById('cc-clear-for-everyone').checked = !!cc.clearForEveryone;
    updateYieldBanner('cc-yield-banner', cc);

    var ph = d.playerHider || {};
    document.getElementById('ph-cooldown').value = (ph.cooldownSeconds === undefined) ? 3 : ph.cooldownSeconds;

    var aw = d.antiWdl || {};
    document.getElementById('aw-action').value = aw.action || 'kick';
}

document.getElementById('cc-save-btn').addEventListener('click', function() {
    var body = {
        lockchat: {
            cooldownSeconds: parseInt(document.getElementById('lc-cooldown').value, 10) || 0,
            commandBlockerMode: document.getElementById('lc-blocker-mode').value,
            commandBlockerCommands: splitLines(document.getElementById('lc-blocker-commands').value)
        },
        clearchat: {
            lines: parseInt(document.getElementById('cc-lines').value, 10) || 100,
            clearForEveryone: document.getElementById('cc-clear-for-everyone').checked
        },
        playerHider: {
            cooldownSeconds: parseInt(document.getElementById('ph-cooldown').value, 10) || 0
        },
        antiWdl: {
            action: document.getElementById('aw-action').value
        }
    };
    apiFetch('api/config/chat-controls', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        chatControlsModel = d;
        renderChatControls(d);
        showSaveStatus('cc-save-status', true, 'Saved & reloaded.');
        loadStatus();
    }).catch(function(e) {
        showSaveStatus('cc-save-status', false, e.message);
    });
});

// ─── Network ─────────────────────────────────────────────────────

var networkModel = null;

function loadNetwork() {
    apiFetch('api/config/network').then(function(d) {
        networkModel = d;
        renderNetwork(d);
    }).catch(function(e) {
        showSaveStatus('nt-save-status', false, e.message);
    });
}

function renderNetwork(d) {
    var px = d.proxy || {};
    document.getElementById('nt-poll-interval').value = (px.pollIntervalSeconds === undefined) ? 10 : px.pollIntervalSeconds;
    document.getElementById('nt-connect-timeout').value = (px.connectTimeoutTicks === undefined) ? 40 : px.connectTimeoutTicks;
    document.getElementById('nt-servers').value = (px.servers || []).join('\n');

    var nw = d.network || {};
    document.getElementById('nt-shared-secret').value = nw.sharedSecret || '';
    renderKnownServers(nw.knownServers || {});
}

function renderKnownServers(knownServers) {
    var list = document.getElementById('nt-known-servers-list');
    list.innerHTML = '';
    var ids = Object.keys(knownServers);
    if (!ids.length) {
        list.innerHTML = '<div class="muted">No known servers configured — networkstats fetch() calls return empty until at least one is added.</div>';
        return;
    }
    ids.forEach(function(id) {
        list.appendChild(knownServerRow(id, knownServers[id]));
    });
}

function knownServerRow(id, url) {
    var block = document.createElement('div');
    block.className = 'entry-block nt-server-row';

    var header = document.createElement('div');
    header.className = 'entry-block-header';
    var label = document.createElement('span');
    label.textContent = 'Server';
    var removeBtn = document.createElement('button');
    removeBtn.className = 'btn btn-danger btn-sm';
    removeBtn.textContent = 'Remove';
    removeBtn.addEventListener('click', function() {
        block.remove();
    });
    header.appendChild(label);
    header.appendChild(removeBtn);
    block.appendChild(header);

    var fields = document.createElement('div');
    fields.className = 'kv-fields';
    fields.appendChild(knownServerField('kv-field-id', 'nt-server-id', 'id', id));
    fields.appendChild(knownServerField('kv-field-url', 'nt-server-url', 'url', url));
    block.appendChild(fields);

    return block;
}

function knownServerField(wrapClass, inputClass, label, value) {
    var wrap = document.createElement('div');
    wrap.className = 'kv-field ' + wrapClass;
    var lbl = document.createElement('label');
    lbl.textContent = label;
    var input = document.createElement('input');
    input.type = 'text';
    input.className = inputClass;
    input.value = value || '';
    wrap.appendChild(lbl);
    wrap.appendChild(input);
    return wrap;
}

document.getElementById('nt-add-known-server-btn').addEventListener('click', function() {
    var list = document.getElementById('nt-known-servers-list');
    if (list.querySelector('.muted')) {
        list.innerHTML = '';
    }
    list.appendChild(knownServerRow('', ''));
});

function syncKnownServersFromForm() {
    var servers = {};
    document.querySelectorAll('#nt-known-servers-list .nt-server-row').forEach(function(row) {
        var id = row.querySelector('.nt-server-id').value.trim();
        var url = row.querySelector('.nt-server-url').value.trim();
        if (id) {
            servers[id] = url;
        }
    });
    return servers;
}

document.getElementById('nt-save-btn').addEventListener('click', function() {
    var body = {
        proxy: {
            pollIntervalSeconds: parseInt(document.getElementById('nt-poll-interval').value, 10) || 10,
            connectTimeoutTicks: parseInt(document.getElementById('nt-connect-timeout').value, 10) || 0,
            servers: splitLines(document.getElementById('nt-servers').value)
        },
        network: {
            sharedSecret: document.getElementById('nt-shared-secret').value,
            knownServers: syncKnownServersFromForm()
        }
    };
    apiFetch('api/config/network', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        networkModel = d;
        renderNetwork(d);
        showSaveStatus('nt-save-status', true, 'Saved & reloaded.');
        loadStatus();
    }).catch(function(e) {
        showSaveStatus('nt-save-status', false, e.message);
    });
});

// ─── Messages ────────────────────────────────────────────────────

var msgModel = null;

// Purely cosmetic grouping for readability — every key returned by the API is
// rendered regardless of whether it's listed here (see the "Other" fallback group
// below), so a message key added by a later build step never silently disappears
// from this editor even before this list is updated to know about it.
var MESSAGE_GROUPS = [
    {title: 'General', keys: ['prefix', 'no-permission', 'reload-complete', 'unknown-subcommand', 'player-only-command', 'module-disabled']},
    {title: 'Spawn / Lobby', keys: ['setlobby-set', 'lobby-not-set', 'lobby-teleporting-now', 'lobby-teleporting-in', 'lobby-teleport-cancelled']},
    {title: 'Join Settings', keys: ['first-join-message']},
    {title: 'Proxy Service', keys: ['server-offline', 'proxy-module-disabled']},
    {title: 'Menus / Server Selector', keys: ['menu-not-found', 'menu-no-permission', 'menus-module-disabled', 'player-not-found']},
    {title: 'Scoreboard', keys: ['scoreboard-toggled-on', 'scoreboard-toggled-off', 'scoreboard-module-disabled']},
    {title: 'Fly / Gamemode / Vanish', keys: [
        'fly-enabled', 'fly-disabled', 'fly-already-enabled-elsewhere',
        'flyspeed-usage', 'flyspeed-changed', 'flyspeed-changed-other',
        'gamemode-changed', 'gamemode-changed-other',
        'vanish-enabled', 'vanish-disabled', 'vanish-enabled-other', 'vanish-disabled-other'
    ]},
    {title: 'Chat Controls', keys: ['lockchat-toggled-on', 'lockchat-toggled-off', 'chat-locked-blocked', 'chat-cooldown-blocked', 'command-blocked']},
    {title: 'Player Hider', keys: ['player-hider-cycled', 'player-hider-cooldown']},
    {title: 'Anti-WorldDownloader', keys: ['wdl-kicked', 'wdl-warned']},
    {title: 'Holograms', keys: [
        'holograms-module-disabled', 'hologram-created', 'hologram-already-exists', 'hologram-deleted',
        'hologram-not-found', 'hologram-line-added', 'hologram-line-updated', 'hologram-invalid-index',
        'hologram-cannot-remove-last-line', 'hologram-moved', 'hologram-list-header', 'hologram-list-empty'
    ]},
    {title: 'Proxy Portals', keys: [
        'portals-module-disabled', 'portal-wand-given', 'portal-wand-corner1-set', 'portal-wand-corner2-set',
        'portal-created', 'portal-already-exists', 'portal-no-selection', 'portal-different-worlds',
        'portal-deleted', 'portal-not-found', 'portal-list-header', 'portal-list-empty'
    ]}
];

function loadMessages() {
    apiFetch('api/config/messages').then(function(d) {
        msgModel = d;
        renderMessageFields(d);
    }).catch(function(e) {
        showSaveStatus('msg-save-status', false, e.message);
    });
}

function renderMessageFields(data) {
    var container = document.getElementById('msg-fields-container');
    container.innerHTML = '';
    var seen = {};

    MESSAGE_GROUPS.forEach(function(group) {
        var groupKeys = group.keys.filter(function(k) { return Object.prototype.hasOwnProperty.call(data, k); });
        if (!groupKeys.length) return;
        container.appendChild(buildMessageGroupBlock(group.title, groupKeys, data));
        groupKeys.forEach(function(k) { seen[k] = true; });
    });

    var otherKeys = Object.keys(data).filter(function(k) { return !seen[k]; }).sort();
    if (otherKeys.length) {
        container.appendChild(buildMessageGroupBlock('Other', otherKeys, data));
    }
}

function buildMessageGroupBlock(title, keys, data) {
    var block = document.createElement('div');
    block.className = 'editor-block';
    var h3 = document.createElement('h3');
    h3.textContent = title;
    block.appendChild(h3);
    keys.forEach(function(key) {
        var field = document.createElement('div');
        field.className = 'msg-field';
        var label = document.createElement('label');
        label.setAttribute('for', 'msg-' + key);
        label.textContent = key;
        var input = document.createElement('input');
        input.type = 'text';
        input.id = 'msg-' + key;
        input.className = 'msg-input';
        input.dataset.key = key;
        input.value = data[key] || '';
        field.appendChild(label);
        field.appendChild(input);
        block.appendChild(field);
    });
    return block;
}

document.getElementById('msg-save-btn').addEventListener('click', function() {
    var body = {};
    document.querySelectorAll('#msg-fields-container .msg-input').forEach(function(input) {
        body[input.dataset.key] = input.value;
    });
    apiFetch('api/config/messages', {method: 'POST', body: JSON.stringify(body)}).then(function(d) {
        msgModel = d;
        renderMessageFields(d);
        showSaveStatus('msg-save-status', true, 'Saved & reloaded.');
    }).catch(function(e) {
        showSaveStatus('msg-save-status', false, e.message);
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
