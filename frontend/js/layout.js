// Layout compartido del frontend: reemplaza a fragments/layout.html.
// Inyecta barra superior móvil, barra lateral según rol, alertas flash, modal de eliminación y chat flotante.
(function () {
    'use strict';

    function navLink(href, icon, label, active, extra) {
        return '<a href="' + href + '" class="nav-link-tn' + (active ? ' active' : '') + '"' + (extra || '') + '>'
            + '<i class="bi ' + icon + '"></i><span>' + label + '</span></a>';
    }

    function menuPorRol(rol, activo, tab) {
        if (rol === 'ADMINISTRADOR') {
            return ''
                + '<div class="nav-destacados">'
                + '<a href="/reservaciones/lista" class="nav-link-tn nav-destacado' + (activo === 'reservaciones' ? ' active' : '') + '"><i class="bi bi-calendar-check"></i><span>Reservaciones</span></a>'
                + '<a href="/asistente" class="nav-link-tn nav-destacado' + (activo === 'asistente' ? ' active' : '') + '"><i class="bi bi-stars"></i><span>Asistente de IA</span></a>'
                + '</div>'
                + navLink('/admin/dashboard', 'bi-speedometer2', 'Inicio', activo === 'dashboard')
                + '<div class="nav-section">Administración</div>'
                + navLink('/hoteles/lista', 'bi-building', 'Hoteles', activo === 'hoteles')
                + navLink('/tipos/lista', 'bi-tags', 'Tipos de habitación', activo === 'tipos')
                + navLink('/habitaciones/lista', 'bi-door-open', 'Habitaciones', activo === 'habitaciones')
                + navLink('/usuarios/lista', 'bi-people', 'Usuarios', activo === 'usuarios')
                + '<div class="nav-section">Historias de usuario</div>'
                + navLink('/busqueda', 'bi-funnel', 'Búsqueda avanzada', activo === 'h-busqueda')
                + navLink('/recepcion/panel', 'bi-person-workspace', 'Panel de recepción', activo === 'h-panel')
                + navLink('/recepcion/checkin', 'bi-key', 'Check-in', activo === 'h-checkin')
                + navLink('/recepcion/checkout', 'bi-box-arrow-right', 'Check-out', activo === 'h-checkout')
                + navLink('/recepcion/estados', 'bi-toggles', 'Estados de habitación', activo === 'h-estados')
                + navLink('/gerente/dashboard', 'bi-graph-up-arrow', 'Panel gerencial', activo === 'h-ejecutivo')
                + navLink('/gerente/informes', 'bi-file-earmark-bar-graph', 'Informes gerenciales', activo === 'g-informes');
        }
        if (rol === 'GERENTE') {
            var t = tab || 'resumen';
            return ''
                + '<div class="nav-section">Análisis</div>'
                + navLink('/gerente/dashboard', 'bi-graph-up-arrow', 'Panel gerencial', activo === 'h-ejecutivo' && t === 'resumen', ' data-gr-tab="resumen"')
                + navLink('/gerente/informes', 'bi-file-earmark-bar-graph', 'Informes y reportes', activo === 'g-informes')
                + navLink('/asistente', 'bi-stars', 'Asistente de IA', activo === 'asistente')
                + '<div class="nav-section">Supervisión</div>'
                + navLink('/gerente/dashboard?tab=reservaciones', 'bi-calendar-check', 'Reservaciones por estado', activo === 'h-ejecutivo' && t === 'reservaciones', ' data-gr-tab="reservaciones"')
                + navLink('/gerente/dashboard?tab=ocupacion', 'bi-building', 'Ocupación por hotel', activo === 'h-ejecutivo' && t === 'ocupacion', ' data-gr-tab="ocupacion"')
                + navLink('/gerente/dashboard?tab=rentabilidad', 'bi-trophy', 'Rentabilidad por tipo', activo === 'h-ejecutivo' && t === 'rentabilidad', ' data-gr-tab="rentabilidad"')
                + navLink('/gerente/dashboard?tab=tendencias', 'bi-graph-up', 'Tendencias', activo === 'h-ejecutivo' && t === 'tendencias', ' data-gr-tab="tendencias"')
                + navLink('/gerente/dashboard?tab=huespedes', 'bi-people', 'Huéspedes y pagos', activo === 'h-ejecutivo' && t === 'huespedes', ' data-gr-tab="huespedes"');
        }
        if (rol === 'RECEPCIONISTA') {
            return ''
                + navLink('/recepcion/nueva-reservacion', 'bi-calendar-plus', 'Nueva reservación', activo === 'reservaciones')
                + navLink('/asistente', 'bi-stars', 'Asistente de IA', activo === 'asistente')
                + navLink('/recepcion/panel', 'bi-house-heart', 'Panel de recepción', activo === 'h-panel')
                + navLink('/recepcion/checkin', 'bi-key', 'Check-in', activo === 'h-checkin')
                + navLink('/recepcion/checkout', 'bi-box-arrow-right', 'Check-out', activo === 'h-checkout')
                + navLink('/recepcion/estados', 'bi-toggles', 'Estados de habitación', activo === 'h-estados');
        }
        return ''
            + navLink('/inicio', 'bi-house-heart', 'Inicio', activo === 'inicio')
            + navLink('/mis-reservaciones', 'bi-calendar-check', 'Mis reservaciones', activo === 'mis-reservaciones')
            + navLink('/asistente', 'bi-stars', 'Asistente de IA', activo === 'asistente');
    }

    function etiquetaRol(rol) {
        if (rol === 'ADMINISTRADOR') { return 'Administrador'; }
        if (rol === 'GERENTE') { return 'Gerente'; }
        if (rol === 'RECEPCIONISTA') { return 'Recepción'; }
        return 'Huésped';
    }

    function construirWidget(user) {
        if (!user || document.getElementById('asistente') || document.getElementById('chatWidget')) { return; }
        var esGerente = user.rol === 'GERENTE';
        var esRecep = user.rol === 'RECEPCIONISTA';
        var saludo = esGerente
            ? 'Hola, soy tu asistente gerencial (solo consulta). Te muestro datos crudos de ingresos, ocupación, rentabilidad y tendencias.'
            : esRecep
            ? 'Hola, soy tu asistente de recepción. Te ayudo con disponibilidad, llegadas y reservaciones en mostrador.'
            : 'Hola, soy tu asistente. Puedo reservar, mostrar precios y tus reservaciones.';
        var sugs = esGerente
            ? ['¿Cómo van los ingresos de este mes?', '¿Cuál es la ocupación actual por hotel?', '¿Qué tipo de habitación es más rentable?']
            : esRecep
            ? ['¿Qué habitaciones están disponibles hoy?', 'Quiero crear una reservación en mostrador', '¿Quién está hospedado actualmente?']
            : ['Quiero reservar una habitación', '¿Cuánto cuestan las habitaciones?', 'Muéstrame mis reservaciones'];

        var wrap = document.createElement('div');
        wrap.id = 'chatWidget';
        wrap.className = 'cw';
        wrap.setAttribute('data-api', '/api/hotel-ai');
        wrap.setAttribute('data-inicio', '/inicio');
        wrap.innerHTML = ''
            + '<button type="button" class="cw-bolita" id="cwBolita" aria-label="Abrir chat con el asistente" title="Asistente de TerraNova">'
            + '  <i class="bi bi-chat-dots-fill cw-ico-chat"></i><i class="bi bi-x-lg cw-ico-cerrar"></i>'
            + '  <span class="cw-punto" id="cwPunto" hidden></span>'
            + '</button>'
            + '<section class="cw-panel" id="cwPanel" aria-label="Chat con el asistente" aria-hidden="true">'
            + '  <header class="cw-cab">'
            + '    <div class="cw-cab-titulo"><span class="cw-avatar"><i class="bi bi-stars"></i></span>'
            + '      <div><strong>Asistente TerraNova</strong><small id="cwEstado">En línea</small></div></div>'
            + '    <div class="cw-cab-acc">'
            + '      <button type="button" id="cwNuevo" title="Nuevo chat" aria-label="Nuevo chat"><i class="bi bi-plus-lg"></i></button>'
            + '      <a href="/asistente" title="Abrir en pantalla completa" aria-label="Pantalla completa"><i class="bi bi-arrows-angle-expand"></i></a>'
            + '      <button type="button" id="cwMin" title="Minimizar" aria-label="Minimizar"><i class="bi bi-dash-lg"></i></button>'
            + '    </div>'
            + '  </header>'
            + '  <div class="cw-msgs" id="cwMsgs" aria-live="polite">'
            + '    <div class="cw-vacio" id="cwVacio">'
            + '      <i class="bi bi-stars"></i>'
            + '      <p>' + TN.esc(saludo) + '</p>'
            + '      <div class="cw-sugs">'
            + sugs.map(function (s) { return '<button type="button" class="chip">' + TN.esc(s) + '</button>'; }).join('')
            + '      </div>'
            + '    </div>'
            + '  </div>'
            + '  <form class="cw-form" id="cwForm" novalidate>'
            + '    <textarea id="cwTexto" rows="1" maxlength="1000" placeholder="Escribe tu mensaje…" aria-label="Tu mensaje"></textarea>'
            + '    <button type="submit" id="cwEnviar" aria-label="Enviar"><i class="bi bi-send"></i></button>'
            + '  </form>'
            + '</section>';
        document.body.appendChild(wrap);
        if (window.TNChatWidget && window.TNChatWidget.mount) {
            window.TNChatWidget.mount();
        }
    }

    function showFlash(tipo, mensaje) {
        var host = document.getElementById('flashHost');
        if (!host || !mensaje) { return; }
        var mapa = {
            exito: { cls: 'flash-exito', ico: 'bi-check-circle-fill' },
            aviso: { cls: 'flash-aviso', ico: 'bi-exclamation-triangle-fill' },
            error: { cls: 'flash-error', ico: 'bi-x-octagon-fill' }
        };
        var cfg = mapa[tipo] || mapa.aviso;
        var div = document.createElement('div');
        div.className = 'alert flash ' + cfg.cls + ' alert-dismissible fade show';
        div.setAttribute('role', 'alert');
        div.innerHTML = '<i class="bi ' + cfg.ico + '"></i><div>' + TN.esc(mensaje) + '</div>'
            + '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Cerrar"></button>';
        host.innerHTML = '';
        host.appendChild(div);
        if (tipo === 'exito') {
            setTimeout(function () {
                if (window.bootstrap && bootstrap.Alert) {
                    try { bootstrap.Alert.getOrCreateInstance(div).close(); } catch (e) { div.remove(); }
                }
            }, 7000);
        }
    }

    function asegurarModalEliminar() {
        if (document.getElementById('modalEliminar')) { return; }
        var m = document.createElement('div');
        m.className = 'modal fade';
        m.id = 'modalEliminar';
        m.tabIndex = -1;
        m.setAttribute('aria-hidden', 'true');
        m.innerHTML = ''
            + '<div class="modal-dialog modal-dialog-centered">'
            + '  <div class="modal-content border-0 shadow-lg">'
            + '    <div class="modal-body text-center p-4">'
            + '      <div class="modal-icon"><i class="bi bi-trash3"></i></div>'
            + '      <h5 class="fw-bold mb-2">¿Confirmas la eliminación?</h5>'
            + '      <p class="mb-1 text-secondary">Estás por eliminar <span id="modalEliminarEntidad">el registro</span> '
            + '        <strong id="modalEliminarNombre" class="text-dark"></strong>.</p>'
            + '      <p class="small text-muted mb-4">Esta acción no se puede deshacer. <span id="modalEliminarNota"></span></p>'
            + '      <div class="d-flex gap-2 justify-content-center">'
            + '        <button type="button" class="btn btn-soft px-4" data-bs-dismiss="modal">Cancelar</button>'
            + '        <button type="button" id="btnConfirmarEliminar" class="btn btn-danger px-4 fw-semibold" style="border-radius:12px">'
            + '          <i class="bi bi-trash3 me-1"></i> Sí, eliminar</button>'
            + '      </div>'
            + '    </div>'
            + '  </div>'
            + '</div>';
        document.body.appendChild(m);
    }

    function confirmarEliminar(opts) {
        asegurarModalEliminar();
        document.getElementById('modalEliminarEntidad').textContent = opts.entidad || 'el registro';
        document.getElementById('modalEliminarNombre').textContent = opts.nombre || '';
        document.getElementById('modalEliminarNota').textContent = opts.nota || '';
        var modalEl = document.getElementById('modalEliminar');
        var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        var btn = document.getElementById('btnConfirmarEliminar');
        var nuevo = btn.cloneNode(true);
        btn.parentNode.replaceChild(nuevo, btn);
        nuevo.addEventListener('click', function () {
            nuevo.disabled = true;
            Promise.resolve(opts.onConfirm()).finally(function () {
                nuevo.disabled = false;
                modal.hide();
            });
        });
        modal.show();
    }

    function init(opts) {
        opts = opts || {};
        return TN.requireRole(opts.roles).then(function (user) {
            if (!opts.sinSidebar) {
                var topbar = document.getElementById('topbarHost');
                if (topbar) {
                    topbar.outerHTML = ''
                        + '<div class="topbar-mobile">'
                        + '  <strong class="letter-spacing-1"><i class="bi bi-buildings me-2"></i>TerraNova</strong>'
                        + '  <span>'
                        + '    <button type="button" class="btn btn-sm" data-logout aria-label="Cerrar sesión" title="Cerrar sesión"><i class="bi bi-box-arrow-left fs-5"></i></button>'
                        + '    <button type="button" class="btn btn-sm" id="sidebarToggle" aria-label="Abrir menú"><i class="bi bi-list fs-5"></i></button>'
                        + '  </span>'
                        + '</div>';
                }
                var sb = document.getElementById('sidebarHost');
                if (sb) {
                    sb.outerHTML = ''
                        + '<aside class="sidebar" id="sidebar">'
                        + '  <a href="' + TN.esc(user.redireccion || '/') + '" class="brand">'
                        + '    <div class="brand-logo">TN</div>'
                        + '    <div class="brand-text"><strong>TerraNova</strong><span>Sanctuary &amp; Resort</span></div>'
                        + '  </a>'
                        + '  <nav>' + menuPorRol(user.rol, opts.activo || '', opts.tab) + '</nav>'
                        + '  <div class="sidebar-footer">'
                        + '    <div class="mb-2"><strong>' + TN.esc(user.nombre) + '</strong><span>' + etiquetaRol(user.rol) + '</span></div>'
                        + '    <button type="button" class="btn btn-soft btn-sm w-100" data-logout><i class="bi bi-box-arrow-left me-1"></i>Cerrar sesión</button>'
                        + '  </div>'
                        + '</aside>';
                }
            }

            var f = TN.consumeFlash();
            if (f) { showFlash(f.tipo, f.mensaje); }

            asegurarModalEliminar();
            construirWidget(user);

            if (window.TNUi && window.TNUi.bind) {
                window.TNUi.bind();
            }
            return user;
        });
    }

    document.addEventListener('click', function (e) {
        var el = e.target.closest ? e.target.closest('[data-logout]') : null;
        if (!el) { return; }
        e.preventDefault();
        TN.logout();
    });

    window.TNLayout = {
        init: init,
        showFlash: showFlash,
        confirmarEliminar: confirmarEliminar
    };
})();
