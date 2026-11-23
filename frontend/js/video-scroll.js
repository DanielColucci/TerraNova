/* Video estilo Apple: al bajar se ensancha hasta ocupar toda la pantalla y, al seguir bajando, se desvanece. */
(function () {
    'use strict';
    var seccion = document.getElementById('videoScroll');
    var marco = document.getElementById('videoFrame');
    var video = document.getElementById('videoPromo');
    var texto = document.getElementById('videoCaption');
    var btnSonido = document.getElementById('videoSonido');
    if (!seccion || !marco) { return; }

    var reducir = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var pegajoso = seccion.querySelector('.video-sticky');
    var pendiente = false;

    function limitar(v) { return Math.max(0, Math.min(1, v)); }
    function suave(t) { return t * t * (3 - 2 * t); }

    function dibujar() {
        pendiente = false;
        var r = seccion.getBoundingClientRect();
        // Altura del contenedor sticky (svh): no cambia cuando la barra del navegador móvil se oculta/aparece
        var alto = pegajoso ? pegajoso.offsetHeight : window.innerHeight;
        var recorrido = Math.max(1, seccion.offsetHeight - alto);
        var p = limitar(-r.top / recorrido);               // 0 = empieza, 1 = termina la sección

        var crece = suave(limitar(p / 0.4));                 // 0–40 %: se ensancha
        var sale = suave(limitar((p - 0.72) / 0.28));        // 72–100 %: desaparece
        var escala = 0.58 + 0.42 * crece;
        var radio = 34 * (1 - crece);

        marco.style.transform = 'scale(' + escala.toFixed(4) + ')';
        marco.style.borderRadius = radio.toFixed(1) + 'px';
        marco.style.opacity = (1 - sale).toFixed(3);
        if (texto) {
            texto.style.opacity = limitar((crece - 0.55) * 2.2 * (1 - sale * 1.5)).toFixed(3);
            texto.style.transform = 'translateY(' + ((1 - crece) * 24).toFixed(1) + 'px)';
        }
        // Pausa el video cuando ya no se ve (ahorra batería y CPU)
        if (video) {
            if (sale >= 1 || r.bottom < 0 || r.top > alto) { if (!video.paused) { video.pause(); } }
            else if (video.paused) { var pr = video.play(); if (pr && pr.catch) { pr.catch(function () { }); } }
        }
    }

    function pedir() { if (!pendiente) { pendiente = true; window.requestAnimationFrame(dibujar); } }

    if (reducir) {
        marco.style.transform = 'none'; marco.style.borderRadius = '0';
        if (texto) { texto.style.opacity = 1; }
    } else {
        window.addEventListener('scroll', pedir, { passive: true });
        window.addEventListener('resize', pedir);
        window.addEventListener('orientationchange', pedir);
        dibujar();
    }

    if (btnSonido && video) {
        btnSonido.addEventListener('click', function () {
            video.muted = !video.muted;
            btnSonido.innerHTML = video.muted ? '<i class="bi bi-volume-mute-fill"></i>' : '<i class="bi bi-volume-up-fill"></i>';
            if (video.paused) { var pr = video.play(); if (pr && pr.catch) { pr.catch(function () { }); } }
        });
    }
})();
