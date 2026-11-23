// Utilidades visuales de TerraNova: menú móvil, filtros de tabla/chips, animaciones de entrada y contadores.
(function () {
    'use strict';

    function bindSidebar() {
        var toggle = document.getElementById('sidebarToggle');
        var sidebar = document.getElementById('sidebar');
        if (!toggle || !sidebar || toggle.dataset.bound) { return; }
        toggle.dataset.bound = '1';
        toggle.addEventListener('click', function () { sidebar.classList.toggle('open'); });
        document.addEventListener('click', function (e) {
            if (window.innerWidth < 992 && sidebar.classList.contains('open')
                && !sidebar.contains(e.target) && !toggle.contains(e.target)) {
                sidebar.classList.remove('open');
            }
        });
    }

    function bindFiltros() {
        document.querySelectorAll('[data-filtro-tabla]').forEach(function (input) {
            if (input.dataset.bound) { return; }
            input.dataset.bound = '1';
            input.addEventListener('input', function () {
                var tabla = document.querySelector(input.getAttribute('data-filtro-tabla'));
                if (!tabla) { return; }
                var q = input.value.trim().toLowerCase();
                var visibles = 0;
                tabla.querySelectorAll('tbody tr[data-fila]').forEach(function (tr) {
                    var ok = tr.textContent.toLowerCase().indexOf(q) !== -1;
                    tr.style.display = ok ? '' : 'none';
                    if (ok) { visibles++; }
                });
                var vacio = document.querySelector(input.getAttribute('data-filtro-vacio') || '#sinCoincidencias');
                if (vacio) { vacio.style.display = visibles === 0 ? '' : 'none'; }
            });
        });

        document.querySelectorAll('[data-estado-filtro]').forEach(function (chip) {
            if (chip.dataset.bound) { return; }
            chip.dataset.bound = '1';
            chip.addEventListener('click', function () {
                var estado = chip.getAttribute('data-estado-filtro');
                chip.parentElement.querySelectorAll('[data-estado-filtro]').forEach(function (c) { c.classList.remove('active'); });
                chip.classList.add('active');
                document.querySelectorAll('[data-estado]').forEach(function (el) {
                    el.style.display = (estado === 'todos' || el.getAttribute('data-estado') === estado) ? '' : 'none';
                });
            });
        });
    }

    function bindReveal() {
        var reducir = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        document.querySelectorAll('.kpi, .card-tn, .atajo, .tipo-card').forEach(function (el, i) {
            if (el.closest('.reveal') || el.classList.contains('reveal')) { return; }
            el.classList.add('reveal');
            el.style.setProperty('--d', Math.min(i % 6, 5) * 0.06 + 's');
        });
        var items = document.querySelectorAll('.reveal:not(.visto)');
        if (reducir || !('IntersectionObserver' in window)) {
            items.forEach(function (el) { el.classList.add('visto'); });
        } else {
            var obs = new IntersectionObserver(function (entradas) {
                entradas.forEach(function (e) {
                    if (e.isIntersecting) { e.target.classList.add('visto'); obs.unobserve(e.target); }
                });
            }, { threshold: 0, rootMargin: '0px 0px -6% 0px' });
            items.forEach(function (el) {
                if (el.getBoundingClientRect().height > window.innerHeight * 0.8) { el.classList.add('visto'); return; }
                obs.observe(el);
            });
        }

        document.querySelectorAll('[data-contar]').forEach(function (el) {
            if (el.dataset.contado) { return; }
            el.dataset.contado = '1';
            var meta = parseInt(el.getAttribute('data-contar'), 10) || 0;
            if (reducir || meta === 0) { el.textContent = meta; return; }
            var ini = null;
            function paso(t) {
                if (ini === null) { ini = t; }
                var p = Math.min((t - ini) / 900, 1);
                el.textContent = Math.round(meta * (1 - Math.pow(1 - p, 3)));
                if (p < 1) { window.requestAnimationFrame(paso); }
            }
            window.requestAnimationFrame(paso);
        });
    }

    function bind() {
        bindSidebar();
        bindFiltros();
        bindReveal();
    }

    document.addEventListener('DOMContentLoaded', bind);
    window.TNUi = { bind: bind, bindReveal: bindReveal, bindFiltros: bindFiltros };
})();
