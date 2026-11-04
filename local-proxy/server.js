import express from 'express';
import fetch from 'node-fetch';
const app = express();
app.use(express.json({ limit: '12mb' }));
const NVIDIA_API_KEY = process.env.NVIDIA_API_KEY;
const NVIDIA_ENDPOINT = 'https://integrate.api.nvidia.com/v1/chat/completions';
// NVIDIA model per official NIM reference: https://docs.api.nvidia.com/nim/reference/meta-llama-guard-4-12b-infer
const MODEL_ID = 'meta/llama-guard-4-12b';
if (!NVIDIA_API_KEY) console.warn('NVIDIA_API_KEY not set — proxy will return 500 until configured');

app.post('/v1/lg4/visual', async (req, res) => {
  if (!NVIDIA_API_KEY) return res.status(500).json({ error: 'proxy_not_configured' });
  const { image_base64, mime_type, prompt } = req.body || {};
  if (!image_base64 || typeof image_base64 !== 'string') return res.status(400).json({ error: 'missing image' });
  if (image_base64.length > 14_000_000) return res.status(413).json({ error: 'image_too_large' });
  if (mime_type && !['image/jpeg','image/png','image/webp'].includes(mime_type)) return res.status(415).json({ error: 'unsupported_type' });
  try {
    // Official NVIDIA schema: model, messages[{role, content:[{type:text},{type:image_url}]} ], max_tokens, stream
    // Verified against https://docs.api.nvidia.com/nim/reference/meta-llama-guard-4-12b-infer
    const nvidiaBody = {
      model: MODEL_ID,
      messages: [{ role: 'user', content: [
        { type: 'text', text: prompt || 'Assess image safety per Llama Guard 4 S1.' },
        { type: 'image_url', image_url: { url: `data:${mime_type||'image/jpeg'};base64,${image_base64}` } }
      ]}],
      max_tokens: 512,
      stream: false
    };
    const r = await fetch(NVIDIA_ENDPOINT, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${NVIDIA_API_KEY}`, 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(nvidiaBody),
      timeout: 25000
    });
    const text = await r.text();
    if (!r.ok) return res.status(r.status).json({ error: 'nvidia_error', status: r.status, body: text.slice(0, 500) });
    res.setHeader('Content-Type', 'application/json');
    return res.send(text);
  } catch (e) {
    return res.status(502).json({ error: 'proxy_error', message: e.message?.slice(0,200) });
  }
});
app.get('/health', (_, res) => res.json({ ok: true, nvidia_configured: !!NVIDIA_API_KEY, model: MODEL_ID }));
const PORT = process.env.PORT || 8787;
// Bind to 0.0.0.0 for physical device LAN access; firewall to trusted network via `ufw allow from 192.168.x.x to any port 8787`
app.listen(PORT, '0.0.0.0', () => console.log(`LG4 dev proxy on 0.0.0.0:${PORT} — model ${MODEL_ID} — NVIDIA_API_KEY ${NVIDIA_API_KEY ? 'set' : 'MISSING'}`));
