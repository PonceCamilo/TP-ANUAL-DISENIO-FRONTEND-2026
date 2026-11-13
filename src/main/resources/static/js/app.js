/* DonaTrack · comportamiento del cliente liviano (sin frameworks). */
(function () {
  'use strict';

  var UMBRAL_CARGA_MS = 300; // Entrega 6: indicador de carga si la espera supera 300 ms

  // ---- indicador de carga global -------------------------------------------------
  var capa = document.getElementById('cargando');
  var temporizador = null;

  function mostrarCarga() {
    if (!capa) return;
    temporizador = setTimeout(function () {
      capa.classList.add('visible');
      capa.setAttribute('aria-busy', 'true');
    }, UMBRAL_CARGA_MS);
  }
  function ocultarCarga() {
    clearTimeout(temporizador);
    if (capa) { capa.classList.remove('visible'); capa.removeAttribute('aria-busy'); }
  }
  // al volver con "atrás" el navegador restaura la página: apagamos el spinner
  window.addEventListener('pageshow', ocultarCarga);

  // ---- formularios: evitar doble envío y mostrar progreso ---------------------------
  document.querySelectorAll('form[data-carga]').forEach(function (form) {
    form.addEventListener('submit', function () {
      if (!form.checkValidity()) return;
      var boton = form.querySelector('button[type="submit"]');
      if (boton) {
        boton.setAttribute('aria-busy', 'true');
        boton.dataset.texto = boton.textContent;
        boton.textContent = boton.dataset.cargando || 'Enviando…';
        setTimeout(function () { boton.disabled = true; }, 0); // después de que el navegador arme el envío
      }
      mostrarCarga();
    });
  });

  var registroGoogle = document.getElementById('registro-google');
  var tipoDonante = document.getElementById('tipoDonante');
  if (registroGoogle && tipoDonante) {
    function actualizarEnlaceGoogle() {
      var url = new URL(registroGoogle.href);
      url.searchParams.set('tipoDonante', tipoDonante.value);
      registroGoogle.href = url.href;
    }

    tipoDonante.addEventListener('change', actualizarEnlaceGoogle);
    window.addEventListener('pageshow', actualizarEnlaceGoogle);
    actualizarEnlaceGoogle();
  }

  // ---- insignias: guardar la visibilidad sin recargar el panel -----------------------
  var insigniasVisibles = document.querySelector('[data-insignias-visibles]');
  document.querySelectorAll('form[data-visibilidad-insignia]').forEach(function (form) {
    var fila = form.closest('li');
    var nombre = fila.querySelector('.item-titulo').textContent;
    var estado = fila.querySelector('[data-estado-insignia]');
    var aviso = fila.querySelector('[data-aviso-insignia]');
    var boton = form.querySelector('button');
    var tachado = boton.querySelector('[data-ojito-tachado]');
    var campoVisible = form.elements.namedItem('visible');
    var insignia = insigniasVisibles.querySelector('[data-insignia-id="' + form.dataset.visibilidadInsignia + '"]');
    var guardando = false;

    form.addEventListener('submit', async function (evento) {
      evento.preventDefault();
      if (guardando) return;
      var visible = campoVisible.value === 'true';
      guardando = true;
      boton.setAttribute('aria-disabled', 'true');
      aviso.textContent = 'Guardando…';
      try {
        var respuesta = await fetch(form.action, {
          method: 'POST',
          headers: { 'X-Actualizar-Insignia': 'true' },
          body: new URLSearchParams(new FormData(form)),
          redirect: 'manual'
        });
        if (respuesta.type === 'opaqueredirect' || respuesta.status === 401) {
          aviso.textContent = 'Recargá la página y volvé a intentarlo.';
          return;
        }
        if (respuesta.status !== 204) {
          var tipo = respuesta.headers.get('Content-Type') || '';
          var error = tipo.includes('application/json') ? await respuesta.json() : null;
          aviso.textContent = error?.mensaje || 'No pudimos confirmar el cambio. Volvé a intentarlo.';
          return;
        }

        campoVisible.value = String(!visible);
        estado.textContent = visible ? 'Visible en el perfil' : 'Oculta en el perfil';
        var accion = visible ? 'Ocultar' : 'Mostrar';
        boton.setAttribute('aria-label', accion + ' insignia ' + nombre);
        boton.title = accion + ' insignia';
        tachado.setAttribute('visibility', visible ? 'hidden' : 'visible');
        insignia.hidden = !visible;
        insigniasVisibles.hidden = !insigniasVisibles.querySelector('[data-insignia-id]:not([hidden])');
        aviso.textContent = '';
      } catch {
        aviso.textContent = 'No pudimos confirmar el cambio. Volvé a intentarlo.';
      } finally {
        guardando = false;
        boton.removeAttribute('aria-disabled');
      }
    });
  });

  // ---- navegación entre pantallas: mismo indicador --------------------------------
  document.querySelectorAll('a[data-carga]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
      mostrarCarga();
    });
  });

  // ---- toasts: se cierran solos (los de error quedan más tiempo) ----------------------
  document.querySelectorAll('.toast').forEach(function (t) {
    var ms = t.classList.contains('toast-error') ? 9000 : 5500;
    var id = setTimeout(function () { t.remove(); }, ms);
    t.addEventListener('mouseenter', function () { clearTimeout(id); });
    var cerrar = t.querySelector('.cerrar');
    if (cerrar) cerrar.addEventListener('click', function () { t.remove(); });
  });

  // ---- banners descartables (tips de onboarding): recuerdan que ya se cerraron ----
  document.querySelectorAll('.banner[data-descartable]').forEach(function (b) {
    var clave = 'donatrack.banner.' + b.dataset.descartable;
    try { if (localStorage.getItem(clave)) { b.hidden = true; return; } } catch (e) { /* sin storage: se muestra siempre */ }
    var cerrar = b.querySelector('.cerrar');
    if (cerrar) cerrar.addEventListener('click', function () {
      b.hidden = true;
      try { localStorage.setItem(clave, '1'); } catch (e) { /* ignorado */ }
    });
  });

  // ---- modales de confirmación (elemento <dialog>: foco atrapado y Esc de fábrica) ----
  document.querySelectorAll('[data-modal]').forEach(function (disparador) {
    var dialogo = document.getElementById(disparador.dataset.modal);
    if (!dialogo || typeof dialogo.showModal !== 'function') return;
    disparador.addEventListener('click', function (e) {
      e.preventDefault();
      var destino = dialogo.querySelector('form');
      if (destino && disparador.dataset.accion) destino.setAttribute('action', disparador.dataset.accion);
      var detalle = dialogo.querySelector('[data-detalle]');
      if (detalle && disparador.dataset.detalle) detalle.textContent = disparador.dataset.detalle;
      dialogo.showModal();
      var cancelar = dialogo.querySelector('[data-cancelar]');
      if (cancelar) cancelar.focus();
    });
    dialogo.querySelectorAll('[data-cancelar]').forEach(function (b) {
      b.addEventListener('click', function () { dialogo.close(); disparador.focus(); });
    });
  });

  // ---- campos condicionales (necesidad recurrente → periodicidad) ----------------
  document.querySelectorAll('[data-muestra-si]').forEach(function (bloque) {
    var partes = bloque.dataset.muestraSi.split('=');
    var radios = document.querySelectorAll('input[name="' + partes[0] + '"]');
    function actualizar() {
      var elegido = document.querySelector('input[name="' + partes[0] + '"]:checked');
      var visible = elegido && elegido.value === partes[1];
      bloque.hidden = !visible;
      bloque.querySelectorAll('select,input').forEach(function (c) { c.disabled = !visible; });
    }
    radios.forEach(function (r) { r.addEventListener('change', actualizar); });
    actualizar();
  });

  // ---- cantidad con − / + ----------------------------------------------------------
  document.querySelectorAll('.cantidad').forEach(function (grupo) {
    var campo = grupo.querySelector('input');
    grupo.querySelectorAll('button[data-paso]').forEach(function (b) {
      b.addEventListener('click', function () {
        var min = parseInt(campo.min || '1', 10);
        var nuevo = (parseInt(campo.value || String(min), 10) || min) + parseInt(b.dataset.paso, 10);
        campo.value = Math.max(min, nuevo);
      });
    });
  });

  // ---- contraseña: mostrar / ocultar ------------------------------------------------
  document.querySelectorAll('[data-mostrar-clave]').forEach(function (b) {
    var campo = document.getElementById(b.dataset.mostrarClave);
    if (!campo) return;
    b.addEventListener('click', function () {
      var visible = campo.type === 'text';
      campo.type = visible ? 'password' : 'text';
      b.textContent = visible ? 'Mostrar' : 'Ocultar';
      b.setAttribute('aria-pressed', visible ? 'false' : 'true');
    });
  });

  // ---- filtros: elegir una opción ya filtra (el botón queda para las fechas) -------------
  document.querySelectorAll('form[data-autoenviar]').forEach(function (form) {
    form.addEventListener('change', function (ev) {
      var t = ev.target;
      if (t && (t.type === 'radio' || t.type === 'checkbox' || t.tagName === 'SELECT')) {
        if (form.requestSubmit) { form.requestSubmit(); } else { form.submit(); }
      }
    });
  });

  // ---- fechas de vencimiento: no se puede donar algo vencido ----------------------------
  // El mínimo es hoy, en la fecha de quien usa el navegador (no la del servidor).
  document.querySelectorAll('input[type="date"][data-desde-hoy]').forEach(function (campo) {
    var hoy = new Date();
    hoy.setMinutes(hoy.getMinutes() - hoy.getTimezoneOffset());
    campo.min = hoy.toISOString().slice(0, 10);
    function revisar() {
      campo.setCustomValidity(campo.value && campo.value < campo.min
        ? 'Ese bien ya está vencido: no se puede donar. Elegí una fecha de hoy en adelante.' : '');
    }
    campo.addEventListener('input', revisar);
    campo.addEventListener('change', revisar);
    revisar();
  });

  // ---- crear cuenta: las dos contraseñas tienen que coincidir ---------------------------
  document.querySelectorAll('form[data-repetir]').forEach(function (form) {
    var a = form.querySelector('[name="password"]');
    var b = form.querySelector('[name="repetir"]');
    if (!a || !b) return;
    function revisar() {
      b.setCustomValidity(b.value && a.value !== b.value ? 'Las contraseñas no coinciden.' : '');
    }
    a.addEventListener('input', revisar);
    b.addEventListener('input', revisar);
  });
})();
