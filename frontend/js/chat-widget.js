/* Chat flotante: bolita en la esquina que abre un panel lateral. Usa TN.fetch (JWT Bearer). */
(function () {
    'use strict';

    var montado = false;

    function mount() {
        var raiz = document.getElementById('chatWidget');
        if (!raiz || document.getElementById('asistente') || montado) { return; }
        montado = true;
        raiz.hidden = false;

        var api = raiz.getAttribute('data-api') || '/api/hotel-ai';
        var bolita = document.getElementById('cwBolita');
        var panel = document.getElementById('cwPanel');
        var msgs = document.getElementById('cwMsgs');
        var vacio = document.getElementById('cwVacio');
        var form = document.getElementById('cwForm');
        var texto = document.getElementById('cwTexto');
        var btnEnviar = document.getElementById('cwEnviar');
        var estado = document.getElementById('cwEstado');
        var punto = document.getElementById('cwPunto');

        var CLAVE_CHAT = 'terranova.chatActual';
        var CLAVE_MODELO = 'terranova.modelo';
        var CLAVE_ABIERTO = 'terranova.cwAbierto';
        var TIMEOUT_MS = 240000;

        var actual = null, enviando = false, cargado = false;

        function guardar(k, v, sesion) { try { (sesion ? sessionStorage : localStorage).setItem(k, v); } catch (e) { } }
        function leer(k, sesion) { try { return (sesion ? sessionStorage : localStorage).getItem(k); } catch (e) { return null; } }
        function modelo() { return leer(CLAVE_MODELO) || 'gemini'; }

        function pedir(path, op) {
            return TN.fetch(path, op).then(function (r) {
                if (!r.ok) { return r.text().then(function (t) { throw new Error(t || ('Error ' + r.status)); }); }
                var tipo = r.headers.get('content-type') || '';
                return tipo.indexOf('json') !== -1 ? r.json() : null;
            });
        }

        function abrir(mensaje) {
            raiz.classList.add('abierto');
            panel.setAttribute('aria-hidden', 'false');
            guardar(CLAVE_ABIERTO, '1', true);
            punto.hidden = true;
            if (!cargado) { iniciar(); }
            if (mensaje) { texto.value = mensaje; ajustarAlto(); }
            setTimeout(function () { texto.focus(); }, 250);
        }
        function cerrar() {
            raiz.classList.remove('abierto');
            panel.setAttribute('aria-hidden', 'true');
            guardar(CLAVE_ABIERTO, '0', true);
        }

        bolita.addEventListener('click', function () { raiz.classList.contains('abierto') ? cerrar() : abrir(); });
        document.getElementById('cwMin').addEventListener('click', cerrar);
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && raiz.classList.contains('abierto')) { cerrar(); } });

        document.addEventListener('click', function (e) {
            var b = e.target.closest ? e.target.closest('[data-abrir-chat]') : null;
            if (b) { abrir(b.getAttribute('data-mensaje') || ''); }
        });

        var MARCA_HAB = '[[VER_HABITACIONES]]';
        function ponerTexto(el, txt) {
            el.textContent = '';
            String(txt).split(/(\/pago\/\d+|\[\[VER_HABITACIONES\]\])/).forEach(function (parte) {
                if (/^\/pago\/(\d+)$/.test(parte)) {
                    var id = parte.replace('/pago/', '');
                    var a = document.createElement('a');
                    a.href = '/pago?folio=' + encodeURIComponent(id);
                    a.textContent = 'Pagar ahora';
                    a.className = 'btn btn-emerald btn-sm ms-1 chat-pago-link';
                    el.appendChild(a);
                } else if (parte === MARCA_HAB) {
                    var b = document.createElement('button');
                    b.type = 'button'; b.className = 'btn btn-tn btn-sm mt-2 d-block';
                    b.setAttribute('data-action', 'scroll-habitaciones');
                    b.innerHTML = '<i class="bi bi-door-open me-1"></i>Ver habitaciones en la página';
                    el.appendChild(b);
                } else if (parte) { el.appendChild(document.createTextNode(parte)); }
            });
        }

        function rutaInicio() { return raiz.getAttribute('data-inicio') || '/inicio'; }
        function enInicio() { return location.pathname.replace(/\/+$/, '') === rutaInicio().replace(/\/+$/, ''); }
        function esMovil() { return window.matchMedia('(max-width: 575.98px)').matches; }
        function irAHabitaciones() {
            var sec = document.getElementById('habitaciones');
            if (enInicio() && sec) {
                if (esMovil()) { cerrar(); }
                setTimeout(function () { sec.scrollIntoView({ behavior: 'smooth', block: 'start' }); }, esMovil() ? 300 : 0);
            } else {
                location.href = rutaInicio() + '#habitaciones';
            }
        }
        document.addEventListener('click', function (e) {
            var t = e.target.closest ? e.target.closest('[data-action="scroll-habitaciones"]') : null;
            if (t) { e.preventDefault(); irAHabitaciones(); }
        });

        function burbuja(clase, contenido) {
            if (vacio) { vacio.style.display = 'none'; }
            var b = document.createElement('div');
            b.className = 'cw-b ' + clase;
            var t = document.createElement('div');
            t.className = 'cw-t';
            ponerTexto(t, contenido);
            b.appendChild(t);
            msgs.appendChild(b);
            msgs.scrollTop = msgs.scrollHeight;
            return b;
        }
        function limpiar() {
            msgs.querySelectorAll('.cw-b').forEach(function (b) { b.remove(); });
            if (vacio) { vacio.style.display = ''; }
        }
        function quitarConfirmar() { msgs.querySelectorAll('.cw-conf').forEach(function (c) { c.remove(); }); }
        function botonesConfirmar(b) {
            var u = TN.getUser();
            if (u && u.rol === 'GERENTE') { return; }
            quitarConfirmar();
            var caja = document.createElement('div');
            caja.className = 'cw-conf';
            var si = document.createElement('button');
            si.type = 'button'; si.className = 'btn btn-emerald btn-sm'; si.textContent = 'Sí, confirmar';
            si.addEventListener('click', function () { enviar('Sí, confirmo'); });
            var no = document.createElement('button');
            no.type = 'button'; no.className = 'btn btn-soft btn-sm'; no.textContent = 'No, descartar';
            no.addEventListener('click', function () { enviar('No, descartar'); });
            caja.appendChild(si); caja.appendChild(no);
            b.appendChild(caja);
            msgs.scrollTop = msgs.scrollHeight;
        }

        function abrirChat(id) {
            actual = id; guardar(CLAVE_CHAT, id); limpiar();
            return pedir(api + '/chats/' + encodeURIComponent(id)).then(function (d) {
                var ultima = null;
                (d.mensajes || []).forEach(function (m) {
                    if (m.rol === 'usuario') { burbuja('cw-yo', m.texto); }
                    else if (m.rol === 'error') { burbuja('cw-ia cw-error', m.texto); }
                    else { ultima = burbuja('cw-ia', m.texto); }
                });
                if (d.pendiente && ultima) { botonesConfirmar(ultima); }
            });
        }

        function nuevoChat() {
            return pedir(api + '/chats', { method: 'POST' }).then(function (c) {
                actual = c.id; guardar(CLAVE_CHAT, actual); limpiar();
            });
        }

        function iniciar() {
            cargado = true;
            pedir(api + '/chats').then(function (lista) {
                lista = lista || [];
                var g = leer(CLAVE_CHAT);
                var existe = lista.some(function (c) { return c.id === g; });
                if (existe) { return abrirChat(g); }
                if (lista.length) { return abrirChat(lista[0].id); }
                return nuevoChat();
            }).catch(function (e) {
                burbuja('cw-ia cw-error', e.message || 'No se pudo cargar el chat.');
                cargado = false;
            });
        }

        function enviar(mensaje) {
            var pregunta = (mensaje || texto.value).trim();
            if (enviando) { return; }
            if (!pregunta) { texto.focus(); return; }
            if (!actual) { nuevoChat().then(function () { enviar(pregunta); }).catch(function (e) { burbuja('cw-ia cw-error', e.message); }); return; }

            quitarConfirmar();
            burbuja('cw-yo', pregunta);
            var pensando = burbuja('cw-ia cw-pensando', '');
            pensando.firstChild.innerHTML = '<span class="cw-puntos"><i></i><i></i><i></i></span>';
            if (!mensaje) { texto.value = ''; ajustarAlto(); }
            enviando = true;
            btnEnviar.disabled = true;
            estado.textContent = 'Escribiendo…';

            var control = new AbortController();
            var corte = setTimeout(function () { control.abort(); }, TIMEOUT_MS);

            pedir(api + '/ask?model=' + encodeURIComponent(modelo()) + '&conversationId=' + encodeURIComponent(actual), {
                method: 'POST',
                body: { pregunta: pregunta },
                signal: control.signal
            }).then(function (d) {
                pensando.classList.remove('cw-pensando');
                ponerTexto(pensando.firstChild, d.respuesta);
                if (d.requiereConfirmacion) { botonesConfirmar(pensando); }
                if (enInicio() && String(d.respuesta).indexOf(MARCA_HAB) !== -1) { irAHabitaciones(); }
                if (!raiz.classList.contains('abierto')) { punto.hidden = false; }
            }).catch(function (err) {
                pensando.classList.remove('cw-pensando');
                pensando.classList.add('cw-error');
                pensando.firstChild.textContent = err.name === 'AbortError'
                    ? 'El asistente tardó demasiado. Intenta de nuevo.'
                    : (err.message || 'No se pudo contactar al servidor.');
            }).finally(function () {
                clearTimeout(corte);
                enviando = false;
                btnEnviar.disabled = false;
                estado.textContent = 'En línea';
                msgs.scrollTop = msgs.scrollHeight;
                texto.focus();
            });
        }

        function ajustarAlto() {
            texto.style.height = 'auto';
            texto.style.height = Math.min(texto.scrollHeight, 110) + 'px';
        }

        form.addEventListener('submit', function (e) { e.preventDefault(); enviar(); });
        texto.addEventListener('input', ajustarAlto);
        texto.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); enviar(); }
        });
        document.getElementById('cwNuevo').addEventListener('click', function () {
            if (enviando) { return; }
            nuevoChat().then(function () { texto.focus(); }).catch(function (e) { burbuja('cw-ia cw-error', e.message); });
        });
        raiz.querySelectorAll('.cw-sugs .chip').forEach(function (c) {
            c.addEventListener('click', function () { enviar(c.textContent.trim()); });
        });

        var vv = window.visualViewport;
        function ajustar() {
            if (!vv) { return; }
            document.documentElement.style.setProperty('--vvh', vv.height + 'px');
            if (panel && window.matchMedia('(max-width: 575.98px)').matches) {
                panel.style.top = vv.offsetTop + 'px';
                if (msgs) { msgs.scrollTop = msgs.scrollHeight; }
            }
        }
        if (vv) { vv.addEventListener('resize', ajustar); vv.addEventListener('scroll', ajustar); ajustar(); }
        new MutationObserver(function () {
            var abierto = raiz.classList.contains('abierto') && window.matchMedia('(max-width: 575.98px)').matches;
            document.body.classList.toggle('cw-bloqueado', abierto);
        }).observe(raiz, { attributes: true, attributeFilter: ['class'] });

        if (leer(CLAVE_ABIERTO, true) === '1') { abrir(); }
    }

    window.TNChatWidget = { mount: mount };
})();
