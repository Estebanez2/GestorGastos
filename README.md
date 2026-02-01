# 📱 Gestor de Gastos

Aplicación Android diseñada para la **gestión eficiente de gastos personales mensuales**, permitiendo organizarlos por categorías y analizarlos mediante reportes visuales.  
La aplicación ofrece distintas vistas para facilitar el control financiero y permite **adjuntar imágenes a los gastos**.

---

## 🧾 Descripción general

**Gestor de Gastos** permite registrar, clasificar y visualizar gastos de forma sencilla e intuitiva.  
Está orientada a usuarios que desean tener un control claro de sus finanzas personales a través de una interfaz visual y organizada.

---

## ✨ Funcionalidades

- Registro de gastos con importe, fecha y categoría  
- Asociación de fotografías a los gastos  
- Gestión de categorías personalizadas  
- Análisis visual del gasto mensual  
- Organización automática por meses  

---

## 🧭 Vistas disponibles

La aplicación dispone de **cuatro vistas principales**:

- **Lista**: visualización detallada de todos los gastos registrados  
- **Calendario**: consulta de gastos organizados por día  
- **Gráficos**: representación visual de los gastos  
- **Categorías**: resumen del gasto agrupado por categoría  

---

## 🛠️ Tecnologías utilizadas

- Android Studio  
- Kotlin  
- Arquitectura MVVM  
- Room (persistencia de datos local)  
- Material Design  

---

## 📸 Capturas de pantalla

| Lista | Calendario |
|------|------------|
| ![](screenshots/lista.png) | ![](screenshots/calendario.png) |

| Gráficos | Categorías |
|---------|------------|
| ![](screenshots/grafico.png) | ![](screenshots/categorias.png) |

| Mis Categorias | Crear Gasto |
|---------|------------|
| ![](screenshots/misCategorias.png) | ![](screenshots/crearGasto.png) |

---

## 📦 APK de demostración

Puedes descargar una versión de demostración de la aplicación desde GitHub Releases:

👉 [Descargar APK](../../releases)

> ⚠️ Es posible que Android solicite habilitar la instalación desde orígenes desconocidos.

---

## 🚀 Ejecución del proyecto

### Opción 1: Desde Android Studio

1. Clona el repositorio:
   ```bash
   git clone https://github.com/estebanez2/gestor-gastos.git
2. Abre el proyecto en Android Studio
3. Sincroniza Gradle
4. Ejecuta la aplicación en un emulador o dispositivo físico

---

## 🔐 Consideraciones de seguridad

- La keystore de firma no se incluye en el repositorio  
- No se almacenan claves privadas ni credenciales sensibles  
- El proyecto es seguro para ser publicado como repositorio público  
- La app solo pide permisos para la cámara y la galeria al intentar hacer o subir una foto

---

## 📄 Licencia

Este proyecto está licenciado bajo la Licencia MIT.
Consulta el archivo [LICENSE](LICENSE) para más información.


---

## 👤 Autor

Desarrollado por Alejandro Estébanez Moreno
GitHub: https://github.com/estebanez2
