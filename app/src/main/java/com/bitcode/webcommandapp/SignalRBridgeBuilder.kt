package com.bitcode.webcommandapp

/**
 * Constructor del bridge HTML de SignalR
 * 
 * Responsabilidades:
 * - Generar HTML para WebView
 * - Configurar carga secuencial de scripts
 * - Inicializar listeners de SignalR
 * - Mantener la lógica JavaScript separada de Kotlin
 */
class SignalRBridgeBuilder {
    
    fun buildSignalRBridgeHtml(): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <script src="/jquery.js"></script>
              <script src="/signalr/js"></script>
              <script src="/signalr/hubs"></script>
            </head>
            <body>
            <script>
            function initializeApp() {
              (function () {
                function callNative(name) {
                  try {
                    if (!window.AndroidDroneSocket || !window.AndroidDroneSocket[name]) return;
                    var args = Array.prototype.slice.call(arguments, 1);
                    window.AndroidDroneSocket[name].apply(window.AndroidDroneSocket, args);
                  } catch (e) {
                    console.error("callNative error: " + e);
                  }
                }

                function mapState(state) {
                  switch (state) {
                    case 0: return "Connecting";
                    case 1: return "Connected";
                    case 2: return "Reconnecting";
                    case 4: return "Disconnected";
                    default: return String(state || "");
                  }
                }

                var hub = null;
                var currentDroneId = "";

                function waitForSignalR(callback, attempts) {
                  attempts = attempts || 0;
                  if (attempts > 50) {
                    var msg = "Timeout esperando SignalR. Estado: jQuery=" + (typeof jQuery) + ", $=" + (typeof $) + ", $.connection=" + (typeof ($ && $.connection));
                    console.error(msg);
                    console.error("Window.$:", window.$);
                    console.error("Window.jQuery:", window.jQuery);
                    console.error("Window.$.connection:", window.$ ? window.$.connection : "N/A");
                    callNative("onLog", msg);
                    callNative("onStateChanged", "Error: Timeout esperando SignalR");
                    return;
                  }
                  
                  if (!window.$$ && window.jQuery) {
                    window.$ = window.jQuery;
                  }
                  
                  if (typeof $ !== 'undefined' && $ && $.connection && $.connection.dronesHub) {
                    console.log("✓ SignalR LISTO! jQuery=" + typeof $ + ", $.connection.dronesHub=" + typeof $.connection.dronesHub);
                    callNative("onLog", "SignalR listo! jQuery=" + typeof $ + ", $.connection.dronesHub=" + typeof $.connection.dronesHub);
                    callback();
                  } else {
                    var jqStatus = typeof $ !== 'undefined' ? "SI (" + typeof $ + ")" : "NO";
                    var connStatus = ($ && $.connection) ? "SI" : "NO";
                    var hubStatus = ($ && $.connection && $.connection.dronesHub) ? "SI" : "NO";
                    var msg = "Esperando SignalR #" + (attempts + 1) + " - jQuery:" + jqStatus + " connection:" + connStatus + " dronesHub:" + hubStatus;
                    console.log(msg);
                    callNative("onLog", msg);
                    setTimeout(function() {
                      waitForSignalR(callback, attempts + 1);
                    }, 100);
                  }
                }

                window.droneSocket = {
                  connect: function (serverUrl, droneId) {
                    currentDroneId = String(droneId || "");
                    console.log("🔄 Iniciando conexión SignalR a: " + serverUrl + " como " + droneId);
                    callNative("onLog", "Iniciando conexión a " + serverUrl + " como " + droneId);
                    
                    waitForSignalR(function() {
                      try {
                        $.connection.hub.url = String(serverUrl || "").replace(/\/+$/, "") + "/signalr";
                        console.log("⚙️ URL del hub configurada: " + $.connection.hub.url);
                        hub = $.connection.dronesHub;
                        if (!hub) {
                          var errMsg = "Hub dronesHub no disponible";
                          console.error("✗ " + errMsg);
                          callNative("onLog", errMsg);
                          callNative("onStateChanged", "Error: " + errMsg);
                          return;
                        }
                        console.log("✓ Hub dronesHub disponible");

                        hub.client.recibirInstruccion = function (droneIdValue, instruccion, parametros, correlationId, timestamp) {
                          console.log("📨 Instrucción recibida: " + instruccion + " para " + droneIdValue);
                          callNative(
                            "onInstruction",
                            String(droneIdValue || ""),
                            String(instruccion || ""),
                            parametros == null ? "" : String(parametros),
                            String(correlationId || ""),
                            String(timestamp || "")
                        );
                      };
                      hub.client.registroConfirmado = function (droneIdValue, connectionId, timestamp) {
                        console.log("✓ Registro confirmado: " + droneIdValue);
                        callNative("onRegistered", String(droneIdValue || ""), String(connectionId || ""), String(timestamp || ""));
                      };
                      hub.client.actualizarEstado = function (droneIdValue, statusJson, timestamp) {
                        console.log("📊 Estado actualizado para: " + droneIdValue);
                        callNative("onStatusUpdated", String(droneIdValue || ""), String(statusJson || ""), String(timestamp || ""));
                      };
                      hub.client.droneConectado = function (droneIdValue, timestamp) {
                        console.log("✓✓✓ DRON CONECTADO: " + droneIdValue);
                        callNative("onDroneConnected", String(droneIdValue || ""), String(timestamp || ""));
                      };
                      hub.client.droneDesconectado = function (droneIdValue, timestamp) {
                        console.log("✗✗✗ DRON DESCONECTADO: " + droneIdValue);
                        callNative("onDroneDisconnected", String(droneIdValue || ""), String(timestamp || ""));
                      };

                        $.connection.hub.stateChanged(function (change) {
                          var state = mapState(change.newState);
                          console.log("🔄 Estado hub cambiado a: " + state);
                          callNative("onStateChanged", state);
                        });
                        $.connection.hub.reconnecting(function () {
                          callNative("onStateChanged", "Reconnecting");
                        });
                        $.connection.hub.disconnected(function () {
                          callNative("onDisconnected");
                        });
                        $.connection.hub.error(function (error) {
                          var message = error && error.message ? error.message : String(error || "Error websocket");
                          console.error("✗ ERROR SignalR: ", error, " Mensaje: " + message);
                          callNative("onLog", "Error SignalR: " + message);
                          callNative("onStateChanged", "Error: " + message);
                        });

                        callNative("onStateChanged", "Connecting");
                        console.log("⚙️ Iniciando conexión del hub con transporte: webSockets, longPolling");
                        $.connection.hub.start({ transport: ["webSockets", "longPolling"] })
                          .done(function () {
                            console.log("✓ Conexión del hub iniciada correctamente");
                            callNative("onStateChanged", "Connected");
                            console.log("📤 Enviando register para: " + currentDroneId);
                            hub.server.register(currentDroneId)
                              .done(function () {
                                console.log("✓ Register confirmado para: " + currentDroneId);
                                callNative("onLog", "Register enviado para " + currentDroneId);
                              })
                              .fail(function (err) {
                                var errMsg = String(err || "");
                                console.error("✗ Error en register: ", err, " Detalle: " + errMsg);
                                callNative("onLog", "Error Register: " + errMsg);
                              });
                          })
                          .fail(function (err) {
                            var message = String(err || "No fue posible conectar");
                            console.error("✗✗ ERROR al iniciar conexión: ", err, " Detalle: " + message);
                            callNative("onLog", "Error conectar: " + message);
                            callNative("onStateChanged", "Error: " + message);
                          });
                      } catch (ex) {
                        var errMsg = "Excepción en websocket: " + String(ex);
                        console.error("✗✗✗ EXCEPCIÓN: ", ex, " - " + errMsg);
                        callNative("onLog", errMsg);
                        callNative("onStateChanged", "Error: " + errMsg);
                      }
                    });
                  },
                  disconnect: function () {
                    try {
                      $.connection.hub.stop();
                    } catch (e) {}
                  },
                  sendStatus: function (payload) {
                    if (!hub) return;
                    hub.server.reportStatus(currentDroneId, String(payload || ""));
                  },
                  sendResponse: function (correlationId, payload) {
                    if (!hub) return;
                    hub.server.informarRespuesta(String(correlationId || ""), String(payload || ""));
                  }
                };

                callNative("onPageReady");
              })();
            }
            initializeApp();
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
