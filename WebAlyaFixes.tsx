/**
 * Alya Assistant — Complete Web-Side Production Audio & Authentication Upgrades
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
  const length = arrayBuffer.byteLength / 2; // 16-bit = 2 bytes per sample
  const float32 = new Float32Array(length);

  for (let i = 0; i < length; i++) {
    const pcm16 = view.getInt16(i * 2, true); // true = Little-Endian
    float32[i] = pcm16 / 32768; // Normalize to -1.0 to 1.0 range
  }
  return float32;
}

/**
 * Single-Instance persistent AudioContext streaming queue manager.
 * Locked at exactly 24000Hz (24kHz) matching Google Gemini Live PCM stream.
 */
export class GeminiWebAudioPlayer {
  private audioCtx: AudioContext | null = null;
  private startTime: number = 0;
  private sampleRate: number = 24000;

  constructor() {
    this.audioCtx = null;
    this.startTime = 0;
  }

  private initContext() {
    if (!this.audioCtx) {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioContextClass({
        sampleRate: this.sampleRate, // Enforce 24kHz matching server sampling
        latencyHint: 'interactive'
      });
      this.startTime = this.audioCtx.currentTime;
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  /**
   * Feed a base64-encoded 24kHz Mono 16-bit PCM raw audio chunk into the streaming Web Audio queue
   */
  public playRawChunk(base64Chunk: string) {
    this.initContext();
    if (!this.audioCtx) return;

    try {
      const binaryString = atob(base64Chunk);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      const floatData = decodePcm16ToFloat32(bytes.buffer);
      const audioBuffer = this.audioCtx.createBuffer(1, floatData.length, this.sampleRate);
      audioBuffer.getChannelData(0).set(floatData);

      const sourceNode = this.audioCtx.createBufferSource();
      sourceNode.buffer = audioBuffer;

      // Connect source to speakers
      sourceNode.connect(this.audioCtx.destination);

      // Jitter buffer queue scheduling to eliminate clicks, pops, and stuttering
      const currentTime = this.audioCtx.currentTime;
      if (this.startTime < currentTime) {
        this.startTime = currentTime + 0.05; // Guard interval for latency spikes
      }

      sourceNode.start(this.startTime);
      this.startTime += audioBuffer.duration;
    } catch (e) {
      console.error("[AlyaAudio] Error writing stream chunk:", e);
    }
  }

  public clearQueue() {
    if (this.audioCtx) {
      this.audioCtx.close().catch(() => {});
      this.audioCtx = null;
    }
    this.startTime = 0;
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
    // E.164 verification pattern
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
 * SECTION 2.5: CUSTOM REACT HOOK FOR RECAPTCHA LIFECYCLE MANAGEMENT
 * Resolves auth/operation-not-allowed and auth/argument-error dynamically
 */
export function usePhoneAuthWithRecaptcha(containerId: string) {
  const [verificationId, setVerificationId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const verifierRef = useRef<RecaptchaVerifier | null>(null);
  const auth = getAuth();

  useEffect(() => {
    // Explicit cleanup on unmount to completely eliminate duplicate widgets or auth/argument-error
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
      // Lazy and safe initialization of the invisible reCAPTCHA verifier attached to DOM container
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

      // Handle duplicate instance rendering error gracefully (auth/argument-error)
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
// SECTION 3: MOBILE KEYBOARD OVERLAP RESIZE COMPONENT (React)
// ============================================================================

export const AlyaChatLayout: React.FC = () => {
  const [messages, setMessages] = useState<any[]>([]);
  const [inputVal, setTextInput] = useState('');
  const chatHistoryRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Scroll message board to bottom dynamically
  const scrollToBottom = () => {
    if (chatHistoryRef.current) {
      chatHistoryRef.current.scrollTop = chatHistoryRef.current.scrollHeight;
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Handle mobile keyboard adjustments on focus
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

  // Keyboard Overlap layout fix via Visual Viewport API
  useEffect(() => {
    const handleViewportResize = () => {
      if (!window.visualViewport) return;
      
      const height = window.visualViewport.height;
      if (containerRef.current) {
        // Enforce the computed visual height to offset the virtual soft keyboard
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
      style={{ height: '100dvh' }} // Uses Dynamic Viewport Unit for fallback
    >
      {/* 1. Header Area */}
      <header className="px-6 py-4 bg-slate-900/60 border-b border-slate-800 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-slate-100">Alya Assistant</h1>
          <p className="text-xs text-slate-400">Real-Time Voice & Device Assistant</p>
        </div>
        <span className="w-3 h-3 bg-emerald-500 rounded-full animate-pulse" title="System Connected"></span>
      </header>

      {/* 2. Message History Area */}
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

      {/* 3. Input Form Area inside flex flow (NOT absolute/fixed) */}
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
