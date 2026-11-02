/**
 * Centralized Session & Subscription Lifecycle Manager for Squish Web.
 *
 * Guarantees complete cross-account isolation:
 * 1. Tracks Firestore onSnapshot listeners and unsubscribes them immediately upon logout or user switch.
 * 2. Employs an incrementing Session Generation ID so in-flight async REST/Firestore responses
 *    from a previous user cannot mutate the state of a new user.
 * 3. Registers state reset callbacks across components/contexts for clean global state cleanup.
 */

type UnsubscribeFn = () => void;
type ResetHandlerFn = () => void;

class SessionManager {
  private activeSubscriptions: Map<string, Set<UnsubscribeFn>> = new Map();
  private resetHandlers: Set<ResetHandlerFn> = new Set();
  private sessionGeneration: number = 0;
  private currentUid: string | null = null;
  private userCache: Map<string, any> = new Map();

  /**
   * Returns the current session generation counter.
   * Async tasks capture this at initiation and check validity upon resolution.
   */
  public getGeneration(): number {
    return this.sessionGeneration;
  }

  /**
   * Returns current active UID managed by the session.
   */
  public getCurrentUid(): string | null {
    return this.currentUid;
  }

  /**
   * Returns total count of active real-time subscriptions across all scopes.
   */
  public getActiveSubscriptionCount(): number {
    let count = 0;
    this.activeSubscriptions.forEach((subs) => {
      count += subs.size;
    });
    return count;
  }

  /**
   * Checks if there are active subscriptions for a specific UID or globally.
   */
  public hasActiveSubscriptions(uid?: string): boolean {
    if (uid) {
      return (this.activeSubscriptions.get(uid)?.size ?? 0) > 0;
    }
    return this.getActiveSubscriptionCount() > 0;
  }

  /**
   * Stores a temporary session-scoped value (cleared on reset/logout).
   */
  public setCache(key: string, value: any): void {
    this.userCache.set(key, value);
  }

  /**
   * Retrieves a temporary session-scoped value.
   */
  public getCache<T>(key: string): T | undefined {
    return this.userCache.get(key) as T | undefined;
  }

  /**
   * Clears in-memory user cache.
   */
  public clearCache(): void {
    this.userCache.clear();
  }

  /**
   * Sets the active user UID. If switching users, performs an immediate session reset first.
   */
  public setActiveUser(uid: string | null) {
    if (this.currentUid !== uid) {
      this.resetSession();
      this.currentUid = uid;
    }
  }

  /**
   * Validates whether a specific generation counter and UID match the active session.
   */
  public isValidSession(generation: number, uid?: string): boolean {
    if (generation !== this.sessionGeneration) {
      return false;
    }
    if (uid !== undefined && this.currentUid !== uid) {
      return false;
    }
    return true;
  }

  /**
   * Registers a Firestore or real-time subscription for automatic lifecycle cleanup.
   * Returns a function to unsubscribe and deregister manually.
   */
  public registerSubscription(uid: string, unsubscribe: UnsubscribeFn): UnsubscribeFn {
    if (!this.activeSubscriptions.has(uid)) {
      this.activeSubscriptions.set(uid, new Set());
    }
    const set = this.activeSubscriptions.get(uid)!;
    set.add(unsubscribe);

    let isCleanedUp = false;
    return () => {
      if (isCleanedUp) return;
      isCleanedUp = true;
      try {
        unsubscribe();
      } catch (err) {
        console.warn('Error unsubscribing listener:', err);
      }
      set.delete(unsubscribe);
    };
  }

  /**
   * Registers a callback to be invoked during global state reset (e.g. on logout or user switch).
   */
  public registerResetHandler(handler: ResetHandlerFn): () => void {
    this.resetHandlers.add(handler);
    return () => {
      this.resetHandlers.delete(handler);
    };
  }

  /**
   * Tears down all active listeners, cancels in-flight async operations by incrementing the generation ID,
   * invokes all registered reset handlers, and clears session data.
   */
  public resetSession() {
    this.sessionGeneration++;
    this.currentUid = null;
    this.clearCache();

    // 1. Unsubscribe all active Firestore listeners
    this.activeSubscriptions.forEach((subs, uid) => {
      subs.forEach((unsub) => {
        try {
          unsub();
        } catch (err) {
          console.warn(`Error cleaning up listener for user ${uid}:`, err);
        }
      });
    });
    this.activeSubscriptions.clear();

    // 2. Invoke all UI state reset handlers
    this.resetHandlers.forEach((handler) => {
      try {
        handler();
      } catch (err) {
        console.warn('Error executing reset handler:', err);
      }
    });

    // 3. Clear session storage tokens and client caches
    if (typeof window !== 'undefined') {
      try {
        sessionStorage.removeItem('squish_session_cache');
        sessionStorage.removeItem('squish_threat_cursor');
        sessionStorage.clear();
      } catch {}
    }
  }
}

export const sessionManager = new SessionManager();
