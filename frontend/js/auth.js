// Pantallas de acceso: registro, verificación de correo por enlace e inicio de sesión (JWT Bearer via /api/auth/*).
(function () {
    'use strict';

    function mostrarError(id, texto) {
        var caja = document.getElementById(id);
        if (!caja) { return; }
        caja.querySelector('div').textContent = texto;
        caja.classList.remove('d-none');
    }
    function ocultarError(id) {
        var caja = document.getElementById(id);
        if (caja) { caja.classList.add('d-none'); }
    }
    function val(id) { var el = document.getElementById(id); return el ? el.value.trim() : ''; }

    // ---- Pantalla /auth/acceso
    var formLogin = document.getElementById('formLogin');
    if (formLogin) {
        // Si ya tiene sesión válida, redirige a su inicio; si no, limpia restos
        if (TN.getToken()) {
            TN.me({ optional: true }).then(function (u) {
                if (u && u.redireccion) { location.href = u.redireccion; }
            });
        } else {
            TN.clearSession();
        }

        if (TN.qs('verificado') !== null) {
            var banner = document.getElementById('bannerVerificado');
            if (banner) { banner.classList.remove('d-none'); }
        }
        if (location.pathname === '/registro' || TN.qs('tab') === 'registro') {
            var tabReg = document.querySelector('[data-bs-target="#panelRegistro"]');
            if (tabReg && window.bootstrap) { new bootstrap.Tab(tabReg).show(); }
        }

        formLogin.addEventListener('submit', function (e) {
            e.preventDefault();
            ocultarError('loginError');
            var btn = formLogin.querySelector('button[type=submit]');
            btn.disabled = true;
            TN.json('/api/auth/login', {
                method: 'POST',
                noRedirect401: true,
                body: { correo: val('loginCorreo'), password: document.getElementById('loginPassword').value }
            }).then(function (data) {
                TN.setSession(data.token, {
                    id: data.id,
                    nombre: data.nombre,
                    correo: data.correo,
                    rol: data.rol,
                    redireccion: data.redireccion
                });
                location.href = data.redireccion || '/';
            }).catch(function (err) {
                btn.disabled = false;
                if (err.data && err.data.requiereVerificacion) {
                    location.href = '/auth/verificar?correo=' + encodeURIComponent(val('loginCorreo'));
                    return;
                }
                mostrarError('loginError', err.message || 'No se pudo iniciar sesión.');
            });
        });
    }

    var formRegistro = document.getElementById('formRegistro');
    if (formRegistro) {
        formRegistro.addEventListener('submit', function (e) {
            e.preventDefault();
            ocultarError('regError');
            var correo = val('regCorreo');
            var boton = formRegistro.querySelector('button[type=submit]');
            boton.disabled = true;
            TN.json('/api/auth/register', {
                method: 'POST',
                noRedirect401: true,
                body: { nombre: val('regNombre'), correo: correo, password: document.getElementById('regPassword').value }
            }).then(function () {
                location.href = '/auth/verificar?enviado=1&correo=' + encodeURIComponent(correo);
            }).catch(function (err) {
                boton.disabled = false;
                mostrarError('regError', err.message || 'No se pudo crear la cuenta.');
            });
        });
    }

    // ---- Pantalla /auth/verificar
    var caja = document.getElementById('verificacion');
    if (caja) {
        var token = TN.qs('token') || '';
        var correoParam = TN.qs('correo') || '';
        var enviado = TN.qs('enviado') === '1';
        var verCorreoInput = document.getElementById('verCorreo');
        if (verCorreoInput && correoParam) { verCorreoInput.value = correoParam; }
        var msgRevisa = document.getElementById('verRevisaTexto');
        if (msgRevisa && enviado) {
            msgRevisa.textContent = 'Si el correo es válido, te enviamos un enlace de confirmación. Vence en 24 horas; revisa también la carpeta de spam.';
        }

        function estado(nombre) {
            ['verCargando', 'verOk', 'verFallo', 'verRevisa'].forEach(function (id) {
                var el = document.getElementById(id); if (el) { el.classList.toggle('d-none', id !== nombre); }
            });
        }

        if (token) {
            estado('verCargando');
            TN.json('/api/auth/verify', {
                method: 'POST',
                noRedirect401: true,
                body: { token: token }
            }).then(function () {
                estado('verOk');
                var f = document.getElementById('formReenvio');
                if (f) { f.classList.add('d-none'); }
            }).catch(function (err) {
                document.getElementById('verFalloTexto').textContent = err.message || 'No se pudo verificar la cuenta.';
                estado('verFallo');
            });
        } else {
            estado('verRevisa');
        }

        var formReenvio = document.getElementById('formReenvio');
        var btnReenviar = document.getElementById('btnReenviar');
        var aviso = document.getElementById('reenvioAviso');
        var espera = 0, reloj = null;
        function contar(seg) {
            espera = seg; btnReenviar.disabled = true;
            clearInterval(reloj);
            reloj = setInterval(function () {
                espera--;
                if (espera <= 0) { clearInterval(reloj); btnReenviar.disabled = false; btnReenviar.textContent = 'Reenviar correo'; return; }
                btnReenviar.textContent = 'Reenviar en ' + espera + ' s';
            }, 1000);
            btnReenviar.textContent = 'Reenviar en ' + seg + ' s';
        }
        if (formReenvio) {
            if (enviado) { contar(60); }
            formReenvio.addEventListener('submit', function (e) {
                e.preventDefault();
                ocultarError('verError'); aviso.classList.add('d-none');
                TN.json('/api/auth/resend-verification', {
                    method: 'POST',
                    noRedirect401: true,
                    body: { correo: val('verCorreo') }
                }).then(function (data) {
                    aviso.textContent = data.mensaje; aviso.classList.remove('d-none'); contar(60);
                }).catch(function (err) {
                    if (err.status === 429) {
                        var m = /(\d+)/.exec(err.message || '');
                        contar(m ? parseInt(m[1], 10) : 60);
                    }
                    mostrarError('verError', err.message || 'No se pudo reenviar el correo.');
                });
            });
        }
    }
})();
