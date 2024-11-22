<template>
  <authenticator
    :form-fields="formFields"
     :services="services"
  >
    <template v-slot="{ user, signOut}">
      <h1>Hello {{ authStore.userAttributes?.given_name || user.username }}!</h1>
      <button @click="handleSignOut">Sign Out</button>

      <div v-if="authStore.userAttributes">
        <p>Email: {{ authStore.email }}</p>
        <p>Name: {{ authStore.givenName }} {{ authStore.familyName }}</p>
        <p>Phone: {{ authStore.phoneNumber }}</p>

        <HelloWorld />
      </div>
    </template>
  </authenticator>
</template>

<script lang="ts" setup>
import { Authenticator } from "@aws-amplify/ui-vue";
import "@aws-amplify/ui-vue/styles.css";

import { Amplify } from "aws-amplify";
import outputs from "../../amplify_outputs.json";
import { onMounted } from 'vue';
import { useAuthStore } from "@/stores/authStore";
import HelloWorld from "@/components/HelloWorld.vue";

// Define the events emitted by the component
defineEmits<{
  (e: "sign-in"): void;
  (e: "sign-out"): void;
}>();

Amplify.configure(outputs);
const authStore = useAuthStore();

onMounted(async () => {
  await authStore.checkUser();
});

const services = {
  async handleSignIn(formData: any) {
    try {
      const { username, password } = formData;
      const result = await authStore.signIn(username, password);
      return {
        ...result,
        username,
      };
    } catch (error) {
      console.error('Sign in error:', error);
      throw error;
    }
  },
  async handlePostSignIn() {
    console.log('Sign in successful');
    await authStore.checkUser();
    await authStore.fetchAttributes();
  }
};

const handleSignOut = async () => {
  debugger // eslint-disable-line
  await authStore.signOut();
};

const formFields = {
  signUp: {
    given_name: {
      label: "First Name",
      placeholder: "Enter your first name",
      required: true,
    },
    family_name: {
      label: "Last Name",
      placeholder: "Enter your last name",
      required: true,
    },
    phone_number: {
      label: "Phone Number",
      placeholder: "Enter your phone number",
      required: true,
    },
  },
};
</script>
