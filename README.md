# Fogata de Resurrección — Forge 1.20.1

Sin librerías ni resource pack: todo (modelos, texturas, sonidos, efectos) va dentro del .jar.

## Compilar
1. Copia el wrapper de Gradle del MDK oficial de Forge 1.20.1 (`gradlew`, `gradlew.bat`, `gradle/`) — o usa el workflow de GitHub incluido.
2. Java 17:  `./gradlew build`  ->  `build/libs/*.jar`  (va en `mods/` del servidor Y del cliente).

## Cómo se usa
- **Cuchara de la Resurrección**: sin ella la fogata no abre. Se gasta al revivir ("Has perdido tu alma por revivir a alguien").
  `/give @s revivalcampfire:resurrection_spoon` · receta: tótem / esmeralda / palo (vertical).
- **Fogata de Resurrección**: `/give @s revivalcampfire:resurrection_campfire` · receta: tótem arriba, arena de almas, fogata al centro.
- Con la cuchara en la mano: click derecho en la fogata -> menú con los baneados -> elegir -> **Revivir**.
- Comandos (OP; en singleplayer siempre):
  - `/revivir <jugador>` / `/revivir <jugador> <x y z>` ritual por comando
  - `/revivir demo` animación de prueba con un jugador ficticio
  - `/revivir testban <nombre>` banea un jugador ficticio para probar el menú

## Probar en singleplayer
`/revivir testban Pepe` -> `/give @s revivalcampfire:resurrection_spoon` + fogata -> click derecho con la cuchara -> Pepe -> Revivir.
O directamente `/revivir demo` para ver la animación.

## Configuración (`config/revivalcampfire-common.toml`)
- `banOnDeath` (false): banea al morir; déjalo en false si ya tienes tu propio sistema de baneo.
- `requiredPermissionLevel` (0): permiso extra además de la cuchara (2 = solo OPs).

## Arte y sonido
Texturas, modelo y sprites de luz son originales y se regeneran con `tools/generate_art.py` y `tools/generate_effects.py`.
`sounds/ritual_main.ogg` (30,6 s) y `sounds/revived.ogg` (3,1 s) son los audios propios del autor del mod; `spoon_use.ogg` lo sintetiza `tools/generate_sounds.py`.
Puedes reemplazar cualquier archivo de `assets/revivalcampfire/` por material propio con licencia.

## Línea de tiempo del ritual (sincronizada con ritual.ogg)
0 s golpe · 0,5-7 s anillo con punto brillante en el cielo · 4,5 s un cubo oscuro cae, impacta (7,5 s) y crece hasta envolverlo todo (9 s) ·
9 s el mundo desaparece: vacío con estrellas, discos turquesa, arcos orbitando · 19-28,5 s estrella dorada que crece ·
28,5 s destello, vuelve el día, se desbanea (sonido revived.ogg) y la figura desciende con un haz de luz · 36,7 s fin.

## Sonidos
`tools/generate_sounds.py` sintetiza los .ogg (originales, sin samples de terceros). Puedes cambiar los tiempos o
reemplazar los archivos en `assets/revivalcampfire/sounds/` por los tuyos.
