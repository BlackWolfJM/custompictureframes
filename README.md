# Custom Picture Frames

Mod para **Minecraft Java Edition 1.21.1 con Fabric** que convierte imágenes de tu equipo en cuadros decorativos para las paredes de tus mundos.

## Objetivo

Permitir que los jugadores personalicen sus construcciones con fotografías, ilustraciones y diseños propios, ajustando el tamaño y el encuadre desde un editor dentro de Minecraft.

## Funciones

- Selección de imágenes locales PNG y JPEG.
- Vista previa antes de generar el cuadro.
- Ancho y alto independientes de **1 a 16 bloques** por lado.
- Zoom, desplazamiento y recorte para ajustar el encuadre.
- Giro de la imagen 90° a la derecha con cada clic, aplicado también al cuadro generado.
- Opción de conservar la proporción o estirar la imagen.
- Nombre opcional visible en el inventario y al seleccionar el objeto en la barra rápida. Los cuadros colocados no muestran etiquetas flotantes.
- Miniaturas en el inventario y catálogo en la pestaña creativa.
- Colocación sobre paredes y recuperación en supervivencia conservando imagen, tamaño y nombre.
- Guardado de cuadros e imágenes con el mundo.
- Transferencia de imágenes mediante el servidor para compartir los cuadros en multijugador.

## Descargar

[**Descargar Custom Picture Frames 2.0.0 (.jar)**](https://github.com/BlackWolfJM/custompictureframes/raw/refs/heads/main/downloads/custompictureframes-2.0.0.jar)

Para Minecraft Java Edition **1.21.1**, con **Fabric** y **Fabric API**. Incluye el giro de imágenes en el editor.

Coloca el archivo descargado en la carpeta mods de tu instalación.

## Requisitos e instalación

- Minecraft Java Edition **1.21.1**.
- Java **21**.
- Fabric Loader **0.16.14 o posterior**.
- Fabric API compatible con **1.21.1**.

1. Instala Fabric para Minecraft 1.21.1.
2. Coloca Fabric API y `custompictureframes-2.0.0.jar` en la carpeta `mods` de tu instalación.
3. Inicia Minecraft usando el perfil de Fabric.

En multijugador, el servidor y cada cliente deben tener el mod y Fabric API instalados. El JAR se carga desde Minecraft; no se abre con doble clic.

## Cómo usarlo

1. Pulsa **K**, configurable en Controles, o usa el **Editor de cuadros**. Está disponible en la pestaña creativa y se fabrica con ocho palos alrededor de un papel.
2. Pulsa **Seleccionar imagen** y elige tu archivo.
3. Introduce un nombre opcional, ajusta ancho y alto, y configura el encuadre con el zoom y el arrastre. Usa **90°** para girar la imagen hacia la derecha.
4. Pulsa **Generar cuadro** para recibir el objeto. Si el inventario está lleno, aparecerá a los pies del jugador.
5. Usa el objeto sobre una pared sólida con espacio libre del tamaño elegido.

También puedes obtener el editor con este comando, si tienes permisos:

```mcfunction
/give @s custompictureframes:editor
```

## Guardado y compatibilidad

Las imágenes se almacenan en la carpeta `custompictureframes` del mundo. Inclúyela al hacer copias de seguridad o trasladar el mundo a otro servidor.

Los archivos locales admiten hasta 64 MiB y 100 megapíxeles y se reducen antes de procesarlos. Las texturas de pared tienen un máximo de 2048 píxeles por lado. WEBP requiere un lector ImageIO compatible; PNG y JPEG funcionan directamente.

Esta versión está diseñada para Fabric 1.21.1 y no convierte automáticamente cuadros del antiguo prototipo para 1.20.1. La validación con dos clientes remotos simultáneos sigue pendiente.

## Compilar desde el código fuente

Necesitas JDK 21. Desde la carpeta del proyecto, utiliza el Gradle Wrapper incluido.

Windows / PowerShell:

```powershell
.\gradlew.bat clean build
```

Linux / macOS:

```bash
./gradlew clean build
```

El JAR instalable se genera en **`build/libs/custompictureframes-2.0.0.jar`**. El archivo `-sources.jar` contiene el código fuente y no es el mod instalable.

Para iniciar el cliente de desarrollo y ejecutar las pruebas de integración en Windows:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runGameTest -PgameTests
```

`build` ejecuta las pruebas unitarias. GameTest utiliza un mundo separado en `build/gametest`; sus clases no se incluyen en el JAR distribuible.

## Licencia

El proyecto se distribuye bajo la [licencia MIT](LICENSE).
