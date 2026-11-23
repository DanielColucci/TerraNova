// Pantalla completa del Asistente de IA (/asistente). Usa TN.fetch con JWT Bearer.
// Mantiene comportamiento estrictamente solo lectura y sugerencias de análisis para GERENTE.
(function () {
    'use strict';

    function ponerTexto(el, texto) {
        el.textContent = '';
        String(texto).split(/(\/pago\/\d+|\[\[VER_HABITACIONES\]\])/).forEach(function (parte) {
            if (/^\/pago\/(\d+)$/.test(parte)) {
                var id = parte.replace('/pago/', '');
                var a = document.createElement('a');
                a.href = '/pago?folio=' + encodeURIComponent(id);
                a.textContent = 'Pagar ahora';
                a.className = 'btn btn-emerald btn-sm ms-1 chat-pago-link';
                el.appendChild(a);
            } else if (parte === '[[VER_HABITACIONES]]') {
                var h = document.createElement('a');
                h.href = '/inicio#habitaciones';
                h.className = 'btn btn-tn btn-sm mt-2 d-block';
                h.setAttribute('data-action', 'scroll-habitaciones');
                h.innerHTML = '<i class="bi bi-door-open me-1"></i>Ver habitaciones en la página';
                el.appendChild(h);
            } else if (parte) {
                el.appendChild(document.createTextNode(parte));
            }
        });
    }

    function init(user) {
        var raiz = document.getElementById('asistente');
        if (!raiz) { return; }

        var api = '/api/hotel-ai';
        var esGerente = user && user.rol === 'GERENTE';
        var esAdmin = user && user.rol === 'ADMINISTRADOR';
        var esRecep = user && user.rol === 'RECEPCIONISTA';

        // Encabezado y sugerencias adaptados por rol
        var tituloPag = document.getElementById('asistTitulo');
        var subPag = document.getElementById('asistSub');
        var accPag = document.getElementById('asistAcciones');
        var sugsEl = document.getElementById('asistSugs');

        if (esGerente) {
            if (tituloPag) { tituloPag.textContent = 'Asistente gerencial de IA'; }
            if (subPag) {
                subPag.textContent = 'Consulta datos crudos e indicadores reales del panel gerencial en lenguaje natural: ingresos, ocupación, rentabilidad, tendencias, huéspedes y pagos (solo lectura, sin gestión de reservaciones).';
            }
            if (accPag) {
                accPag.innerHTML = '<a href="/gerente/dashboard" class="btn btn-soft"><i class="bi bi-graph-up-arrow me-1"></i>Panel gerencial</a>'
                    + '<a href="/gerente/informes" class="btn btn-soft"><i class="bi bi-file-earmark-bar-graph me-1"></i>Informes</a>';
            }
            if (sugsEl) {
                sugsEl.innerHTML = [
                    '¿Cómo van los ingresos aprobados, pagos y ticket promedio de este mes?',
                    '¿Cuál es la ocupación actual y del periodo por hotel?',
                    'Muéstrame las reservaciones por estado y el porcentaje de pérdidas',
                    '¿Qué tipos de habitación son más rentables y cuál es la estancia promedio?',
                    'Muéstrame la evolución anual, temporada y comparativa contra el periodo anterior',
                    '¿De dónde vienen los huéspedes, reservan más por Web o IA y cómo pagan?'
                ].map(function (s) { return '<button type="button" class="chip sugerencia">' + TN.esc(s) + '</button>'; }).join('');
            }
        } else {
            if (accPag) {
                var href = esAdmin ? '/reservaciones/lista' : (esRecep ? '/recepcion/panel' : '/mis-reservaciones');
                var lbl = esAdmin ? 'Ver reservaciones' : (esRecep ? 'Panel de recepción' : 'Mis reservaciones');
                accPag.innerHTML = '<a href="' + href + '" class="btn btn-soft"><i class="bi bi-calendar-check me-1"></i>' + lbl + '</a>';
            }
        }

        var form = document.getElementById('formPregunta');
        var texto = document.getElementById('pregunta');
        var historial = document.getElementById('historial');
        var vacio = document.getElementById('chatVacio');
        var btnEnviar = document.getElementById('btnEnviar');
        var btnNuevo = document.getElementById('btnNuevoChat');
        var listaEl = document.getElementById('listaChats');
        var tituloEl = document.getElementById('tituloChat');

        var CLAVE_CHAT = 'terranova.chatActual';
        var CLAVE_MODELO = 'terranova.modelo';
        var TIMEOUT_MS = 240000;

        var chats = [];
        var actual = null;
        var enviando = false;

        function guardar(k, v) { try { localStorage.setItem(k, v); } catch (e) { } }
        function leer(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
        function modeloElegido() { return document.querySelector('input[name="modelo"]:checked').value; }

        function pedir(path, opciones) {
            return TN.fetch(path, opciones).then(function (resp) {
                if (!resp.ok) {
                    return resp.text().then(function (t) { throw new Error(t || ('Error ' + resp.status)); });
                }
                var tipo = resp.headers.get('content-type') || '';
                return tipo.indexOf('json') !== -1 ? resp.json() : null;
            });
        }

        function dibujarLista() {
            listaEl.innerHTML = '';
            if (!chats.length) {
                var p = document.createElement('div');
                p.className = 'text-muted small p-2';
                p.textContent = 'Aún no hay chats.';
                listaEl.appendChild(p);
                return;
            }
            chats.forEach(function (c) {
                var fila = document.createElement('div');
                fila.className = 'chat-item' + (c.id === actual ? ' activo' : '');

                var abrir = document.createElement('button');
                abrir.type = 'button';
                abrir.className = 'chat-item-titulo';
                abrir.title = c.titulo;
                abrir.textContent = c.titulo;
                abrir.addEventListener('click', function () { abrirChat(c.id); });

                var acciones = document.createElement('div');
                acciones.className = 'chat-item-acciones';

                var renombrar = document.createElement('button');
                renombrar.type = 'button';
                renombrar.title = 'Cambiar nombre';
                renombrar.innerHTML = '<i class="bi bi-pencil"></i>';
                renombrar.addEventListener('click', function () { renombrarChat(c); });

                var borrar = document.createElement('button');
                borrar.type = 'button';
                borrar.title = 'Eliminar chat';
                borrar.innerHTML = '<i class="bi bi-trash3"></i>';
                borrar.addEventListener('click', function () { eliminarChat(c); });

                acciones.appendChild(renombrar);
                acciones.appendChild(borrar);
                fila.appendChild(abrir);
                fila.appendChild(acciones);
                listaEl.appendChild(fila);
            });
        }

        function cargarLista() {
            return pedir(api + '/chats').then(function (datos) {
                chats = datos || [];
                dibujarLista();
                actualizarTitulo();
            });
        }

        function actualizarTitulo() {
            var c = chats.filter(function (x) { return x.id === actual; })[0];
            tituloEl.innerHTML = '<i class="bi bi-chat-dots me-2 text-primary"></i>';
            tituloEl.appendChild(document.createTextNode(c ? c.titulo : 'Conversación'));
        }

        function nuevoChat() {
            return pedir(api + '/chats', { method: 'POST' }).then(function (c) {
                chats.unshift(c);
                actual = c.id;
                guardar(CLAVE_CHAT, actual);
                dibujarLista();
                actualizarTitulo();
                limpiarPantalla();
                texto.focus();
            });
        }

        function renombrarChat(c) {
            var nombre = window.prompt('Nuevo nombre del chat:', c.titulo);
            if (nombre === null || !nombre.trim()) { return; }
            pedir(api + '/chats/' + encodeURIComponent(c.id), {
                method: 'PUT',
                body: { titulo: nombre.trim() }
            }).then(cargarLista).catch(function (e) { window.alert(e.message); });
        }

        function eliminarChat(c) {
            if (!window.confirm('¿Eliminar el chat «' + c.titulo + '» y su memoria? No se puede deshacer.')) { return; }
            pedir(api + '/chats/' + encodeURIComponent(c.id), { method: 'DELETE' }).then(function () {
                chats = chats.filter(function (x) { return x.id !== c.id; });
                if (actual === c.id) {
                    if (chats.length) { abrirChat(chats[0].id); } else { nuevoChat(); }
                } else {
                    dibujarLista();
                }
            }).catch(function (e) { window.alert(e.message); });
        }

        function limpiarPantalla() {
            historial.querySelectorAll('.chat-burbuja').forEach(function (b) { b.remove(); });
            if (vacio) { vacio.style.display = ''; }
        }

        function nuevaBurbuja(clase, contenido) {
            if (vacio) { vacio.style.display = 'none'; }
            var b = document.createElement('div');
            b.className = 'chat-burbuja ' + clase;
            var p = document.createElement('div');
            p.className = 'chat-texto';
            ponerTexto(p, contenido);
            b.appendChild(p);
            historial.appendChild(b);
            historial.scrollTop = historial.scrollHeight;
            return b;
        }

        function agregarMeta(burbuja, modelo, tiempoMs, herramientas) {
            if (!modelo) { return; }
            var meta = document.createElement('div');
            meta.className = 'chat-meta';
            var segs = (tiempoMs === null || tiempoMs === undefined) ? '' : ' · ' + (tiempoMs / 1000).toFixed(1) + ' s';
            var h = herramientas && herramientas.length ? herramientas : 'ninguna (no consultó la base de datos)';
            meta.textContent = 'Modelo: ' + modelo + segs + ' · Herramientas: ' + h;
            burbuja.appendChild(meta);
        }

        function quitarBotonesConfirmar() {
            historial.querySelectorAll('.chat-confirmar').forEach(function (b) { b.remove(); });
        }

        function agregarBotonesConfirmar(burbuja) {
            if (esGerente) { return; } // gerente es estrictamente solo lectura
            quitarBotonesConfirmar();
            var caja = document.createElement('div');
            caja.className = 'chat-confirmar';
            var si = document.createElement('button');
            si.type = 'button'; si.className = 'btn btn-emerald btn-sm';
            si.innerHTML = '<i class="bi bi-check2-circle me-1"></i>Sí, confirmar';
            si.addEventListener('click', function () { enviar('Sí, confirmo'); });
            var no = document.createElement('button');
            no.type = 'button'; no.className = 'btn btn-soft btn-sm';
            no.innerHTML = '<i class="bi bi-x-circle me-1"></i>No, descartar';
            no.addEventListener('click', function () { enviar('No, descartar'); });
            caja.appendChild(si); caja.appendChild(no);
            burbuja.appendChild(caja);
            historial.scrollTop = historial.scrollHeight;
        }

        function abrirChat(id) {
            actual = id;
            guardar(CLAVE_CHAT, id);
            dibujarLista();
            actualizarTitulo();
            limpiarPantalla();
            return pedir(api + '/chats/' + encodeURIComponent(id)).then(function (d) {
                var ultima = null;
                (d.mensajes || []).forEach(function (m) {
                    if (m.rol === 'usuario') {
                        nuevaBurbuja('chat-usuario', m.texto);
                    } else if (m.rol === 'error') {
                        nuevaBurbuja('chat-ia chat-error', m.texto);
                    } else {
                        ultima = nuevaBurbuja('chat-ia', m.texto);
                        agregarMeta(ultima, m.modelo, m.tiempoMs, m.herramientas);
                    }
                });
                if (d.pendiente && ultima && !esGerente) { agregarBotonesConfirmar(ultima); }
                historial.scrollTop = historial.scrollHeight;
            }).catch(function (e) {
                nuevaBurbuja('chat-ia chat-error', e.message || 'No se pudo cargar el chat.');
            });
        }

        function enviar(mensaje) {
            var pregunta = (mensaje || texto.value).trim();
            if (enviando) { return; }
            if (!pregunta) { texto.focus(); return; }
            if (!actual) { nuevoChat().then(function () { enviar(pregunta); }); return; }

            var modelo = modeloElegido();
            guardar(CLAVE_MODELO, modelo);
            quitarBotonesConfirmar();
            nuevaBurbuja('chat-usuario', pregunta);
            var pensando = nuevaBurbuja('chat-ia chat-pensando', 'Consultando con ' + modelo + '… 0 s');
            var inicio = Date.now();
            var reloj = setInterval(function () {
                pensando.firstChild.textContent = 'Consultando con ' + modelo + '… ' + Math.round((Date.now() - inicio) / 1000) + ' s';
            }, 1000);
            if (!mensaje) { texto.value = ''; }
            enviando = true;
            btnEnviar.disabled = true;

            var control = new AbortController();
            var corte = setTimeout(function () { control.abort(); }, TIMEOUT_MS);

            pedir(api + '/ask?model=' + encodeURIComponent(modelo) + '&conversationId=' + encodeURIComponent(actual), {
                method: 'POST',
                body: { pregunta: pregunta },
                signal: control.signal
            }).then(function (datos) {
                pensando.classList.remove('chat-pensando');
                ponerTexto(pensando.firstChild, datos.respuesta);
                agregarMeta(pensando, datos.modelo, datos.tiempoMs,
                    datos.herramientasUsadas && datos.herramientasUsadas.length ? datos.herramientasUsadas.join(', ') : '');
                if (datos.requiereConfirmacion && !esGerente) { agregarBotonesConfirmar(pensando); }
            }).catch(function (err) {
                pensando.classList.remove('chat-pensando');
                pensando.classList.add('chat-error');
                pensando.firstChild.textContent = err.name === 'AbortError'
                    ? 'El modelo tardó demasiado y se canceló la espera. Intenta de nuevo o cambia de modelo.'
                    : (err.message || 'No se pudo contactar al servidor.');
            }).finally(function () {
                clearInterval(reloj);
                clearTimeout(corte);
                enviando = false;
                btnEnviar.disabled = false;
                historial.scrollTop = historial.scrollHeight;
                texto.focus();
                cargarLista();
            });
        }

        form.addEventListener('submit', function (e) { e.preventDefault(); enviar(); });
        texto.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); enviar(); }
        });
        btnNuevo.addEventListener('click', function () {
            var c = chats.filter(function (x) { return x.id === actual; })[0];
            if (c && c.titulo === 'Nueva conversación' && !historial.querySelector('.chat-burbuja')) { texto.focus(); return; }
            nuevoChat().catch(function (e) { window.alert(e.message); });
        });
        document.querySelectorAll('.sugerencia').forEach(function (chip) {
            chip.addEventListener('click', function () {
                texto.value = chip.textContent.trim();
                texto.focus();
            });
        });

        var modeloGuardado = leer(CLAVE_MODELO);
        if (modeloGuardado) {
            var radio = document.querySelector('input[name="modelo"][value="' + modeloGuardado + '"]');
            if (radio) { radio.checked = true; }
        }

        cargarLista().then(function () {
            var guardado = leer(CLAVE_CHAT);
            var existe = chats.some(function (c) { return c.id === guardado; });
            if (existe) { return abrirChat(guardado); }
            if (chats.length) { return abrirChat(chats[0].id); }
            return nuevoChat();
        }).catch(function (e) {
            listaEl.textContent = '';
            nuevaBurbuja('chat-ia chat-error', e.message || 'No se pudo cargar la lista de chats.');
        });
    }

    window.TNAsistente = { init: init };
})();
