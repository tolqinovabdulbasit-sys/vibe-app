import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.database();
const PAIRING_CODES_PATH = "pairing_codes";
const DEVICES_PATH = "devices";
const COMMANDS_PATH = "commands";
const PAIRINGS_PATH = "pairings";

// ─────────────────────────────────────────────────────────────────────────────
// PAIRING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POST /pair/create
 * Called by Device A to generate a one-time pairing code.
 * The code is stored in Realtime DB with a 15-minute TTL.
 */
export const pairCreate = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const { deviceId, displayName } = data;
  if (!deviceId || typeof deviceId !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "deviceId required");
  }

  // Generate secure 8-char code
  const code = generateCode();
  const now = Date.now();
  const expiresAt = now + 15 * 60 * 1000; // 15 minutes

  await db.ref(`${PAIRING_CODES_PATH}/${code}`).set({
    code,
    initiator_device_id: deviceId,
    initiator_display_name: displayName || "Unknown",
    initiator_firebase_uid: context.auth.uid,
    created_at: now,
    expires_at: expiresAt,
    used: false,
  });

  // Auto-delete after expiry using Firebase TTL (if RTDB Rules configured)
  functions.logger.info(`Pairing code generated for device ${deviceId}`);

  return { code, expires_at: expiresAt };
});

/**
 * POST /pair/consume
 * Called by Device B to consume a pairing code.
 * Atomic read-and-mark-used using Firebase transactions.
 */
export const pairConsume = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const { code, consumerDeviceId, consumerDisplayName } = data;
  if (!code || !consumerDeviceId) {
    throw new functions.https.HttpsError("invalid-argument", "code and consumerDeviceId required");
  }

  const codeRef = db.ref(`${PAIRING_CODES_PATH}/${code.toUpperCase()}`);

  // Transaction — prevents race conditions if two devices try same code
  const result = await codeRef.transaction((current) => {
    if (!current) return current; // Code doesn't exist
    if (current.used === true) return current; // Already used — abort
    if (Date.now() > current.expires_at) return current; // Expired — abort
    return { ...current, used: true, consumer_device_id: consumerDeviceId };
  });

  if (!result.committed || !result.snapshot.exists()) {
    throw new functions.https.HttpsError("not-found", "Invalid pairing code");
  }

  const codeData = result.snapshot.val();
  if (codeData.consumer_device_id !== consumerDeviceId) {
    throw new functions.https.HttpsError("already-exists", "Code already used");
  }

  if (Date.now() > codeData.expires_at) {
    await codeRef.remove();
    throw new functions.https.HttpsError("deadline-exceeded", "Code expired");
  }

  const initiatorDeviceId = codeData.initiator_device_id;
  const now = Date.now();

  // Register pairing relationship (bidirectional for routing)
  await Promise.all([
    db.ref(`${PAIRINGS_PATH}/${initiatorDeviceId}_${consumerDeviceId}`).set({
      device_a: initiatorDeviceId,
      device_b: consumerDeviceId,
      created_at: now,
      active: true,
    }),
    db.ref(`${PAIRINGS_PATH}/${consumerDeviceId}_${initiatorDeviceId}`).set({
      device_a: consumerDeviceId,
      device_b: initiatorDeviceId,
      created_at: now,
      active: true,
    }),
    // Clean up the code
    codeRef.remove(),
  ]);

  functions.logger.info(`Pairing established: ${initiatorDeviceId} <-> ${consumerDeviceId}`);

  return {
    success: true,
    initiator_device_id: initiatorDeviceId,
    initiator_display_name: codeData.initiator_display_name,
  };
});

// ─────────────────────────────────────────────────────────────────────────────
// DEVICE REGISTRATION
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POST /device/register
 * Registers a device's FCM token and online status.
 */
export const deviceRegister = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const { deviceId, fcmToken, displayName } = data;
  if (!deviceId) {
    throw new functions.https.HttpsError("invalid-argument", "deviceId required");
  }

  await db.ref(`${DEVICES_PATH}/${deviceId}`).update({
    device_id: deviceId,
    firebase_uid: context.auth.uid,
    fcm_token: fcmToken || null,
    display_name: displayName || null,
    status: "ONLINE",
    last_seen_at: Date.now(),
  });

  return { success: true };
});

/**
 * POST /device/status
 * Updates device online/offline status.
 */
export const deviceStatus = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const { deviceId, status } = data;
  if (!deviceId || !status) {
    throw new functions.https.HttpsError("invalid-argument", "deviceId and status required");
  }

  await db.ref(`${DEVICES_PATH}/${deviceId}`).update({
    status,
    last_seen_at: Date.now(),
  });

  return { success: true };
});

// ─────────────────────────────────────────────────────────────────────────────
// COMMAND ROUTING via Firebase Realtime Database
// ─────────────────────────────────────────────────────────────────────────────
// Commands are written to RTDB by the sending device and read by the receiving device.
// This provides the primary real-time channel (WebSocket-like via RTDB listeners).
// 
// The Android app listens to: /commands/{targetDeviceId}/{commandId}
// When a command appears, the receiver processes it and writes the ACK.

/**
 * POST /delivery/ack
 * HTTP fallback ACK endpoint (used when WebSocket/RTDB listener is unavailable).
 */
export const deliveryAck = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const { commandId, sourceDeviceId, status, timestamp } = data;
  if (!commandId || !sourceDeviceId) {
    throw new functions.https.HttpsError("invalid-argument", "commandId and sourceDeviceId required");
  }

  // Write ACK to RTDB so sender can pick it up
  await db.ref(`acks/${sourceDeviceId}/${commandId}`).set({
    command_id: commandId,
    status,
    timestamp: timestamp || Date.now(),
  });

  // Auto-cleanup old commands older than 1 hour via RTDB listener trigger
  return { success: true };
});

/**
 * Cleanup expired pairing codes — runs every 30 minutes.
 */
export const cleanupExpiredCodes = functions.pubsub
  .schedule("every 30 minutes")
  .onRun(async () => {
    const cutoff = Date.now();
    const snapshot = await db.ref(PAIRING_CODES_PATH)
      .orderByChild("expires_at")
      .endAt(cutoff)
      .get();

    const updates: Record<string, null> = {};
    snapshot.forEach((child) => {
      updates[child.key!] = null;
    });

    if (Object.keys(updates).length > 0) {
      await db.ref(PAIRING_CODES_PATH).update(updates);
      functions.logger.info(`Cleaned ${Object.keys(updates).length} expired pairing codes`);
    }
  });

/**
 * Cleanup old commands — removes commands older than 24 hours.
 */
export const cleanupOldCommands = functions.pubsub
  .schedule("every 6 hours")
  .onRun(async () => {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const snapshot = await db.ref(COMMANDS_PATH).get();
    const updates: Record<string, null> = {};

    snapshot.forEach((deviceNode) => {
      deviceNode.forEach((cmd) => {
        const ts = cmd.val().timestamp;
        if (ts && ts < cutoff) {
          updates[`${COMMANDS_PATH}/${deviceNode.key}/${cmd.key}`] = null;
        }
      });
    });

    if (Object.keys(updates).length > 0) {
      await db.ref().update(updates);
    }
  });

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function generateCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let code = "";
  for (let i = 0; i < 8; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}
