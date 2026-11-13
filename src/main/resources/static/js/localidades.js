/*
 * Entrega 6: localidades sugeridas por provincia, para el campo "Localidad" de los
 * formularios de alta. Son las principales de cada una; si la tuya no está, se
 * escribe a mano. Al cambiar la provincia se cambia la lista de sugerencias.
 */
(function () {
  'use strict';

  var LOCALIDADES = {
    'Buenos Aires': ['La Plata', 'Mar del Plata', 'Bahía Blanca', 'Tandil', 'Quilmes', 'Lanús', 'Avellaneda', 'Lomas de Zamora',
      'Banfield', 'Adrogué', 'Berazategui', 'Florencio Varela', 'Almirante Brown', 'Esteban Echeverría', 'Ezeiza',
      'La Matanza', 'San Justo', 'Ramos Mejía', 'González Catán', 'Morón', 'Castelar', 'Haedo', 'Ituzaingó', 'Hurlingham',
      'Merlo', 'Moreno', 'General Rodríguez', 'Luján', 'Tres de Febrero', 'Caseros', 'San Martín', 'Vicente López',
      'Olivos', 'San Isidro', 'Martínez', 'San Fernando', 'Tigre', 'Pilar', 'Escobar', 'José C. Paz', 'Malvinas Argentinas',
      'San Miguel', 'Zárate', 'Campana', 'San Nicolás', 'Pergamino', 'Junín', 'Olavarría', 'Azul', 'Necochea',
      'Chivilcoy', 'Mercedes', 'Cañuelas', 'Berisso', 'Ensenada'],
    'Ciudad Autónoma de Buenos Aires': ['Ciudad Autónoma de Buenos Aires', 'Palermo', 'Recoleta', 'Belgrano', 'Caballito',
      'Almagro', 'Balvanera', 'San Telmo', 'La Boca', 'Barracas', 'Boedo', 'Flores', 'Floresta', 'Villa Crespo',
      'Villa Urquiza', 'Villa Devoto', 'Mataderos', 'Liniers', 'Villa Lugano', 'Parque Patricios', 'Pompeya', 'Saavedra',
      'Núñez', 'Colegiales', 'Chacarita', 'Villa del Parque', 'Retiro', 'San Nicolás', 'Monserrat', 'Puerto Madero'],
    'Catamarca': ['San Fernando del Valle de Catamarca', 'Valle Viejo', 'Fray Mamerto Esquiú', 'Andalgalá', 'Belén',
      'Tinogasta', 'Santa María', 'Recreo'],
    'Chaco': ['Resistencia', 'Barranqueras', 'Fontana', 'Puerto Vilelas', 'Presidencia Roque Sáenz Peña', 'Villa Ángela',
      'Charata', 'General San Martín', 'Juan José Castelli', 'Quitilipi'],
    'Chubut': ['Rawson', 'Trelew', 'Puerto Madryn', 'Comodoro Rivadavia', 'Esquel', 'Gaiman', 'Sarmiento', 'Rada Tilly'],
    'Córdoba': ['Córdoba', 'Río Cuarto', 'Villa María', 'San Francisco', 'Villa Carlos Paz', 'Alta Gracia', 'Río Tercero',
      'Bell Ville', 'Jesús María', 'La Calera', 'Cosquín', 'Marcos Juárez', 'Villa Dolores', 'Cruz del Eje', 'Arroyito'],
    'Corrientes': ['Corrientes', 'Goya', 'Paso de los Libres', 'Curuzú Cuatiá', 'Mercedes', 'Santo Tomé', 'Esquina',
      'Bella Vista', 'Ituzaingó'],
    'Entre Ríos': ['Paraná', 'Concordia', 'Gualeguaychú', 'Concepción del Uruguay', 'Villaguay', 'Victoria', 'La Paz',
      'Gualeguay', 'Colón', 'Chajarí', 'Federación', 'Crespo'],
    'Formosa': ['Formosa', 'Clorinda', 'Pirané', 'El Colorado', 'Las Lomitas', 'Ingeniero Juárez'],
    'Jujuy': ['San Salvador de Jujuy', 'Palpalá', 'San Pedro de Jujuy', 'Libertador General San Martín', 'Perico',
      'Humahuaca', 'Tilcara', 'La Quiaca'],
    'La Pampa': ['Santa Rosa', 'General Pico', 'Toay', 'Realicó', 'Eduardo Castex', 'General Acha', 'Macachín'],
    'La Rioja': ['La Rioja', 'Chilecito', 'Aimogasta', 'Chamical', 'Chepes', 'Villa Unión'],
    'Mendoza': ['Mendoza', 'Godoy Cruz', 'Guaymallén', 'Las Heras', 'Luján de Cuyo', 'Maipú', 'San Rafael', 'San Martín',
      'Rivadavia', 'Tunuyán', 'Malargüe', 'General Alvear'],
    'Misiones': ['Posadas', 'Oberá', 'Eldorado', 'Puerto Iguazú', 'Garupá', 'Apóstoles', 'Leandro N. Alem',
      'San Vicente', 'Puerto Rico', 'Montecarlo'],
    'Neuquén': ['Neuquén', 'Plottier', 'Centenario', 'Cutral Có', 'Zapala', 'San Martín de los Andes', 'Villa La Angostura',
      'Chos Malal', 'Rincón de los Sauces'],
    'Río Negro': ['Viedma', 'San Carlos de Bariloche', 'General Roca', 'Cipolletti', 'Allen', 'Villa Regina',
      'El Bolsón', 'San Antonio Oeste', 'Choele Choel'],
    'Salta': ['Salta', 'San Ramón de la Nueva Orán', 'Tartagal', 'General Güemes', 'Metán', 'Rosario de Lerma',
      'Cafayate', 'Cerrillos', 'Embarcación'],
    'San Juan': ['San Juan', 'Rawson', 'Rivadavia', 'Chimbas', 'Santa Lucía', 'Pocito', 'Caucete', 'Jáchal'],
    'San Luis': ['San Luis', 'Villa Mercedes', 'Merlo', 'Juana Koslay', 'La Punta', 'Justo Daract', 'Tilisarao'],
    'Santa Cruz': ['Río Gallegos', 'Caleta Olivia', 'El Calafate', 'Pico Truncado', 'Puerto Deseado', 'Las Heras',
      'Puerto San Julián', 'El Chaltén'],
    'Santa Fe': ['Santa Fe', 'Rosario', 'Rafaela', 'Venado Tuerto', 'Reconquista', 'Villa Gobernador Gálvez',
      'Santo Tomé', 'Esperanza', 'San Lorenzo', 'Casilda', 'Granadero Baigorria', 'Funes', 'Cañada de Gómez', 'Firmat'],
    'Santiago del Estero': ['Santiago del Estero', 'La Banda', 'Termas de Río Hondo', 'Añatuya', 'Frías',
      'Fernández', 'Quimilí'],
    'Tierra del Fuego': ['Ushuaia', 'Río Grande', 'Tolhuin'],
    'Tucumán': ['San Miguel de Tucumán', 'Yerba Buena', 'Tafí Viejo', 'Banda del Río Salí', 'Concepción', 'Aguilares',
      'Famaillá', 'Monteros', 'Lules', 'Tafí del Valle']
  };

  function normalizar(texto) {
    return (texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
  }

  document.querySelectorAll('select[data-provincia]').forEach(function (provincia) {
    var form = provincia.form || document;
    var localidad = form.querySelector('input[data-localidad]');
    if (!localidad) { return; }
    var lista = document.getElementById(localidad.getAttribute('list'));

    // Si el formulario vuelve con un valor que no está en la lista (por ejemplo,
    // escrito distinto), se elige la provincia que coincide sin tildes.
    var inicial = provincia.getAttribute('data-valor');
    if (inicial && !provincia.value) {
      Array.prototype.forEach.call(provincia.options, function (op) {
        if (normalizar(op.value) === normalizar(inicial)) { provincia.value = op.value; }
      });
    }

    function actualizar() {
      var opciones = LOCALIDADES[provincia.value] || [];
      if (lista) {
        lista.innerHTML = '';
        opciones.forEach(function (nombre) {
          var op = document.createElement('option');
          op.value = nombre;
          lista.appendChild(op);
        });
      }
      localidad.placeholder = provincia.value ? 'Ej.: ' + (opciones[0] || 'tu localidad') : 'Elegí la provincia primero';
      if (provincia.value === 'Ciudad Autónoma de Buenos Aires' && !localidad.value) {
        localidad.value = 'Ciudad Autónoma de Buenos Aires';
      }
    }
    provincia.addEventListener('change', function () {
      // Una localidad sugerida de otra provincia ya no corresponde.
      var antes = Object.keys(LOCALIDADES).some(function (p) {
        return p !== provincia.value && (LOCALIDADES[p] || []).indexOf(localidad.value) >= 0
          && (LOCALIDADES[provincia.value] || []).indexOf(localidad.value) < 0;
      });
      if (antes) { localidad.value = ''; }
      actualizar();
    });
    actualizar();
  });
})();
