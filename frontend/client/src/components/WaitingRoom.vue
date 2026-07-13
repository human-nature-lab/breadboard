<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PlayerData } from '@human-nature-lab/breadboard-core'
import {
  notify,
  requestNotificationPermission,
  useNotificationPermission,
} from '../lib/notify'

const props = defineProps<{
  player: PlayerData
  debug?: boolean
}>()

const waitingRoom = computed(() => {
  return props.player._system.waitingRoom
})

const sentReadyUp = ref(false)
watch(
  () => waitingRoom.value.state,
  (state) => {
    if (state === 'waiting-room') {
      sentReadyUp.value = false
    }
  },
)

let notification: Notification | null = null
watch(
  () => waitingRoom.value.state,
  (state) => {
    if (state === 'ready-up' && document.hidden) {
      notification = notify('Ready up!', {
        body: 'Click the ready button to indicate you are ready to start the game.',
      })
    }
  },
)

const notificationPermissionState = useNotificationPermission()
const triedToEnableNotifications = ref(
  notificationPermissionState.value === 'granted',
)
watch(notificationPermissionState, (state) => {
  if (state === 'prompt') {
    triedToEnableNotifications.value = false
  } else if (state === 'granted') {
    triedToEnableNotifications.value = true
  }
})
const successfulTest = ref(false)

async function enableNotifications() {
  await requestNotificationPermission()
  triedToEnableNotifications.value = true
  if (notificationPermissionState.value === 'granted') {
    testNotification()
  }
}

async function testNotification() {
  const not = await notify('Test notification', {
    body: 'Please click this notification to verify that notifications are working!',
  })
  if (!not) {
    return
  }
  not.onclick = () => {
    console.log('Notification clicked')
    successfulTest.value = true
  }
}

function readyUp() {
  Breadboard.send('waiting-room:ready')
  sentReadyUp.value = true
  // if (notification) {
  //   notification.close()
  // }
}

const enabledNotifications = computed(() => {
  return notificationPermissionState.value === 'granted'
})
</script>

<template>
  <div class="pa-4 text-center waiting-room">
    <h1 class="my-4">Waiting Room</h1>
    <v-slide-x-transition group>
      <div v-show="waitingRoom.state === 'ready-up'" key="ready">
        <p v-if="!waitingRoom.isReady">
          Let us know you're ready by clicking the ready button
        </p>
        <p v-else>You are ready! Please wait for the game to start.</p>
        <v-row justify="center" class="no-gutters">
          <v-btn
            @click="readyUp"
            :disabled="sentReadyUp || waitingRoom.isReady"
            large
            color="success"
          >
            Ready
          </v-btn>
        </v-row>
        <v-row justify="center" class="no-gutters">
          <v-scale-transition>
            <v-icon v-if="waitingRoom.isReady" color="success" large>
              mdi-check
            </v-icon>
          </v-scale-transition>
        </v-row>
      </div>
      <div
        v-show="waitingRoom.state === 'waiting-room'"
        class="pa-4"
        key="not-ready"
      >
        <v-alert
          v-if="waitingRoom.notChosenForGroup"
          border="left"
          colored-border
          color="orange accent-4"
          elevation="2"
          class="my-4"
        >
          You weren't chosen for this group, but will be prioritized for the
          next group.
        </v-alert>
        <v-alert
          v-else-if="waitingRoom.notEnoughReadyForGroup"
          border="left"
          colored-border
          color="orange accent-4"
          elevation="2"
          class="my-4"
        >
          Not enough participants have indicated they are ready. Please wait...
        </v-alert>
        <v-alert
          v-else-if="waitingRoom.readyUpFailures > 0"
          type="error"
          outlined
          elevation="2"
          class="my-4"
        >
          You failed to indicate you were ready. Too many failures will remove
          you from the experiment.
        </v-alert>
        <h3 class="my-4">
          The game will start once another participant has reached this point.
          Please wait...
        </h3>

        <v-progress-linear
          :value="waitingRoom.progress * 100"
          :buffer-value="waitingRoom.progress * 100"
          :height="20"
          color="primary"
          stream
        />
        <h2 class="my-4">
          {{ Math.floor(waitingRoom.progress * 100) }}% of participants are
          ready
        </h2>
        <v-row class="no-gutters justify-center">
          <v-btn
            v-if="enabledNotifications || !triedToEnableNotifications"
            @click="enableNotifications"
            :disabled="enabledNotifications"
          >
            {{
              enabledNotifications
                ? 'Notifications enabled'
                : 'Enable notifications'
            }}
          </v-btn>
          <v-btn
            v-if="enabledNotifications && !successfulTest"
            @click="testNotification"
          >
            Send test notification
          </v-btn>
        </v-row>
      </div>
      <div v-show="waitingRoom.state === 'group-start'" key="group-start">
        <h2 class="my-4">The game will start shortly!</h2>
      </div>
    </v-slide-x-transition>
    <p v-if="props.debug">
      {{ waitingRoom }}
    </p>
    <v-snackbar
      :value="triedToEnableNotifications && !enabledNotifications"
      color="error"
      timeout="5000"
    >
      Unable to enable notifications. Please wait on this page for the game to
      start.
    </v-snackbar>
  </div>
</template>

<style lang="scss" scoped>
.waiting-room {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
</style>
