# QA MVP

## Build
- [ ] `gradlew.bat assembleDebug` exitoso
- [ ] APK generado en `app/build/outputs/apk/debug/app-debug.apk`

## Dispositivo
- [ ] App instalada en tablet
- [ ] App abre sin crash
- [ ] WebView carga `https://www.google.com`

## Navegacion local
- [ ] Campo URL navega correctamente
- [ ] Boton atras funciona dentro del WebView

## API remota
- [ ] Se visualiza IP y puerto en pantalla
- [ ] `/health` responde
- [ ] `/navigate` cambia de sitio en la tablet
- [ ] `/execute` ejecuta JS y retorna resultado

## Seguridad minima
- [ ] Sin API key correcta retorna 401
