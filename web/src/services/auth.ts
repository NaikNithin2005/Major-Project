import { initializeApp, getApps, getApp, FirebaseApp } from 'firebase/app';
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

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || 'demo-api-key',
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || 'squish-shield.firebaseapp.com',
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || 'squish-shield',
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || 'squish-shield.appspot.com',
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || '123456789',
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || '1:123456789:web:abcdef',
};

let app: FirebaseApp | null = null;
let auth: Auth | null = null;

try {
  if (typeof window !== 'undefined') {
    app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
    auth = getAuth(app);
  }
} catch (err) {
  console.warn('Firebase client SDK initialization deferred:', err);
}

export { app, auth };

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
  if (!auth) {
    throw new Error('Firebase Auth is not initialized in this browser environment.');
  }

  // 1. Create Firebase Auth account
  const userCredential = await createUserWithEmailAndPassword(auth, email.trim(), pass);
  const fbUser = userCredential.user;

  // 2. Set Firebase display name
  try {
    await updateProfile(fbUser, { displayName: name.trim() });
  } catch (err) {
    console.warn('Could not set displayName on Firebase user:', err);
  }

  // 3. Optional Firestore Profile sync under users/{uid}
  if (app) {
    try {
      const db = getFirestore(app);
      await setDoc(doc(db, 'users', fbUser.uid), {
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
  }

  // 4. Obtain authoritative Firebase ID token
  const idToken = await fbUser.getIdToken();
  setAuthToken(idToken);

  // 5. Verify token with FastAPI backend to auto-provision user record
  const verifyRes = await api.auth.verify();
  const backendUser = verifyRes.user;

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
  if (!auth) {
    throw new Error('Firebase Auth is not initialized in this browser environment.');
  }

  // 1. Firebase Client Authentication
  const userCredential = await signInWithEmailAndPassword(auth, email.trim(), pass);
  const idToken = await userCredential.user.getIdToken();

  // 2. Set token in API client
  setAuthToken(idToken);

  // 3. Verify token with FastAPI backend & fetch authoritative User profile
  const verifyRes = await api.auth.verify();
  const backendUser = verifyRes.user;

  // 4. Account active check
  if (!backendUser.is_active) {
    await fbSignOut(auth);
    setAuthToken(null);
    throw new Error('Access Denied: Your account has been deactivated. Please contact support.');
  }

  // 5. Optional role restriction
  if (requiredRole === 'ADMIN' && backendUser.role !== 'ADMIN') {
    await fbSignOut(auth);
    setAuthToken(null);
    throw new Error('Access Denied: Administrative privileges required to access the Admin Console.');
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
  if (!auth) {
    throw new Error('Firebase Auth is not initialized.');
  }
  await sendPasswordResetEmail(auth, email.trim());
}

/**
 * Developer / Mock Login
 * Supported when backend ALLOW_MOCK_AUTH=true for testing or dev setups without live Firebase project keys.
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

  const verifyRes = await api.auth.verify();
  const backendUser = verifyRes.user;

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
  if (auth) {
    try {
      await fbSignOut(auth);
    } catch {}
  }
  if (typeof window !== 'undefined') {
    localStorage.removeItem('squish_auth_token');
    localStorage.removeItem('squish_auth_user');
    localStorage.removeItem('squish_admin_token');
    localStorage.removeItem('squish_admin_user');
  }
}
