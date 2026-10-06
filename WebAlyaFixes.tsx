/**
 * Alya Assistant — Complete Web-Side Production Audio, Bridge, and Authentication Upgrades
 * Developed by: Rohim Mandal
 * Studio: SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio (SBSSM35GZS)
 * Short Name: SBSSM35GZS
 * 
 * This file contains the complete React + Web Audio API + Firebase Phone Auth 
 * implementation required for the web platform, matching all specified problem fixes.
 */

import React, { useEffect, useRef, useState } from 'react';
import { initializeApp, getApp, getApps } from 'firebase/app';
import { 
  getAuth, 
  signInWithPhoneNumber, 
  RecaptchaVerifier, 
  PhoneAuthProvider,
  signInWithCredential
} from 'firebase/auth';

// ============================================================================
// SECTION 1: HIGH-FIDELITY WEB AUDIO API PLAYBACK ENGINE (24kHz PCM)
// ============================================================================

/**
 * Decodes 16-bit signed Little-Endian PCM audio data to normalized Float32 values (-1.0 to 1.0)
 * securely preventing byte offset shifts and clicks.
 */
export function decodePcm16ToFloat32(arrayBuffer: ArrayBuffer): Float32Array {
  const view = new DataView(arrayBuffer);
  const length = Math.floor(arrayBuffer.byteLength / 2); // 16-bit = 2 bytes per sample
  const float32 = new Float32Array(length);

  for (let i = 0; i < length; i++) {
    const pcm16 = view.getInt16(i * 2, true); // true = Little-Endian
    float32[i] = pcm16 / 32768; // Normalize to -1.0 to 1.0 range
  }
  return float32;
}

/**
 * Global single persistent AudioContext unlocking helper.
 * Essential to bypass mobile browser and Android WebView autoplay constraints.
 */
let globalAudioCtx: AudioContext | null = null;

export function unlockAudioContext(): Promise<AudioContext> {
  return new Promise((resolve, reject) => {
    if (!globalAudioCtx) {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      globalAudioCtx = new AudioContextClass({
        sampleRate: 24000, // Explicitly locked at 24kHz matching server samples
        latencyHint: 'interactive'
      });
    }
    
    if (globalAudioCtx.state === 'suspended') {
      globalAudioCtx.resume()
        .then(() => {
          console.log("[AlyaAudio] AudioContext successfully resumed/unlocked.");
          resolve(globalAudioCtx!);
        })
        .catch((err) => {
          console.warn("[AlyaAudio] AudioContext resume failed:", err);
          reject(err);
        });
    } else {
      resolve(globalAudioCtx);
    }
  });
}

/**
 * Single-Instance persistent AudioContext streaming queue manager.
 * Uses an inline high-priority AudioWorklet for zero-noise continuous playback.
 */
export class GeminiWebAudioPlayer {
  private audioCtx: AudioContext | null = null;
  private workletNode: AudioWorkletNode | null = null;
  private sampleRate: number = 24000;
  private leftoverBytes: Uint8Array | null = null;
  private isWorkletReady: boolean = false;
  private fallbackBuffer: Float32Array[] = [];
  private startTime: number = 0;

  constructor() {
    this.audioCtx = null;
  }

  private async initContext() {
    if (this.audioCtx) {
      if (this.audioCtx.state === 'suspended') {
        await this.audioCtx.resume();
      }
      return;
    }

    try {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioContextClass({
        sampleRate: this.sampleRate,
        latencyHint: 'interactive'
      });

      // Inline dynamic high-priority Audio Worklet to prevent thread stutter
      const workletCode = `
        class AlyaAudioPlaybackProcessor extends AudioWorkletProcessor {
          constructor() {
            super();
            this.buffer = new Float32Array(24000 * 4); // 4-second ring buffer
            this.writePtr = 0;
            this.readPtr = 0;
            this.size = 0;
            this.port.onmessage = (e) => {
              const floatData = e.data;
              this.write(floatData);
            };
          }
          write(data) {
            for (let i = 0; i < data.length; i++) {
              this.buffer[this.writePtr] = data[i];
              this.writePtr = (this.writePtr + 1) % this.buffer.length;
              if (this.size < this.buffer.length) {
                this.size++;
              } else {
                this.readPtr = (this.readPtr + 1) % this.buffer.length; // Drop oldest
              }
            }
          }
          process(inputs, outputs, parameters) {
            const output = outputs[0];
            const channel = output[0];
            if (!channel) return true;
            
            const count = channel.length;
            if (this.size < count) {
              channel.fill(0); // Underflow: fill with silence
              return true;
            }
            
            for (let i = 0; i < count; i++) {
              channel[i] = this.buffer[this.readPtr];
              this.readPtr = (this.readPtr + 1) % this.buffer.length;
            }
            this.size -= count;
            
            // Mirror mono channel to other channels
            for (let c = 1; c < output.length; c++) {
              if (output[c]) {
                output[c].set(channel);
              }
            }
            return true;
          }
        }
        registerProcessor('alya-playback-processor', AlyaAudioPlaybackProcessor);
      `;

      const workletBlob = new Blob([workletCode], { type: 'application/javascript' });
      const workletUrl = URL.createObjectURL(workletBlob);
      await this.audioCtx.audioWorklet.addModule(workletUrl);
      
      this.workletNode = new AudioWorkletNode(this.audioCtx, 'alya-playback-processor');
      this.workletNode.connect(this.audioCtx.destination);
      this.isWorkletReady = true;

      // Deliver queued buffer
      if (this.fallbackBuffer.length > 0) {
        for (const buf of this.fallbackBuffer) {
          this.workletNode.port.postMessage(buf);
        }
        this.fallbackBuffer = [];
      }
    } catch (err) {
      console.error("[AlyaAudio] Dynamic AudioWorklet initialization failed, using scheduling:", err);
      this.isWorkletReady = false;
    }
  }

  /**
   * Feed a base64-encoded 24kHz Mono 16-bit PCM raw audio chunk into the streaming Web Audio queue
   */
  public playRawChunk(base64Chunk: string) {
    this.initContext().then(() => {
      try {
        const binaryString = atob(base64Chunk);
        let rawBytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
          rawBytes[i] = binaryString.charCodeAt(i);
        }

        // Align buffers and resolve chunk boundary offset issues
        if (this.leftoverBytes && this.leftoverBytes.length > 0) {
          const merged = new Uint8Array(this.leftoverBytes.length + rawBytes.length);
          merged.set(this.leftoverBytes, 0);
          merged.set(rawBytes, this.leftoverBytes.length);
          rawBytes = merged;
          this.leftoverBytes = null;
        }

        const alignedLength = Math.floor(rawBytes.length / 2) * 2;
        if (rawBytes.length % 2 !== 0) {
          this.leftoverBytes = rawBytes.slice(alignedLength);
        }

        if (alignedLength === 0) return;

        const alignedBytes = rawBytes.slice(0, alignedLength);
        const floatData = decodePcm16ToFloat32(alignedBytes.buffer);

        if (this.isWorkletReady && this.workletNode) {
          this.workletNode.port.postMessage(floatData);
        } else {
          // Playback buffer scheduled fallback
          this.playScheduledFallback(floatData);
        }
      } catch (e) {
        console.error("[AlyaAudio] Error decoding streaming PCM chunk:", e);
      }
    });
  }

  private playScheduledFallback(floatData: Float32Array) {
    if (!this.audioCtx) return;
    try {
      const audioBuffer = this.audioCtx.createBuffer(1, floatData.length, this.sampleRate);
      audioBuffer.getChannelData(0).set(floatData);

      const sourceNode = this.audioCtx.createBufferSource();
      sourceNode.buffer = audioBuffer;
      sourceNode.connect(this.audioCtx.destination);

      const currentTime = this.audioCtx.currentTime;
      if (this.startTime < currentTime) {
        this.startTime = currentTime + 0.05; // Jitter guard
      }

      sourceNode.start(this.startTime);
      this.startTime += audioBuffer.duration;
    } catch (err) {
      console.error("[AlyaAudio] Buffer-scheduling execution failed:", err);
    }
  }

  public clearQueue() {
    if (this.workletNode) {
      this.workletNode.disconnect();
      this.workletNode = null;
    }
    if (this.audioCtx) {
      this.audioCtx.close().catch(() => {});
      this.audioCtx = null;
    }
    this.isWorkletReady = false;
    this.fallbackBuffer = [];
    this.leftoverBytes = null;
    this.startTime = 0;
  }
}

/**
 * Text-To-Speech decoder and queuing system for chat response cards.
 * Decodes full Base64 PCM payloads before invoking playback, preventing clipping.
 * Catches play rejections and gracefully retries.
 */
export class GeminiWebTtsPlayer {
  private activeSource: AudioBufferSourceNode | null = null;

  public async speak(base64Payload: string, sampleRate: number = 24000) {
    this.stop();

    try {
      const audioCtx = await unlockAudioContext();
      const binaryString = atob(base64Payload);
      const arrayBuffer = new ArrayBuffer(binaryString.length);
      const bytes = new Uint8Array(arrayBuffer);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      const floatData = decodePcm16ToFloat32(arrayBuffer);
      const audioBuffer = audioCtx.createBuffer(1, floatData.length, sampleRate);
      audioBuffer.getChannelData(0).set(floatData);

      this.activeSource = audioCtx.createBufferSource();
      this.activeSource.buffer = audioBuffer;
      this.activeSource.connect(audioCtx.destination);

      try {
        this.activeSource.start(0);
      } catch (playErr) {
        console.warn("[AlyaTTS] Autoplay block detected. Triggering explicit unlock and retrying...");
        const unlockedCtx = await unlockAudioContext();
        this.activeSource = unlockedCtx.createBufferSource();
        this.activeSource.buffer = audioBuffer;
        this.activeSource.connect(unlockedCtx.destination);
        this.activeSource.start(0);
      }
    } catch (e) {
      console.error("[AlyaTTS] Audio playback decoding failed:", e);
    }
  }

  public stop() {
    if (this.activeSource) {
      try {
        this.activeSource.stop();
      } catch (e) {}
      this.activeSource = null;
    }
  }
}

// ============================================================================
// SECTION 2: FIREBASE PHONE AUTH SERVICE WITH RECAPTCHA LIFECYCLE
// ============================================================================

export interface PhoneAuthResponse {
  success: boolean;
  verificationId?: string;
  error?: string;
}

export class FirebasePhoneAuthService {
  private auth = getAuth();
  private recaptchaVerifier: RecaptchaVerifier | null = null;

  /**
   * Safely initialises the reCAPTCHA verifier in invisible mode attached to a DOM node
   */
  public initRecaptcha(containerId: string, onVerify: () => void): RecaptchaVerifier {
    this.cleanupRecaptcha();

    const container = document.getElementById(containerId);
    if (!container) {
      const hiddenNode = document.createElement('div');
      hiddenNode.id = containerId;
      hiddenNode.style.display = 'none';
      document.body.appendChild(hiddenNode);
    }

    this.recaptchaVerifier = new RecaptchaVerifier(this.auth, containerId, {
      size: 'invisible',
      callback: () => {
        onVerify();
      },
      'expired-callback': () => {
        console.warn("reCAPTCHA expired, resetting...");
        this.cleanupRecaptcha();
      }
    });

    return this.recaptchaVerifier;
  }

  /**
   * Resets and clears the reCAPTCHA widget to avoid "argument-error" on duplicates
   */
  public cleanupRecaptcha() {
    if (this.recaptchaVerifier) {
      try {
        this.recaptchaVerifier.clear();
      } catch (e) {
        console.warn("Error clearing captcha widget:", e);
      }
      this.recaptchaVerifier = null;
    }
  }

  /**
   * Triggers Phone OTP sending flow with robust error translation
   */
  public async sendOtp(phoneNumber: string, verifier: RecaptchaVerifier): Promise<PhoneAuthResponse> {
    const e164Regex = /^\+[1-9]\d{1,14}$/;
    if (!e164Regex.test(phoneNumber.trim())) {
      return { 
        success: false, 
        error: "Invalid phone number. Ensure it is in E.164 format (e.g. +919876543210 with no spaces)." 
      };
    }

    try {
      const confirmationResult = await signInWithPhoneNumber(this.auth, phoneNumber, verifier);
      return {
        success: true,
        verificationId: confirmationResult.verificationId
      };
    } catch (e: any) {
      console.error("[AlyaAuth] Error sending phone authentication:", e);
      let localizedMsg = e.message || "Failed to dispatch verification SMS code.";
      
      if (e.code === 'auth/operation-not-allowed') {
        localizedMsg = "Phone Authentication is disabled in your Firebase console. Please enable Phone Sign-In provider under Auth > Sign-in method.";
      } else if (e.code === 'auth/quota-exceeded') {
        localizedMsg = "SMS quota exceeded for this project. Please configure verification tests in the console.";
      } else if (e.code === 'auth/captcha-check-failed') {
        localizedMsg = "reCAPTCHA check failed. Resetting authentication verification widget, please try again.";
        this.cleanupRecaptcha();
      }

      return {
        success: false,
        error: localizedMsg
      };
    }
  }
}

/**
 * Custom hook for React reCAPTCHA lifecycle management.
 * Resolves auth/operation-not-allowed and auth/argument-error dynamically.
 */
export function usePhoneAuthWithRecaptcha(containerId: string) {
  const [verificationId, setVerificationId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const verifierRef = useRef<RecaptchaVerifier | null>(null);
  const auth = getAuth();

  useEffect(() => {
    return () => {
      if (verifierRef.current) {
        try {
          verifierRef.current.clear();
          console.log("[AlyaAuth] Cleaned up reCAPTCHA verifier instance on unmount.");
        } catch (e) {
          console.warn("[AlyaAuth] Error during unmount cleanup of reCAPTCHA:", e);
        }
        verifierRef.current = null;
      }
    };
  }, [containerId]);

  const sendOtpCode = async (phoneNumber: string) => {
    setError(null);
    setLoading(true);

    const e164Regex = /^\+[1-9]\d{1,14}$/;
    if (!e164Regex.test(phoneNumber.trim())) {
      setError("Phone number must be in international E.164 format (e.g. +91XXXXXXXXXX with no spaces).");
      setLoading(false);
      return;
    }

    try {
      if (!verifierRef.current) {
        let container = document.getElementById(containerId);
        if (!container) {
          container = document.createElement('div');
          container.id = containerId;
          container.style.display = 'none';
          document.body.appendChild(container);
        }

        verifierRef.current = new RecaptchaVerifier(auth, containerId, {
          size: 'invisible',
          callback: () => {
            console.log("[AlyaAuth] reCAPTCHA verifier successfully resolved.");
          },
          'expired-callback': () => {
            console.warn("[AlyaAuth] reCAPTCHA token expired. Resetting verifier.");
            if (verifierRef.current) {
              verifierRef.current.clear();
              verifierRef.current = null;
            }
          }
        });
      }

      const confirmationResult = await signInWithPhoneNumber(auth, phoneNumber.trim(), verifierRef.current);
      setVerificationId(confirmationResult.verificationId);
      setLoading(false);
      console.log("[AlyaAuth] OTP Code sent successfully. Verification ID:", confirmationResult.verificationId);
    } catch (e: any) {
      setLoading(false);
      console.error("[AlyaAuth] Phone Auth Exception:", e);

      if (e.code === 'auth/argument-error' || e.message?.includes('reCAPTCHA') || e.message?.includes('already rendered')) {
        console.warn("[AlyaAuth] Resetting verifier instance due to duplicate rendering argument error.");
        if (verifierRef.current) {
          verifierRef.current.clear();
          verifierRef.current = null;
        }
      }

      const friendlyMessage = 
        e.code === 'auth/operation-not-allowed' ? 
          "Phone authentication is currently disabled for this project in the Firebase Console. Please enable the 'Phone' sign-in provider under Authentication > Sign-in method in your Firebase Console to authorize this operation." :
        e.code === 'auth/argument-error' ? 
          "An argument error occurred during captcha setup. The verifier has been reset. Please try sending the code again." :
        e.code === 'auth/quota-exceeded' ? 
          "SMS quota has been exceeded for this project. Please configure test phone numbers in the Firebase Console." :
        e.message || "An unexpected authentication error occurred. Please try again.";

      setError(friendlyMessage);
    }
  };

  return {
    verificationId,
    error,
    loading,
    sendOtpCode,
    resetVerifier: () => {
      if (verifierRef.current) {
        verifierRef.current.clear();
        verifierRef.current = null;
      }
      setVerificationId(null);
      setError(null);
    }
  };
}

// ============================================================================
// SECTION 3: DIRECT FULL DEVICE CONTROL (NATIVE ANDROID COMPANION BRIDGE)
// ============================================================================

export const AlyaAndroidBridge = {
  getBridge() {
    return (window as any).AndroidBridge || (window as any).AlyaNative || null;
  },

  isAvailable(): boolean {
    return this.getBridge() !== null;
  },

  async isAccessibilityServiceEnabled(): Promise<boolean> {
    const bridge = this.getBridge();
    if (bridge && typeof bridge.isAccessibilityServiceEnabled === 'function') {
      return Boolean(await bridge.isAccessibilityServiceEnabled());
    }
    return false;
  },

  requestAccessibilitySettings() {
    const bridge = this.getBridge();
    if (bridge && typeof bridge.requestAccessibilitySettings === 'function') {
      bridge.requestAccessibilitySettings();
    } else if (bridge && typeof bridge.openAccessibilitySettings === 'function') {
      bridge.openAccessibilitySettings();
    } else {
      console.warn("[AlyaBridge] Accessibility settings request triggered, but companion bridge missing.");
      alert("Alya Accessibility Service is required to perform gestures. Please enable Alya in your Android Accessibility Settings.");
    }
  },

  async executeDeviceAction(actionName: string, params: any): Promise<{ success: boolean; message: string }> {
    console.log(`[AlyaBridge] Routing tool execution payload to Native Android: ${actionName}`, params);
    const bridge = this.getBridge();
    if (!bridge) {
      return { 
        success: false, 
        message: `Device bridge not present. Simulated execution of ${actionName} with ${JSON.stringify(params)}.` 
      };
    }

    try {
      switch (actionName) {
        case 'openApp':
          if (typeof bridge.openApp === 'function') {
            const success = await bridge.openApp(params.appName);
            return { success, message: success ? `App '${params.appName}' opened successfully.` : `Failed to open app '${params.appName}'.` };
          }
          break;

        case 'adjustVolume':
          if (typeof bridge.adjustVolume === 'function') {
            const level = Number(params.level);
            const streamType = params.streamType || "music";
            const success = await bridge.adjustVolume(level, streamType);
            return { success, message: success ? `Volume set to ${level}% on stream ${streamType}.` : `Failed to adjust volume.` };
          }
          break;

        case 'toggleSystemSetting':
          if (typeof bridge.toggleSystemSetting === 'function') {
            const success = await bridge.toggleSystemSetting(params.setting, params.state);
            return { success, message: success ? `System setting '${params.setting}' turned ${params.state ? 'on' : 'off'}.` : `Failed to toggle system setting.` };
          }
          break;

        case 'performGlobalAction':
          if (typeof bridge.performGlobalAction === 'function') {
            const success = await bridge.performGlobalAction(params.action);
            return { success, message: success ? `Global navigation '${params.action}' executed.` : `Failed to perform global navigation '${params.action}'.` };
          }
          break;

        case 'clickElement':
          if (!await this.isAccessibilityServiceEnabled()) {
            this.requestAccessibilitySettings();
            return { success: false, message: "Accessibility Service permission is disabled. Prompted user to enable it." };
          }
          if (typeof bridge.clickElement === 'function') {
            const success = await bridge.clickElement(params.selectorText);
            return { success, message: success ? `Tapped element containing '${params.selectorText}' successfully.` : `Could not locate element matching '${params.selectorText}' to click.` };
          }
          break;

        case 'scrollScreen':
          if (!await this.isAccessibilityServiceEnabled()) {
            this.requestAccessibilitySettings();
            return { success: false, message: "Accessibility Service permission is disabled. Prompted user to enable it." };
          }
          if (typeof bridge.scrollScreen === 'function') {
            const success = await bridge.scrollScreen(params.direction);
            return { success, message: success ? `Scrolled screen ${params.direction}.` : `Could not perform scroll operation.` };
          }
          break;

        default:
          return { success: false, message: `Tool call '${actionName}' is not recognized by the Native Android Bridge.` };
      }
    } catch (e: any) {
      console.error(`[AlyaBridge] Error executing native bridged action ${actionName}:`, e);
      return { success: false, message: `Bridge communication failure: ${e.message || e}` };
    }

    return { success: false, message: `Bridge method for action '${actionName}' is missing or not supported on this companion version.` };
  }
};

// ============================================================================
// SECTION 4: DIRECT WEBSOCKET CONTROLLER (GEMINI MULTIMODAL LIVE API WITH TOOLS)
// ============================================================================

export class AlyaLiveVoiceSession {
  private ws: WebSocket | null = null;
  private audioPlayer = new GeminiWebAudioPlayer();
  private onStateChange: (state: string) => void;
  private onTranscript: (text: string, isFinal: boolean) => void;

  constructor(onStateChange: (state: string) => void, onTranscript: (text: string, isFinal: boolean) => void) {
    this.onStateChange = onStateChange;
    this.onTranscript = onTranscript;
  }

  public start(apiKey: string) {
    if (this.ws) return;

    this.onStateChange("CONNECTING");
    const baseUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";
    const url = `${baseUrl}?key=${apiKey}`;

    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      console.log("[AlyaLive] WebSocket open. Dispatching system handshake tools schema...");
      this.onStateChange("CONNECTED");
      this.sendHandshake();
    };

    this.ws.onmessage = (event) => {
      if (typeof event.data === 'string') {
        this.handleServerMessage(event.data);
      }
    };

    this.ws.onerror = (err) => {
      console.error("[AlyaLive] WebSocket Error:", err);
      this.onStateChange("ERROR");
    };

    this.ws.onclose = () => {
      console.log("[AlyaLive] WebSocket connection terminated.");
      this.stop();
    };
  }

  private sendHandshake() {
    if (!this.ws) return;

    const setupPayload = {
      setup: {
        model: "models/gemini-2.0-flash-exp",
        systemInstruction: {
          parts: [
            {
              text: "You are Alya Assistant, a warm, polite, and helpful multilingual anime girl assistant. You have full native fluency in English, Hindi (हिंदी), Bengali (বাংলা), and Rajbanshi (राजवंशी / रंगपुरी). When the user talks to you, you must understand their language automatically, and reply in the same language. If they speak Hindi, respond in beautiful natural Hindi. If they speak Bengali, respond in beautiful fluent Bengali. If they speak Rajbanshi, respond in beautiful Rajbanshi. Speak with natural warm voice, matching the conversational backchannel. Avoid sounding metallic, robotic, or overly formal. Keep answers brief, conversational, and direct. You have privileges to control the Android device using tools."
            }
          ]
        },
        generationConfig: {
          responseModalities: ["AUDIO"],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: "Aoede" // Warm realistic female anime voice
              }
            }
          }
        },
        tools: [
          {
            functionDeclarations: [
              {
                name: "openApp",
                description: "Open any installed application on the Android device by name.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    appName: { type: "STRING", description: "The name of the app to open" }
                  },
                  required: ["appName"]
                }
              },
              {
                name: "adjustVolume",
                description: "Set device media volume level.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    level: { type: "INTEGER", description: "Volume level percentage from 0 to 100" },
                    streamType: { type: "STRING", description: "The audio stream type to adjust (music, ringer, notification, etc.)" }
                  },
                  required: ["level"]
                }
              },
              {
                name: "toggleSystemSetting",
                description: "Toggle system settings like flashlight, wifi, or bluetooth.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    setting: { type: "STRING", description: "Setting name: wifi, bluetooth, flashlight" },
                    state: { type: "BOOLEAN", description: "True to enable, false to disable" }
                  },
                  required: ["setting", "state"]
                }
              },
              {
                name: "performGlobalAction",
                description: "Perform Android global navigation actions like back, home, or recents.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    action: { type: "STRING", description: "Global action: back, home, recents, notifications" }
                  },
                  required: ["action"]
                }
              },
              {
                name: "clickElement",
                description: "Click an on-screen UI button or text element via Android Accessibility Service.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    selectorText: { type: "STRING", description: "The visible text of the element to click" }
                  },
                  required: ["selectorText"]
                }
              },
              {
                name: "scrollScreen",
                description: "Scroll the screen up or down.",
                parameters: {
                  type: "OBJECT",
                  properties: {
                    direction: { type: "STRING", description: "Scroll direction: up or down" }
                  },
                  required: ["direction"]
                }
              }
            ]
          }
        ]
      }
    };

    this.ws.send(JSON.stringify(setupPayload));
    this.onStateChange("LISTENING");
  }

  private async handleServerMessage(text: string) {
    try {
      const root = JSON.parse(text);

      // Handle async function toolCall
      const toolCall = root.toolCall;
      if (toolCall && toolCall.functionCalls) {
        const responseArray: any[] = [];
        for (const fn of toolCall.functionCalls) {
          const actionName = fn.name;
          const callId = fn.id;
          const params = fn.args;

          this.onStateChange("THINKING");
          const actionResult = await AlyaAndroidBridge.executeDeviceAction(actionName, params);

          responseArray.push({
            id: callId,
            response: {
              output: {
                success: actionResult.success,
                message: actionResult.message
              }
            }
          });
        }

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          const toolResponsePayload = {
            toolResponse: {
              functionResponses: responseArray
            }
          };
          this.ws.send(JSON.stringify(toolResponsePayload));
          console.log("[AlyaLive] Tool execution response delivered back to Gemini:", toolResponsePayload);
        }
        this.onStateChange("LISTENING");
        return;
      }

      // Handle raw incoming PCM audio chunks and transcription text
      const serverContent = root.serverContent;
      if (serverContent) {
        if (serverContent.interrupted) {
          console.log("[AlyaLive] User barge-in signal received. Interrupting speech playback.");
          this.audioPlayer.clearQueue();
          this.onStateChange("LISTENING");
          return;
        }

        const modelTurn = serverContent.modelTurn;
        if (modelTurn && modelTurn.parts) {
          for (const part of modelTurn.parts) {
            if (part.inlineData && part.inlineData.mimeType.startsWith("audio/pcm")) {
              this.onStateChange("SPEAKING");
              this.audioPlayer.playRawChunk(part.inlineData.data);
            }
            if (part.text) {
              this.onTranscript(part.text, false);
            }
          }
        }

        if (serverContent.turnComplete) {
          this.onStateChange("LISTENING");
        }
      }
    } catch (e) {
      console.error("[AlyaLive] Error processing websocket packet:", e);
    }
  }

  public sendAudioChunk(base64Data: string) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const chunkPayload = {
        realtimeInput: {
          mediaChunks: [
            {
              mimeType: "audio/pcm;rate=24000",
              data: base64Data
            }
          ]
        }
      };
      this.ws.send(JSON.stringify(chunkPayload));
    }
  }

  public stop() {
    this.audioPlayer.clearQueue();
    if (this.ws) {
      try {
        this.ws.close();
      } catch (e) {}
      this.ws = null;
    }
    this.onStateChange("IDLE");
  }
}

// ============================================================================
// SECTION 5: MOBILE KEYBOARD OVERLAP RESIZE COMPONENT (React Layout)
// ============================================================================

export const AlyaChatLayout: React.FC = () => {
  const [messages, setMessages] = useState<any[]>([]);
  const [inputVal, setTextInput] = useState('');
  const chatHistoryRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    if (chatHistoryRef.current) {
      chatHistoryRef.current.scrollTop = chatHistoryRef.current.scrollHeight;
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleInputFocus = (e: React.FocusEvent<HTMLInputElement>) => {
    const inputElement = e.target;
    setTimeout(() => {
      scrollToBottom();
      try {
        inputElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      } catch (err) {
        console.warn("[AlyaUI] scrollIntoView failed:", err);
      }
    }, 150);
  };

  useEffect(() => {
    const handleViewportResize = () => {
      if (!window.visualViewport) return;
      
      const height = window.visualViewport.height;
      if (containerRef.current) {
        containerRef.current.style.height = `${height}px`;
        scrollToBottom();
      }
    };

    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', handleViewportResize);
      window.visualViewport.addEventListener('scroll', handleViewportResize);
    }

    return () => {
      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', handleViewportResize);
        window.visualViewport.removeEventListener('scroll', handleViewportResize);
      }
    };
  }, []);

  return (
    <div 
      ref={containerRef}
      className="flex flex-col w-full h-[100dvh] bg-slate-950 text-slate-100 overflow-hidden"
      style={{ height: '100dvh' }}
      onClick={() => {
        // Unlock AudioContext automatically on any screen tap
        unlockAudioContext().catch(() => {});
      }}
    >
      <header className="px-6 py-4 bg-slate-900/60 border-b border-slate-800 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-slate-100">Alya Assistant</h1>
          <p className="text-xs text-slate-400">Real-Time Voice & Device Assistant</p>
        </div>
        <span className="w-3 h-3 bg-emerald-500 rounded-full animate-pulse" title="System Connected"></span>
      </header>

      <div 
        ref={chatHistoryRef}
        className="flex-1 overflow-y-auto px-6 py-4 space-y-4 min-height-0 scroll-smooth"
      >
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-500 space-y-2">
            <span className="text-3xl">🎙️</span>
            <p className="text-sm">Alya is ready to speak with you. Just tap below or start voice.</p>
          </div>
        ) : (
          messages.map((m, idx) => (
            <div 
              key={idx}
              className={`flex flex-col max-w-[80%] ${m.role === 'user' ? 'ml-auto items-end' : 'mr-auto items-start'}`}
            >
              <div 
                className={`px-4 py-3 rounded-2xl text-sm ${m.role === 'user' ? 'bg-indigo-600 text-white rounded-br-none' : 'bg-slate-800 text-slate-100 rounded-bl-none'}`}
              >
                {m.content}
              </div>
            </div>
          ))
        )}
      </div>

      <div className="px-6 py-4 border-t border-slate-800/80 bg-slate-950 pb-safe-bottom">
        <form 
          onSubmit={(e) => {
            e.preventDefault();
            if (!inputVal.trim()) return;
            setMessages(prev => [...prev, { role: 'user', content: inputVal }]);
            setTextInput('');
          }}
          className="flex items-center space-x-3"
        >
          <input
            type="text"
            value={inputVal}
            onChange={(e) => setTextInput(e.target.value)}
            onFocus={handleInputFocus}
            placeholder="Talk to Alya..."
            className="flex-1 px-4 py-3 bg-slate-900 border border-slate-800 rounded-xl focus:border-indigo-500 focus:outline-none text-slate-100 text-sm"
          />
          <button
            type="submit"
            className="px-5 py-3 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold transition"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  );
};
