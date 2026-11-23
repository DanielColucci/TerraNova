// Módulo central de comunicación con el Backend REST (JWT Bearer + CORS + utilidades de formato).
(function () {
    'use strict';

    var TOKEN_KEY = 'terranova.token';
    var USER_KEY = 'terranova.user';
    var FLASH_KEY = 'terranova.flash';

    function baseUrl() {
        var cfg = window.TERRANOVA_CONFIG || {};
        var u = (cfg.API_BASE_URL || 'http://localhost:8080').trim();
        return u.replace(/\/+$/, '');
    }

    function apiUrl(path) {
        if (!path) { return baseUrl(); }
        if (/^https?:\/\//i.test(path)) { return path; }
        return baseUrl() + (path.charAt(0) === '/' ? path : '/' + path);
    }

    function getToken() {
        try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
    }

    function getUser() {
        try {
            var raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    }

    function setSession(token, user) {
        try {
            if (token) { localStorage.setItem(TOKEN_KEY, token); }
            if (user) { localStorage.setItem(USER_KEY, JSON.stringify(user)); }
        } catch (e) { /* sin storage */ }
    }

    function clearSession() {
        try {
            Object.keys(localStorage)
                .filter(function (k) { return k.indexOf('terranova') === 0; })
                .forEach(function (k) { localStorage.removeItem(k); });
            sessionStorage.removeItem('terranova.cwAbierto');
        } catch (e) { /* sin storage */ }
    }

    function flash(tipo, mensaje) {
        try { sessionStorage.setItem(FLASH_KEY, JSON.stringify({ tipo: tipo, mensaje: mensaje })); } catch (e) { }
    }

    function consumeFlash() {
        try {
            var raw = sessionStorage.getItem(FLASH_KEY);
            if (!raw) { return null; }
            sessionStorage.removeItem(FLASH_KEY);
            return JSON.parse(raw);
        } catch (e) { return null; }
    }

    function headers(extra) {
        var h = {
            'ngrok-skip-browser-warning': 'true',
            'Accept': 'application/json'
        };
        var tk = getToken();
        if (tk) { h['Authorization'] = 'Bearer ' + tk; }
        if (extra) {
            Object.keys(extra).forEach(function (k) { h[k] = extra[k]; });
        }
        return h;
    }

    function rawFetch(path, opts) {
        opts = opts || {};
        var init = {
            method: opts.method || 'GET',
            headers: headers(opts.headers),
            signal: opts.signal
        };
        if (opts.body !== undefined) {
            if (typeof opts.body === 'string' || opts.body instanceof FormData || opts.body instanceof URLSearchParams) {
                init.body = opts.body;
            } else {
                init.headers['Content-Type'] = 'application/json';
                init.body = JSON.stringify(opts.body);
            }
        }
        return fetch(apiUrl(path), init).then(function (resp) {
            if (resp.status === 401 && !opts.noRedirect401) {
                clearSession();
                if (location.pathname.indexOf('/auth/') !== 0) {
                    location.href = '/auth/acceso';
                }
            }
            return resp;
        });
    }

    function json(path, opts) {
        return rawFetch(path, opts).then(function (resp) {
            var ct = resp.headers.get('content-type') || '';
            var parser = ct.indexOf('json') !== -1
                ? resp.json().catch(function () { return {}; })
                : resp.text().then(function (t) { return t ? { error: t } : {}; });
            return parser.then(function (data) {
                if (!resp.ok) {
                    var err = new Error((data && (data.error || data.mensaje)) || ('Error ' + resp.status));
                    err.status = resp.status;
                    err.data = data;
                    if (resp.status === 403 && opts && opts.redirect403) {
                        var u = getUser();
                        flash('aviso', err.message || 'Tu rol no tiene permiso para esa sección.');
                        location.href = (u && u.redireccion) || '/auth/acceso';
                    }
                    throw err;
                }
                return data;
            });
        });
    }

    function downloadBlob(path, defaultFilename) {
        return rawFetch(path, { headers: { 'Accept': '*/*' } }).then(function (resp) {
            if (!resp.ok) {
                return resp.text().then(function (t) {
                    throw new Error(t || ('No se pudo descargar el archivo (HTTP ' + resp.status + ').'));
                });
            }
            var cd = resp.headers.get('content-disposition') || '';
            var match = /filename="?([^"]+)"?/i.exec(cd);
            var nombre = (match && match[1]) ? match[1] : (defaultFilename || 'descarga.csv');
            return resp.blob().then(function (blob) {
                var url = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = nombre;
                document.body.appendChild(a);
                a.click();
                a.remove();
                setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
            });
        });
    }

    function me(opts) {
        if (!getToken()) {
            if (!(opts && opts.optional)) {
                location.href = '/auth/acceso';
            }
            return Promise.resolve(null);
        }
        return json('/api/auth/me', { noRedirect401: Boolean(opts && opts.optional) }).then(function (u) {
            setSession(null, u);
            return u;
        }).catch(function (err) {
            if (opts && opts.optional) {
                clearSession();
                return null;
            }
            throw err;
        });
    }

    function requireRole(allowedRoles) {
        return me().then(function (u) {
            if (!u) { throw new Error('Sesión requerida'); }
            if (allowedRoles && allowedRoles.length && allowedRoles.indexOf(u.rol) === -1) {
                flash('aviso', 'Tu cuenta no tiene permiso para entrar a esa sección.');
                location.href = u.redireccion || '/auth/acceso';
                throw new Error('Acceso denegado');
            }
            return u;
        });
    }

    function logout() {
        var tk = getToken();
        clearSession();
        if (tk) {
            fetch(apiUrl('/api/auth/logout'), {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + tk, 'ngrok-skip-browser-warning': 'true' }
            }).catch(function () { });
        }
        location.href = '/auth/acceso';
    }

    // ------------------------------------------------------------------ utilidades de formato y DOM
    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function money(v) {
        var n = Number(v || 0);
        return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function money0(v) {
        var n = Number(v || 0);
        return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
    }

    function fecha(iso) {
        if (!iso) { return '—'; }
        var p = String(iso).slice(0, 10).split('-');
        return p.length === 3 ? p[2] + '/' + p[1] + '/' + p[0] : String(iso);
    }

    function fechaHora(iso) {
        if (!iso) { return '—'; }
        var s = String(iso);
        var f = fecha(s.slice(0, 10));
        var h = s.length >= 16 ? s.slice(11, 16) : '';
        return h ? f + ' ' + h : f;
    }

    function estadoBadge(estado) {
        var raw = String(estado || '');
        var css = raw.toLowerCase().replace(/_/g, '');
        if (css === 'pendientepago') { css = 'pendiente'; }
        var label = raw.replace(/_/g, ' ');
        return '<span class="estado-badge estado-' + esc(css) + '"><span class="dot"></span>' + esc(label) + '</span>';
    }

    function activoBadge(activo) {
        return '<span class="activo-badge ' + (activo ? 'si' : 'no') + '">'
            + '<i class="bi ' + (activo ? 'bi-check-circle-fill' : 'bi-pause-circle-fill') + '"></i>'
            + (activo ? 'Activo' : 'Inactivo') + '</span>';
    }

    function qs(name) {
        return new URLSearchParams(location.search).get(name);
    }

    window.TN = {
        baseUrl: baseUrl,
        apiUrl: apiUrl,
        getToken: getToken,
        getUser: getUser,
        setSession: setSession,
        clearSession: clearSession,
        flash: flash,
        consumeFlash: consumeFlash,
        fetch: rawFetch,
        json: json,
        get: function (p, o) { return json(p, Object.assign({ method: 'GET' }, o)); },
        post: function (p, body, o) { return json(p, Object.assign({ method: 'POST', body: body }, o)); },
        put: function (p, body, o) { return json(p, Object.assign({ method: 'PUT', body: body }, o)); },
        patch: function (p, body, o) { return json(p, Object.assign({ method: 'PATCH', body: body }, o)); },
        del: function (p, o) { return json(p, Object.assign({ method: 'DELETE' }, o)); },
        downloadBlob: downloadBlob,
        me: me,
        requireRole: requireRole,
        logout: logout,
        esc: esc,
        money: money,
        money0: money0,
        fecha: fecha,
        fechaHora: fechaHora,
        estadoBadge: estadoBadge,
        activoBadge: activoBadge,
        qs: qs
    };
})();
