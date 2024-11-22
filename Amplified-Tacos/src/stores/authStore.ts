import { defineStore } from "pinia";
import { signIn, signOut, getCurrentUser, fetchUserAttributes } from 'aws-amplify/auth';
import type { AuthUser, FetchUserAttributesOutput } from '@aws-amplify/auth';

interface UserState {
    user: AuthUser | null
    isAuthenticated: boolean,
    userAttributes: FetchUserAttributesOutput | null;
  }

export const useAuthStore = defineStore('auth', {
  state: (): UserState => ({
    user: null,
    isAuthenticated: false,
    userAttributes: null
  }),
  actions: {
    async signIn(username: string, password: string) {
      try {
        const { isSignedIn, nextStep } = await signIn({
          username,
          password
        });

        if (isSignedIn) {
          const user = await getCurrentUser();
          this.user = user;
          this.isAuthenticated = true;
        }
        return { isSignedIn, nextStep };
      } catch (error) {
        console.error('Sign in error:', error);
        throw error;
      }
    },
    async signOut() {
      try {
        await signOut();
        this.user = null;
        this.isAuthenticated = false;
      } catch (error) {
        console.error('Sign out error:', error);
        throw error;
      }
    },
    async fetchAttributes() {
      try {
        if (this.isAuthenticated) {
          const attributes = await fetchUserAttributes();
          this.userAttributes = attributes;
          return attributes;
        }
        return null;
      } catch (error) {
        console.error('Fetch attributes error:', error);
        throw error;
      }
    },

    async checkUser() {
      try {
        const user = await getCurrentUser();
        this.user = user;
        this.isAuthenticated = true;
        await this.fetchAttributes();
        return user;
      } catch (error) {
        this.user = null;
        this.isAuthenticated = false;
        this.userAttributes = null;
        return null;
      }
    }
  },
  getters: {
    username: (state) => state.user?.username,
    email: (state) => state.userAttributes?.email,
    givenName: (state) => state.userAttributes?.given_name,
    familyName: (state) => state.userAttributes?.family_name,
    phoneNumber: (state) => state.userAttributes?.phone_number
  }
})