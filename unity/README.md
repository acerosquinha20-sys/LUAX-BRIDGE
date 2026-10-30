# Cliente Unity

Copie `LuaxBridgeClient.cs` para `Assets/Scripts/`. Adicione-o a um GameObject em uma cena de teste, configure `serverUrl` e `token` e chame `Connect()` a partir de um botão de UI ou de um script. Chame `SendTargetTelemetry(...)` somente com dados de objetos da própria cena/simulação.

A implementação usa `ClientWebSocket`; a compatibilidade deve ser verificada para a plataforma de build escolhida. Para Unity Android/IL2CPP, se a API de WebSocket não estiver disponível no perfil escolhido, use uma biblioteca WebSocket compatível com Unity.
