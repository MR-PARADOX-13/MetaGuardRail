import express from 'express';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import 'dotenv/config';

// Use global fetch if available (Node >= 18), otherwise fall back to node-fetch
// Node 20 (see Dockerfile) provides global fetch — no extra dependency needed.
const fetchFn = typeof globalThis.fetch === 'function'
  ? globalThis.fetch.bind(globalThis)
  : (await import('node-fetch')).default;

const BASETEN_BASE = 'https://inference.baseten.co/v1';
const PORT = process.env.PORT || 3000;
const BASETEN_API_KEY = process.env.BASETEN_API_KEY;

const app = express();

// --- Middleware ---
app.use(cors());
app.use(express.json({ limit: '2mb' }));

const rateLimiter = rateLimit({
  windowMs: 60_000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
});

// Lightweight auth stub: require either X-App-Check or a Bearer token of
// reasonable length. Replace with real Firebase App Check / Auth verification
// before production use.
const requireAuth = (req, res, next) => {
  const appCheck = req.header('X-App-Check');
  const auth = req.header('Authorization');
  if (!appCheck && (!auth || auth.length < 10)) {
    return res.status(401).json({ error: 'Missing auth' });
  }
  next();
};

// --- Health ---
app.get('/health', (_req, res) => {
  res.json({ ok: true, time: new Date().toISOString() });
});

// --- Shared handler for /v1/moderate and /v1/chat/completions ---
async function handleChatCompletions(req, res) {
  // 1. Validate missing API key — safe message, never leak key
  if (!BASETEN_API_KEY) {
    console.error('[moderate] missing BASETEN_API_KEY configuration');
    return res.status(500).json({ error: 'Server misconfiguration' });
  }

  // 2. Validate body has messages array
  const { messages, model } = req.body ?? {};
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: 'messages must be a non-empty array' });
  }

  // 3. Validate content length budget: sum of content string lengths <= 4000
  let totalLength = 0;
  for (const m of messages) {
    if (typeof m.content === 'string') {
      totalLength += m.content.length;
    } else if (Array.isArray(m.content)) {
      for (const part of m.content) {
        if (typeof part.text === 'string') totalLength += part.text.length;
        else if (typeof part === 'string') totalLength += part.length;
      }
    }
  }
  if (totalLength > 4000) {
    return res.status(413).json({ error: 'Payload too large: messages content exceeds 4000 characters' });
  }

  // 4. Ensure content-type is JSON (express.json already parsed, but verify header)
  // If client sent non-JSON, express.json would have failed; this is a safety check.
  const contentType = req.header('content-type') || '';
  if (contentType && !contentType.includes('application/json')) {
    return res.status(415).json({ error: 'Content-Type must be application/json' });
  }

  // 5. Forward to Baseten with timeout via AbortController (30 s)
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30_000);

  try {
    const basetenUrl = `${BASETEN_BASE}/chat/completions`;

    const basetenBody = {
      messages,
      ...(model ? { model } : {}),
    };

    const basetenRes = await fetchFn(basetenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${BASETEN_API_KEY}`,
      },
      body: JSON.stringify(basetenBody),
      signal: controller.signal,
    });

    // Log only status, never body/headers containing the key
    console.log(`[proxy] Baseten responded with status ${basetenRes.status}`);

    // 6. Handle 429 / 5xx — forward status to client with safe body
    if (basetenRes.status === 429 || basetenRes.status >= 500) {
      const text = await basetenRes.text().catch(() => '');
      // Do NOT include BASETEN_API_KEY in response; return upstream status
      return res.status(basetenRes.status).json({
        error: basetenRes.status === 429 ? 'Rate limited by upstream' : 'Upstream error',
        upstreamStatus: basetenRes.status,
      });
    }

    if (!basetenRes.ok) {
      const text = await basetenRes.text().catch(() => '');
      console.warn(`[proxy] Baseten non-OK ${basetenRes.status}`);
      return res.status(basetenRes.status).json({ error: 'Upstream request failed', upstreamStatus: basetenRes.status });
    }

    const data = await basetenRes.json();

    // 7. Validate choices[0].message.content exists, else 502
    const content = data?.choices?.[0]?.message?.content;
    if (typeof content !== 'string' || content.length === 0) {
      console.error('[proxy] Baseten response missing choices[0].message.content');
      return res.status(502).json({ error: 'Invalid upstream response' });
    }

    // Never return BASETEN_API_KEY — only forward the model content
    return res.json(data);
  } catch (err) {
    if (err.name === 'AbortError') {
      console.error('[proxy] Baseten request timed out after 30s');
      return res.status(504).json({ error: 'Upstream timeout' });
    }
    console.error('[proxy] Baseten fetch error:', err.message);
    return res.status(502).json({ error: 'Bad gateway' });
  } finally {
    clearTimeout(timeout);
  }
}

// Both endpoints share the same handler
app.post('/v1/moderate', rateLimiter, requireAuth, handleChatCompletions);
app.post('/v1/chat/completions', rateLimiter, requireAuth, handleChatCompletions);

// --- Optional proxy: GET /v1/models ---
app.get('/v1/models', rateLimiter, requireAuth, async (_req, res) => {
  if (!BASETEN_API_KEY) {
    console.error('[models] missing BASETEN_API_KEY configuration');
    return res.status(500).json({ error: 'Server misconfiguration' });
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30_000);
  try {
    const basetenRes = await fetchFn(`${BASETEN_BASE}/models`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${BASETEN_API_KEY}`,
      },
      signal: controller.signal,
    });
    console.log(`[models] Baseten responded with status ${basetenRes.status}`);
    if (!basetenRes.ok) {
      return res.status(basetenRes.status).json({ error: 'Upstream request failed', upstreamStatus: basetenRes.status });
    }
    const data = await basetenRes.json();
    return res.json(data);
  } catch (err) {
    if (err.name === 'AbortError') {
      return res.status(504).json({ error: 'Upstream timeout' });
    }
    console.error('[models] fetch error:', err.message);
    return res.status(502).json({ error: 'Bad gateway' });
  } finally {
    clearTimeout(timeout);
  }
});

// --- Start server (only if not imported as module in tests) ---
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`MetaGuardRail backend listening on port ${PORT}`);
  });
}

export default app;
export { app };
