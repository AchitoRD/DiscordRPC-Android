# DiscordRPC Android

Rich Presence para Android. Muestra tu actividad en Discord desde tu telefono.

## Features

- Deteccion automatica de apps en segundo plano
- UI Glassmorphism translucida
- Conexion directa a Discord Gateway
- Anti-flood y proteccion de bateria
- Intervalo configurable (5s - 120s)
- Foreground service con notificacion

## Como usar

1. Descarga el APK desde [Releases](https://github.com/AchitoRD/DiscordRPC-Android/releases)
2. Instala en tu Android (API 26+)
3. Otorga permisos de **Usage Access**
4. Pega tu **Discord Token** (obtenido desde F12 > Network > Authorization header)
5. Presiona **INICIAR**

## Permisos necesarios

- `PACKAGE_USAGE_STATS` - Detectar apps en uso
- `FOREGROUND_SERVICE` - Mantener el servicio activo
- `INTERNET` - Conexion a Discord Gateway
- `POST_NOTIFICATIONS` - Notificacion del servicio

## Build

```bash
./gradlew assembleDebug
```

## Stack

- Kotlin
- OkHttp (WebSocket)
- Android UsageStatsManager
- Material Components

## License

MIT
