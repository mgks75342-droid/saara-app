/*
 * Sara mic module (extracted from Saara-tools-final.apk, assets/index.html).
 * Only the microphone / live-voice part is included.
 * Usage:
 *   SaaraMic.start({ apiKey, model, voice, systemPrompt, callbacks... })
 *   SaaraMic.stop()
 */
(function (global) {
  "use strict";

  const DEFAULT_MODEL = "gemini-3.1-flash-live-preview";
  const WS_URL =
    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=";

  let ws = null, micCtx = null, outCtx = null, stream = null, micNode = null, proc = null;
  let analyser = null, wkOut = null, wkBuf = 0, lastAudioAt = 0, nextTime = 0;
  let sources = [], live = false, wantLive = false, wkUrl = null;
  let opts = {};
  let interruptOn = false;

  const noop = () => {};
  const cb = (name) => (opts[name] || noop);

  // ---------- helpers ----------
  function b64FromPcm(int16) {
    const bytes = new Uint8Array(int16.buffer);
    let s = "";
    for (let i = 0; i < bytes.length; i += 0x8000)
      s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return btoa(s);
  }

  // ---------- AudioWorklet code (player + mic) ----------
  const WK_SRC = `
class SaaraPlayer extends AudioWorkletProcessor {
  constructor() {
    super(); this.q = []; this.cur = null; this.pos = 0; this.buf = 0; this.run = false; this.idle = 0; this.tick = 0;
    this.port.onmessage = e => {
      const d = e.data;
      if (d === "clear") { this.q = []; this.cur = null; this.pos = 0; this.buf = 0; this.run = false; this.idle = 0; }
      else if (d === "flush") { this.run = true; }
      else { this.q.push(d); this.buf += d.length; this.idle = 0; }
    };
  }
  process(inputs, outputs) {
    const out = outputs[0][0], n = out.length;
    if (!this.run) {
      if (this.buf >= 3600) this.run = true;
      else if (this.buf > 0) { this.idle += n; if (this.idle > 6000) this.run = true; }
    }
    if (this.run) {
      for (let i = 0; i < n; i++) {
        if (!this.cur || this.pos >= this.cur.length) { this.cur = this.q.length ? this.q.shift() : null; this.pos = 0; }
        if (this.cur) { out[i] = this.cur[this.pos++]; this.buf--; }
        else { out[i] = 0; this.run = false; this.idle = 0; }
      }
    }
    this.tick += n;
    if (this.tick >= 2400) { this.tick = 0; this.port.postMessage(this.buf); }
    return true;
  }
}
registerProcessor("saara-player", SaaraPlayer);

class SaaraMic extends AudioWorkletProcessor {
  constructor() { super(); this.b = new Float32Array(1600); this.i = 0; }
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (!ch) return true;
    for (let k = 0; k < ch.length; k++) {
      this.b[this.i++] = ch[k];
      if (this.i >= 1600) {
        const o = new Int16Array(1600);
        for (let j = 0; j < 1600; j++) { const v = Math.max(-1, Math.min(1, this.b[j])); o[j] = v * 32767; }
        this.port.postMessage(o.buffer, [o.buffer]); this.i = 0;
      }
    }
    return true;
  }
}
registerProcessor("saara-mic", SaaraMic);
`;

  function workletUrl() {
    return wkUrl || (wkUrl = URL.createObjectURL(new Blob([WK_SRC], { type: "application/javascript" })));
  }

  // ---------- speaker output ----------
  async function initAudio() {
    outCtx = new AudioContext({ sampleRate: 24000 });
    try { outCtx.resume(); } catch (e) {}
    analyser = outCtx.createAnalyser();
    analyser.fftSize = 512;
    analyser.connect(outCtx.destination);
    wkOut = null; wkBuf = 0;
    try {
      if (!outCtx.audioWorklet) throw new Error("no worklet");
      await outCtx.audioWorklet.addModule(workletUrl());
      wkOut = new AudioWorkletNode(outCtx, "saara-player", { numberOfInputs: 0, outputChannelCount: [1] });
      wkOut.connect(analyser);
      wkOut.port.onmessage = e => {
        const was = wkBuf; wkBuf = e.data;
        if (was > 0 && wkBuf === 0 && live) cb("onStatus")("Sun rahi hoon...");
      };
    } catch (e) {
      wkOut = null;
      cb("onLog")("Smooth audio engine nahi chala, simple mode.");
    }
  }

  function playPcm(b64) {
    const bin = atob(b64), n = bin.length >> 1, f32 = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      let v = (bin.charCodeAt(2 * i + 1) << 8) | bin.charCodeAt(2 * i);
      if (v & 0x8000) v -= 0x10000;
      f32[i] = v / 32768;
    }
    lastAudioAt = Date.now();
    if (wkOut) {
      wkOut.port.postMessage(f32, [f32.buffer]);
      wkBuf += n;
    } else {
      const buf = outCtx.createBuffer(1, n, 24000);
      buf.copyToChannel(f32, 0);
      const src = outCtx.createBufferSource();
      src.buffer = buf;
      src.connect(analyser || outCtx.destination);
      const now = outCtx.currentTime;
      if (nextTime < now + 0.02) nextTime = now + 0.15;
      src.start(nextTime);
      nextTime += buf.duration;
      sources.push(src);
      src.onended = () => {
        sources = sources.filter(x => x !== src);
        if (!sources.length && live) cb("onStatus")("Sun rahi hoon...");
      };
    }
    cb("onStatus")("Saara bol rahi hai...");
  }

  function stopPlayback() {
    if (wkOut) { wkOut.port.postMessage("clear"); wkBuf = 0; }
    sources.forEach(x => { try { x.stop(); } catch (e) {} });
    sources = [];
    nextTime = 0;
  }

  // ---------- microphone capture ----------
  function sendMic(i16) {
    if (!ws || ws.readyState !== 1) return;
    // Echo guard: mic band rakho jab Saara bol rahi ho (interrupt off ho)
    if (!interruptOn && (wkBuf > 0 || Date.now() - lastAudioAt < 600)) return;
    ws.send(JSON.stringify({
      realtimeInput: { audio: { data: b64FromPcm(i16), mimeType: "audio/pcm;rate=16000" } }
    }));
  }

  async function beginMic() {
    micCtx = new AudioContext({ sampleRate: 16000 });
    try { micCtx.resume(); } catch (e) {}
    const src = micCtx.createMediaStreamSource(stream);
    try {
      await micCtx.audioWorklet.addModule(workletUrl());
      micNode = new AudioWorkletNode(micCtx, "saara-mic");
      micNode.port.onmessage = e => sendMic(new Int16Array(e.data));
      const g = micCtx.createGain();
      g.gain.value = 0;
      src.connect(micNode);
      micNode.connect(g);
      g.connect(micCtx.destination);
    } catch (e) {
      // Fallback for old WebViews without AudioWorklet
      proc = micCtx.createScriptProcessor(4096, 1, 1);
      proc.onaudioprocess = ev => {
        const f = ev.inputBuffer.getChannelData(0), i16 = new Int16Array(f.length);
        for (let i = 0; i < f.length; i++) i16[i] = Math.max(-1, Math.min(1, f[i])) * 32767;
        sendMic(i16);
      };
      src.connect(proc);
      proc.connect(micCtx.destination);
    }
  }

  // ---------- Gemini Live session ----------
  async function start(options) {
    opts = options || {};
    interruptOn = !!opts.allowInterrupt;
    wantLive = true;

    const key = (opts.apiKey || "").trim();
    if (!key) { cb("onError")("API key chahiye."); wantLive = false; return; }

    let model = (opts.model || DEFAULT_MODEL).trim();
    if (!model.startsWith("models/")) model = "models/" + model;

    cb("onStatus")("Connecting...");
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true }
      });
    } catch (e) {
      cb("onStatus")("Mic permission chahiye.");
      wantLive = false;
      return;
    }

    await initAudio();

    ws = new WebSocket(WS_URL + encodeURIComponent(key));
    ws.binaryType = "arraybuffer";

    ws.onopen = () => {
      ws.send(JSON.stringify({
        setup: {
          model,
          generationConfig: {
            responseModalities: ["AUDIO"],
            speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: opts.voice || "Kore" } } }
          },
          systemInstruction: { parts: [{ text: opts.systemPrompt || "Tum Saara ho, ek friendly voice assistant." }] },
          tools: opts.tools || [],
          realtimeInputConfig: { automaticActivityDetection: { silenceDurationMs: 10000, prefixPaddingMs: 400 } },
          inputAudioTranscription: {},
          outputAudioTranscription: {}
        }
      }));
    };

    ws.onmessage = ev => {
      const raw = typeof ev.data === "string" ? ev.data : new TextDecoder().decode(ev.data);
      let m;
      try { m = JSON.parse(raw); } catch (e) { return; }

      if (m.setupComplete) {
        live = true;
        beginMic();
        cb("onStatus")("Sun rahi hoon... bolo.");
        cb("onConnected")();
      }

      if (m.toolCall && m.toolCall.functionCalls) {
        Promise.all(m.toolCall.functionCalls.map(async fc => {
          let r;
          try { r = await cb("onToolCall")(fc.name, fc.args); }
          catch (e) { r = "error: " + e.message; }
          return { id: fc.id, name: fc.name, response: { result: String(r) } };
        })).then(responses => {
          if (ws && ws.readyState === 1)
            ws.send(JSON.stringify({ toolResponse: { functionResponses: responses } }));
        });
      }

      const sc = m.serverContent;
      if (sc) {
        if (sc.interrupted) { stopPlayback(); cb("onInterrupted")(); }
        if (sc.inputTranscription && sc.inputTranscription.text) cb("onUserText")(sc.inputTranscription.text);
        if (sc.outputTranscription && sc.outputTranscription.text) cb("onBotText")(sc.outputTranscription.text);
        if (sc.turnComplete) {
          if (wkOut) wkOut.port.postMessage("flush");
          cb("onTurnComplete")();
        }
        (sc.modelTurn && sc.modelTurn.parts || []).forEach(p => {
          if (p.inlineData && p.inlineData.data) playPcm(p.inlineData.data);
        });
      }
    };

    ws.onerror = () => cb("onError")("Connection error");

    ws.onclose = e => {
      const wasLive = live;
      stop(true);
      if (wasLive && e.code !== 1000) cb("onError")("Band: " + (e.reason || e.code));
      cb("onClosed")(e.code);
    };
  }

  function stop(keepWant) {
    if (!keepWant) wantLive = false;
    live = false;
    try { ws && ws.close(1000); } catch (e) {}
    try { proc && proc.disconnect(); } catch (e) {}
    try { micCtx && micCtx.close(); } catch (e) {}
    try { outCtx && outCtx.close(); } catch (e) {}
    if (stream) stream.getTracks().forEach(t => t.stop());
    stopPlayback();
    ws = null; micCtx = null; outCtx = null; stream = null;
    micNode = null; proc = null; analyser = null; wkOut = null; wkBuf = 0;
    cb("onStatus")("Tap to start...");
  }

  function isLive() { return live; }

  global.SaaraMic = { start, stop, isLive, stopPlayback };
})(window);
