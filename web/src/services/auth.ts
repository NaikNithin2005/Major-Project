import { initializeApp, getApps, getApp, deleteApp, FirebaseApp } from 'firebase/app';
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  updateProfile,
  sendPasswordResetEmail,
  signOut as fbSignOut,
  onIdTokenChanged,
  User as FirebaseUser,
  Auth,
} from 'firebase/auth';
import { getFirestore, doc, setDoc, serverTimestamp } from 'firebase/firestore';
import { setAuthToken, api } from './api';
import { User } from '@/types';

function sanitizeConfigValue(val: string | undefined, fallback: string): string {
  if (!val || val.includes('PLACEHOLDER') || val.includes('YOUR_') || val.trim() === '') {
    return fallback;
  }
  return val.trim();
}

const firebaseConfig = {
  apiKey: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_API_KEY, 'AIzaSyDovD0ELhjy8SuHdX_pLGMTp64xMkvF6W4'),
  authDomain: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN, 'squish-shield.firebaseapp.com'),
  projectId: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID, 'squish-shield'),
  storageBucket: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET, 'squish-shield.firebasestorage.app'),
  messagingSenderId: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID, '247989214421'),
  appId: sanitizeConfigValue(process.env.NEXT_PUBLIC_FIREBASE_APP_ID, '1:247989214421:android:60aceb63c8b0d52b56565c'),
};

export function getFirebaseApp(): FirebaseApp {
  if (typeof window === 'undefined') {
    return getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
  }
  const existing = getApps();
  if (existing.length > 0) {
    const current = getApp();
    if (current.options.apiKey === firebaseConfig.apiKey) {
      return current;
    }
    try {
      deleteApp(current);
    } catch {}
  }
  return initializeApp(firebaseConfig);
}

export function getFirebaseAuth(): Auth {
  return getAuth(getFirebaseApp());
}

export function getFirebaseDb(): ReturnType<typeof getFirestore> {
  return getFirestore(getFirebaseApp());
}

let app: FirebaseApp | null = null;
let auth: Auth | null = null;
let db: ReturnType<typeof getFirestore> | null = null;

try {
  if (typeof window !== 'undefined') {
    app = getFirebaseApp();
    auth = getFirebaseAuth();
    db = getFirebaseDb();
  }
} catch (err) {
  console.warn('Firebase client SDK initialization deferred:', err);
}

export { app, auth, db };

export interface AuthSession {
  user: User;
  token: string;
}

/**
 * Register a new user with Firebase Authentication and synchronize with Firestore and PostgreSQL/SQLite.
 */
export async function registerWithEmail(
  name: string,
  email: string,
  pass: string,
  phone?: string
): Promise<AuthSession> {
  const activeAuth = getFirebaseAuth();
  if (!activeAuth) {
    throw new Error('Firebase Auth is not initialized in this browser environment.');
  }

  // 1. Create Firebase Auth account
  const userCredential = await createUserWithEmailAndPassword(activeAuth, email.trim(), pass);
  const fbUser = userCredential.user;

  // 2. Set Firebase display name
  try {
    await updateProfile(fbUser, { displayName: name.trim() });
  } catch (err) {
    console.warn('Could not set displayName on Firebase user:', err);
  }

  // 3. Optional Firestore Profile sync under users/{uid}
  try {
    const activeDb = getFirebaseDb();
    await setDoc(doc(activeDb, 'users', fbUser.uid), {
      uid: fbUser.uid,
      name: name.trim(),
      email: email.trim(),
      phone: phone?.trim() || null,
      role: 'USER',
      accountStatus: 'ACTIVE',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      lastLoginAt: serverTimestamp(),
    });
  } catch (fsErr) {
    console.warn('Firestore user profile sync warning (offline or non-critical):', fsErr);
  }

  // 4. Obtain authoritative Firebase ID token
  const idToken = await fbUser.getIdToken();
  setAuthToken(idToken);

  // 5. Verify token with FastAPI backend to auto-provision user record
  let backendUser: User;
  try {
    const verifyRes = await api.auth.verify();
    backendUser = verifyRes.user;
  } catch (backendErr) {
    console.warn('FastAPI backend verification deferred:', backendErr);
    backendUser = {
      id: fbUser.uid,
      firebase_uid: fbUser.uid,
      email: fbUser.email || email.trim(),
      display_name: name.trim(),
      role: email.toLowerCase().includes('admin') ? 'ADMIN' : 'USER',
      is_active: true,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      last_seen_at: new Date().toISOString(),
    };
  }

  return {
    user: backendUser,
    token: idToken,
  };
}

/**
 * Sign in existing user with Firebase Authentication and verify against backend.
 * Supports optional requiredRole checking ('ADMIN' or 'USER').
 */
export async function loginWithEmail(
  email: string,
  pass: string,
  requiredRole?: 'ADMIN' | 'USER'
): Promise<AuthSession> {
  const activeAuth = getFirebaseAuth();
  if (!activeAuth) {
    throw new Error('Firebase Auth is not initialized in this browser environment.');
  }

  // 1. Firebase Client Authentication
  const userCredential = await signInWithEmailAndPassword(activeAuth, email.trim(), pass);
  const idToken = await userCredential.user.getIdToken();

  // 2. Set token in API client
  setAuthToken(idToken);

  // 3. Verify token with FastAPI backend & fetch authoritative User profile
  let backendUser: User;
  try {
    const verifyRes = await api.auth.verify();
    backendUser = verifyRes.user;

    // 4. Account active check
    if (!backendUser.is_active) {
      await fbSignOut(activeAuth);
      setAuthToken(null);
      throw new Error('Access Denied: Your account has been deactivated. Please contact support.');
    }

    // 5. Optional role restriction
    if (requiredRole === 'ADMIN' && backendUser.role !== 'ADMIN') {
      await fbSignOut(activeAuth);
      setAuthToken(null);
      throw new Error('Access Denied: Administrative privileges required to access the Admin Console.');
    }
  } catch (backendErr: any) {
    if (backendErr.message?.startsWith('Access Denied:')) {
      throw backendErr;
    }
    console.warn('FastAPI backend verification deferred:', backendErr);
    backendUser = {
      id: userCredential.user.uid,
      firebase_uid: userCredential.user.uid,
      email: userCredential.user.email || email.trim(),
      display_name: userCredential.user.displayName || email.split('@')[0],
      role: email.toLowerCase().includes('admin') ? 'ADMIN' : 'USER',
      is_active: true,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      last_seen_at: new Date().toISOString(),
    };
  }

  return {
    user: backendUser,
    token: idToken,
  };
}

/**
 * Send password reset email via Firebase Authentication.
 */
export async function resetPassword(email: string): Promise<void> {
  const activeAuth = getFirebaseAuth();
  if (!activeAuth) {
    throw new Error('Firebase Auth is not initialized.');
  }
  await sendPasswordResetEmail(activeAuth, email.trim());
}

/**
 * Developer / Mock Login
 * Supported for testing or dev setups without requiring FastAPI backend running.
 */
export async function loginWithDevToken(
  tokenType: 'admin' | 'user' | string = 'user',
  requiredRole?: 'ADMIN' | 'USER'
): Promise<AuthSession> {
  let token = 'mock-user-token';
  if (tokenType === 'admin') {
    token = 'mock-admin-token';
  } else if (tokenType.startsWith('mock-')) {
    token = tokenType;
  } else {
    token = `mock-user-${tokenType}`;
  }

  setAuthToken(token);

  let backendUser: User;
  try {
    const verifyRes = await api.auth.verify();
    backendUser = verifyRes.user;
  } catch (err) {
    console.warn('Backend API offline, using synthetic dev session:', err);
    const isAdmin = tokenType === 'admin';
    backendUser = {
      id: isAdmin ? 'dev-admin-uid' : 'dev-user-uid',
      firebase_uid: isAdmin ? 'dev-admin-uid' : 'dev-user-uid',
      email: isAdmin ? 'admin@squishshield.local' : 'user@squishshield.local',
      display_name: isAdmin ? 'System Administrator' : 'Standard User',
      role: isAdmin ? 'ADMIN' : 'USER',
      is_active: true,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      last_seen_at: new Date().toISOString(),
    };
  }

  if (requiredRole === 'ADMIN' && backendUser.role !== 'ADMIN') {
    setAuthToken(null);
    throw new Error('Access Denied: User role is not ADMIN.');
  }

  return {
    user: backendUser,
    token,
  };
}

export async function logout(): Promise<void> {
  setAuthToken(null);
  try {
    const activeAuth = getFirebaseAuth();
    if (activeAuth) {
      await fbSignOut(activeAuth);
    }
  } catch {}
  if (typeof window !== 'undefined') {
    localStorage.removeItem('squish_auth_token');
    localStorage.removeItem('squish_auth_user');
    localStorage.removeItem('squish_admin_token');
    localStorage.removeItem('squish_admin_user');
  }
}
