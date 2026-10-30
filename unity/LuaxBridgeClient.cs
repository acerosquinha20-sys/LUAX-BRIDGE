// C# · LuaxBridgeClient.cs · Unity 2022.3+ · authorized test scene
using System;
using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

public class LuaxBridgeClient : MonoBehaviour
{
    [Serializable] public class TargetTelemetry
    {
        public string id;
        public float x;
        public float y;
        public bool visible;
    }

    [Serializable] public class TelemetryPayload
    {
        public string source = "unity-demo";
        public TargetTelemetry[] targets;
    }

    [Serializable] public class Envelope
    {
        public string type;
        public string token;
        public TelemetryPayload payload;
    }

    public string serverUrl = "ws://127.0.0.1:8787";
    public string token = "change-this-token";

    private ClientWebSocket socket;
    private CancellationTokenSource cancelSource;
    private readonly ConcurrentQueue<string> incoming = new();

    public async void Connect()
    {
        try
        {
            socket = new ClientWebSocket();
            cancelSource = new CancellationTokenSource();
            await socket.ConnectAsync(new Uri(serverUrl), cancelSource.Token);
            var hello = "{\"type\":\"hello\",\"token\":\"" + Escape(token) + "\",\"payload\":{}}";
            await SendRaw(hello);
            _ = ReceiveLoop();
            Debug.Log("LUAX Bridge connected");
        }
        catch (Exception ex) { Debug.LogError("LUAX Bridge: " + ex.Message); }
    }

    public async void SendTargetTelemetry(string targetId, Vector2 normalizedScreenPosition, bool visible)
    {
        if (socket == null || socket.State != WebSocketState.Open) return;
        var json = "{\"type\":\"telemetry\",\"token\":\"" + Escape(token) +
            "\",\"payload\":{\"source\":\"unity-demo\",\"targets\":[{\"id\":\"" +
            Escape(targetId) + "\",\"x\":" + normalizedScreenPosition.x.ToString(System.Globalization.CultureInfo.InvariantCulture) +
            ",\"y\":" + normalizedScreenPosition.y.ToString(System.Globalization.CultureInfo.InvariantCulture) +
            ",\"visible\":" + (visible ? "true" : "false") + "}]}}";
        await SendRaw(json);
    }

    private async Task SendRaw(string json)
    {
        var bytes = Encoding.UTF8.GetBytes(json);
        await socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancelSource.Token);
    }

    private async Task ReceiveLoop()
    {
        var buffer = new byte[8192];
        try
        {
            while (socket != null && socket.State == WebSocketState.Open && !cancelSource.IsCancellationRequested)
            {
                var result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), cancelSource.Token);
                if (result.MessageType == WebSocketMessageType.Close) break;
                incoming.Enqueue(Encoding.UTF8.GetString(buffer, 0, result.Count));
            }
        }
        catch (Exception ex) { Debug.Log("LUAX receive ended: " + ex.Message); }
    }

    private void Update()
    {
        while (incoming.TryDequeue(out var message))
            Debug.Log("LUAX message: " + message);
    }

    private static string Escape(string value) =>
        value.Replace("\\", "\\\\").Replace("\"", "\\\"");

    private async void OnDestroy()
    {
        try
        {
            cancelSource?.Cancel();
            if (socket != null)
            {
                if (socket.State == WebSocketState.Open)
                    await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Unity object destroyed", CancellationToken.None);
                socket.Dispose();
            }
            cancelSource?.Dispose();
        }
        catch { /* shutdown path */ }
    }
}
