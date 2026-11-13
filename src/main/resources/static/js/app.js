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
  // Los bienes de una donación se resuelven dentro de su bloque: cada grupo de radios es independiente.
  document.querySelectorAll('[data-muestra-si]').forEach(function (bloque) {
    if (bloque.closest('[data-bien]')) return;
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

  function radiosDelBien(raiz, nombre) {
    return Array.prototype.filter.call(raiz.querySelectorAll('input[type="radio"]'), function (r) {
      return r.name === nombre || r.name.endsWith('.' + nombre);
    });
  }

  function prepararBien(bloque) {
    if (bloque.dataset.listo === '1') return;
    bloque.dataset.listo = '1';
    bloque.querySelectorAll('[data-muestra-si]').forEach(function (condicional) {
      var partes = condicional.dataset.muestraSi.split('=');
      var radios = radiosDelBien(bloque, partes[0]);
      function actualizar() {
        var elegido = radios.filter(function (r) { return r.checked; })[0];
        var visible = elegido && elegido.value === partes[1];
        condicional.hidden = !visible;
        condicional.querySelectorAll('select,input,textarea').forEach(function (c) { c.disabled = !visible; });
      }
      radios.forEach(function (r) { r.addEventListener('change', actualizar); });
      actualizar();
    });
    bloque.querySelectorAll('.cantidad').forEach(engancharCantidad);
    bloque.querySelectorAll('input[type="date"][data-desde-hoy]').forEach(limitarFecha);
    var quitar = bloque.querySelector('[data-quitar-bien]');
    if (quitar) {
      quitar.addEventListener('click', function () {
        var lista = document.getElementById('lista-bienes');
        if (!lista || lista.querySelectorAll('[data-bien]').length < 2) return;
        bloque.remove();
        reindexarBienes();
      });
    }
  }

  function reindexarBienes() {
    var bloques = document.querySelectorAll('#lista-bienes [data-bien]');
    bloques.forEach(function (bloque, i) {
      var titulo = bloque.querySelector('[data-numero]');
      if (titulo) titulo.textContent = 'Bien ' + (i + 1);
      bloque.setAttribute('aria-label', 'Bien ' + (i + 1));
      bloque.querySelectorAll('[name]').forEach(function (campo) {
        var punto = campo.name.lastIndexOf('.');
        var cola = punto === -1 ? campo.name : campo.name.slice(punto + 1);
        campo.name = 'bienes[' + i + '].' + cola;
      });
      bloque.querySelectorAll('[id]').forEach(function (el) {
        if (/-\d+$/.test(el.id)) el.id = el.id.replace(/-\d+$/, '-' + i);
      });
      bloque.querySelectorAll('label[for]').forEach(function (el) {
        if (/-\d+$/.test(el.htmlFor)) el.htmlFor = el.htmlFor.replace(/-\d+$/, '-' + i);
      });
      bloque.querySelectorAll('[aria-describedby]').forEach(function (el) {
        var ref = el.getAttribute('aria-describedby');
        if (ref && /-\d+$/.test(ref)) el.setAttribute('aria-describedby', ref.replace(/-\d+$/, '-' + i));
      });
      var quitar = bloque.querySelector('[data-quitar-bien]');
      if (quitar) quitar.hidden = bloques.length < 2;
    });
  }

  function vaciarBien(bloque) {
    bloque.querySelectorAll('input, textarea, select').forEach(function (campo) {
      if (campo.type === 'radio' || campo.type === 'checkbox') campo.checked = false;
      else if (campo.tagName === 'SELECT') campo.selectedIndex = 0;
      else campo.value = campo.type === 'number' ? '1' : '';
      campo.setCustomValidity('');
    });
    bloque.querySelectorAll('input[type="radio"][value="true"]').forEach(function (radio) {
      if (radio.name.endsWith('.esNuevo')) radio.checked = true;
    });
  }

  var agregarBien = document.getElementById('agregar-bien');
  if (agregarBien) {
    agregarBien.addEventListener('click', function () {
      var lista = document.getElementById('lista-bienes');
      var origen = lista && lista.querySelector('[data-bien]');
      if (!origen) return;
      var copia = origen.cloneNode(true);
      copia.removeAttribute('data-listo');
      vaciarBien(copia);
      lista.appendChild(copia);
      reindexarBienes();
      prepararBien(copia);
      var titulo = copia.querySelector('[data-numero]');
      if (titulo) titulo.focus();
    });
    document.querySelectorAll('#lista-bienes [data-bien]').forEach(prepararBien);
    reindexarBienes();
  }

  // ---- cantidad con − / + ----------------------------------------------------------
  function engancharCantidad(grupo) {
    var campo = grupo.querySelector('input');
    grupo.querySelectorAll('button[data-paso]').forEach(function (b) {
      b.addEventListener('click', function () {
        var min = parseInt(campo.min || '1', 10);
        var nuevo = (parseInt(campo.value || String(min), 10) || min) + parseInt(b.dataset.paso, 10);
        campo.value = Math.max(min, nuevo);
      });
    });
  }
  document.querySelectorAll('.cantidad').forEach(function (grupo) {
    if (grupo.closest('[data-bien]')) return;
    engancharCantidad(grupo);
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
  function limitarFecha(campo) {
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
  }
  document.querySelectorAll('input[type="date"][data-desde-hoy]').forEach(function (campo) {
    if (campo.closest('[data-bien]')) return;
    limitarFecha(campo);
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
