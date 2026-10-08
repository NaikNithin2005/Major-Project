'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User } from '@/types';
import { setAuthToken, setOnUnauthorizedCallback, api } from '@/services/api';
import {
  loginWithEmail,
  registerWithEmail,
  loginWithDevToken,
  resetPassword as authResetPassword,
  logout as authLogout,
  auth,
} from '@/services/auth';
import { onIdTokenChanged } from 'firebase/auth';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, pass: string, requiredRole?: 'ADMIN' | 'USER') => Promise<void>;
  register: (name: string, email: string, pass: string, phone?: string) => Promise<void>;
  loginDev: (type?: string, requiredRole?: 'ADMIN' | 'USER') => Promise<void>;
  resetPassword: (email: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const handleLogout = async () => {
    setUser(null);
    setToken(null);
    await authLogout();
  };

  useEffect(() => {
    setOnUnauthorizedCallback(() => {
      handleLogout();
    });

    // Check cached session
    const initSession = async () => {
      try {
        const savedToken =
          localStorage.getItem('squish_auth_token') ||
          localStorage.getItem('squish_admin_token');

        if (savedToken) {
          setAuthToken(savedToken);
          // Verify with backend
          const backendUser = await api.auth.getMe();
          if (backendUser && backendUser.is_active) {
            setUser(backendUser);
            setToken(savedToken);
          } else {
            await handleLogout();
          }
        }
      } catch (err) {
        // Backend unavailable or token expired
        await handleLogout();
      } finally {
        setIsLoading(false);
      }
    };

    initSession();

    // Listen to Firebase token changes if live auth is present
    if (auth) {
      const unsubscribe = onIdTokenChanged(auth, async (fbUser) => {
        if (fbUser) {
          try {
            const idToken = await fbUser.getIdToken();
            setAuthToken(idToken);
            setToken(idToken);
            localStorage.setItem('squish_auth_token', idToken);
          } catch {}
        }
      });
      return () => unsubscribe();
    }
  }, []);

  const login = async (email: string, pass: string, requiredRole?: 'ADMIN' | 'USER') => {
    setIsLoading(true);
    try {
      const session = await loginWithEmail(email, pass, requiredRole);
      setUser(session.user);
      setToken(session.token);
      localStorage.setItem('squish_auth_token', session.token);
      localStorage.setItem('squish_auth_user', JSON.stringify(session.user));
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (name: string, email: string, pass: string, phone?: string) => {
    setIsLoading(true);
    try {
      const session = await registerWithEmail(name, email, pass, phone);
      setUser(session.user);
      setToken(session.token);
      localStorage.setItem('squish_auth_token', session.token);
      localStorage.setItem('squish_auth_user', JSON.stringify(session.user));
    } finally {
      setIsLoading(false);
    }
  };

  const loginDev = async (type: string = 'user', requiredRole?: 'ADMIN' | 'USER') => {
    setIsLoading(true);
    try {
      const session = await loginWithDevToken(type, requiredRole);
      setUser(session.user);
      setToken(session.token);
      localStorage.setItem('squish_auth_token', session.token);
      localStorage.setItem('squish_auth_user', JSON.stringify(session.user));
    } finally {
      setIsLoading(false);
    }
  };

  const resetPassword = async (email: string) => {
    await authResetPassword(email);
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await handleLogout();
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        isAuthenticated: !!user && !!token,
        isAdmin: user?.role === 'ADMIN',
        login,
        register,
        loginDev,
        resetPassword,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
