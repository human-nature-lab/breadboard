import { ref, onMounted, onBeforeUnmount } from 'vue'

export function useNotificationPermission () {
  const notificationPermissionState = ref<'prompt' | 'granted' | 'denied'>('prompt')

  let permissionObj: PermissionStatus | null = null
  let cb: any | null = null
  onBeforeUnmount(() => {
    if (permissionObj && cb) {
      permissionObj.removeEventListener('change', cb)
    }
  })
  onMounted(() => {
    if (!('permissions' in navigator)) {
      return
    }
    navigator.permissions.query({ name: 'notifications' }).then(permission => {
      function handleChange () {
        console.log('notifications permission change', permission.state)
        notificationPermissionState.value = permission.state
      }
      handleChange()
      permission.addEventListener('change', handleChange)
      cb = handleChange
      permissionObj = permission
    })
  })

  return notificationPermissionState
}

export function notificationsEnabled () {
  if (!('Notification' in window)) {
    return false
  }
  return Notification.permission === 'granted'
}

export async function requestNotificationPermission () {
  if (!('Notification' in window)) {
    console.log('Notification API not supported')
    return false
  }
  if (!notificationsEnabled()) {
    const permission = await Notification.requestPermission()
    if (permission === 'granted') {
      console.log('Notification permission granted')
      return true
    } else {
      console.log('Notification permission not granted', permission)
      return false
    }
  }
  return true
}

export async function notify (title: string, options: NotificationOptions) {
  options.requireInteraction = true
  if (!(await requestNotificationPermission())) {
    return
  }
  const notification = new Notification(title, options)
  notification.onclick = () => {
    window.focus()
  }
  notification.onerror = () => {
    console.error('Notification error')
  }
  notification.onshow = () => {
    console.log('Notification shown')
  }
  return notification
}
