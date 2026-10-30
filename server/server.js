// JavaScript · server.js · Node.js 20+ · LUAX BRIDGE relay
'use strict';

require('dotenv').config();
const WebSocket = require('ws');

const PORT = Number(process.env.PORT || 8787);
const TOKEN = process.env.BRIDGE_TOKEN || 'change-this-token';
const wss = new WebSocket.Server({ port: PORT, maxPayload: 64 * 1024 });

function safeSend(ws, data) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
}

wss.on('connection', (ws) => {
  ws.authenticated = false;
  ws.on('message', (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      safeSend(ws, { type: 'error', payload: { message: 'Invalid JSON' } });
      return;
    }

    if (!ws.authenticated) {
      if (message.type !== 'hello' || message.token !== TOKEN) {
        safeSend(ws, { type: 'error', payload: { message: 'Authentication failed' } });
        ws.close(1008, 'Authentication failed');
        return;
      }
      ws.authenticated = true;
      safeSend(ws, { type: 'status', payload: { connected: true, message: 'LUAX Bridge connected' } });
      return;
    }

    if (!['command', 'telemetry', 'status'].includes(message.type) ||
        typeof message.payload !== 'object' || message.payload === null) {
      safeSend(ws, { type: 'error', payload: { message: 'Unsupported message shape' } });
      return;
    }

    // Relay only to authenticated peers. This is a demo relay, not a game-process reader.
    for (const peer of wss.clients) {
      if (peer !== ws && peer.authenticated && peer.readyState === WebSocket.OPEN) {
        peer.send(JSON.stringify({ type: message.type, payload: message.payload }));
      }
    }
    safeSend(ws, { type: 'ack', payload: { received: message.type } });
  });
  ws.on('error', (err) => console.error('WebSocket client error:', err.message));
});

wss.on('listening', () => console.log(`LUAX Bridge listening on ws://0.0.0.0:${PORT}`));
