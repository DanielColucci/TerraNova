// Pago simulado: formato y validación (Luhn) en el navegador; el backend vuelve a validar todo via /api/pago/{folio}/procesar.
(function () {
    'use strict';

    function init(folio, restanteSeg) {
        var form = document.getElementById('formPago');
        if (!form) { return; }
        var numero = document.getElementById('pgNumero'), vence = document.getElementById('pgVence'), cvv = document.getElementById('pgCvv');
        var titular = document.getElementById('pgTitular'), boton = document.getElementById('pgBoton');
        var marca = document.getElementById('pgMarca'), error = document.getElementById('pgError');
        var reducir = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        function luhn(d) {
            var s = 0, doble = false;
            for (var i = d.length - 1; i >= 0; i--) {
                var n = d.charCodeAt(i) - 48;
                if (doble) { n *= 2; if (n > 9) { n -= 9; } }
                s += n; doble = !doble;
            }
            return s % 10 === 0;
        }
        function mostrar(msg) { error.querySelector('div').textContent = msg; error.classList.remove('d-none'); }
        function ocultar() { error.classList.add('d-none'); }
        function solo(v) { return v.replace(/\D/g, ''); }

        numero.addEventListener('input', function () {
            var d = solo(numero.value).slice(0, 19);
            numero.value = d.replace(/(.{4})/g, '$1 ').trim();
            marca.innerHTML = /^4/.test(d) ? '<i class="bi bi-credit-card-2-front-fill"></i> Visa'
                : /^(5[1-5]|2[2-7])/.test(d) ? '<i class="bi bi-credit-card-2-front-fill"></i> MC'
                : '<i class="bi bi-credit-card-2-front"></i>';
        });
        vence.addEventListener('input', function () {
            var d = solo(vence.value).slice(0, 4);
            vence.value = d.length > 2 ? d.slice(0, 2) + '/' + d.slice(2) : d;
        });
        cvv.addEventListener('input', function () { cvv.value = solo(cvv.value).slice(0, 4); });

        document.querySelectorAll('[data-usar-tarjeta]').forEach(function (b) {
            b.addEventListener('click', function () {
                numero.value = b.getAttribute('data-usar-tarjeta'); numero.dispatchEvent(new Event('input'));
                if (!vence.value) { vence.value = '12/' + String((new Date().getFullYear() + 3) % 100).padStart(2, '0'); }
                if (!cvv.value) { cvv.value = '123'; }
                if (!titular.value) { titular.value = 'Huésped de Prueba'; }
            });
        });

        var reloj = document.getElementById('pagoReloj'), tiempo = document.getElementById('pagoTiempo');
        var fin = Date.now() + (parseInt(restanteSeg, 10) || 0) * 1000, temporizador = null;
        function tic() {
            var s = Math.max(0, Math.round((fin - Date.now()) / 1000));
            tiempo.textContent = String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
            reloj.classList.toggle('urgente', s <= 120);
            if (s <= 0) { clearInterval(temporizador); location.reload(); }
        }
        if (reloj) { tic(); temporizador = setInterval(tic, 1000); }

        function panel(id) {
            ['pagoTarjeta', 'pagoProcesando', 'pagoOk', 'pagoNo'].forEach(function (x) {
                var el = document.getElementById(x); if (el) { el.classList.toggle('d-none', x !== id); }
            });
            window.scrollTo({ top: 0, behavior: reducir ? 'auto' : 'smooth' });
        }

        form.addEventListener('submit', function (e) {
            e.preventDefault(); ocultar();
            var d = solo(numero.value);
            if (titular.value.trim().length < 2) { return mostrar('Escribe el nombre del titular.'); }
            if (d.length < 13 || !luhn(d)) { return mostrar('El número de tarjeta no es válido.'); }
            var m = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(vence.value);
            if (!m) { return mostrar('Escribe el vencimiento como MM/AA.'); }
            var hoy = new Date();
            if (2000 + parseInt(m[2], 10) < hoy.getFullYear() || (2000 + parseInt(m[2], 10) === hoy.getFullYear() && parseInt(m[1], 10) < hoy.getMonth() + 1)) {
                return mostrar('La tarjeta está vencida.');
            }
            if (!/^\d{3,4}$/.test(cvv.value)) { return mostrar('El CVV debe tener 3 o 4 dígitos.'); }

            boton.disabled = true;
            panel('pagoProcesando');
            var paso = document.getElementById('pagoPaso');
            var pasos = ['Verificando la tarjeta', 'Contactando al banco', 'Confirmando tu reservación'];
            var i = 0;
            var animacion = setInterval(function () { i = (i + 1) % pasos.length; paso.textContent = pasos[i]; }, 700);
            var minimo = new Promise(function (ok) { setTimeout(ok, reducir ? 300 : 1800); });

            var peticion = TN.post('/api/pago/' + encodeURIComponent(folio) + '/procesar', {
                titular: titular.value.trim(),
                numero: d,
                vencimiento: vence.value,
                cvv: cvv.value
            }).then(function (data) { return { ok: true, data: data }; })
              .catch(function (err) { return { ok: false, data: { error: err.message || 'No se pudo procesar el pago.' } }; });

            Promise.all([peticion, minimo]).then(function (res) {
                clearInterval(animacion);
                cvv.value = ''; numero.value = '';
                var r = res[0];
                if (r.ok && r.data.aprobado) {
                    document.getElementById('pagoRef').textContent = r.data.referencia || '—';
                    panel('pagoOk');
                } else if (r.ok) {
                    document.getElementById('pagoNoTexto').textContent = r.data.mensaje;
                    panel('pagoNo');
                } else {
                    panel('pagoTarjeta'); boton.disabled = false;
                    mostrar(r.data.error || 'No se pudo procesar el pago.');
                }
            });
        });
    }

    window.TNPago = { init: init };
})();
