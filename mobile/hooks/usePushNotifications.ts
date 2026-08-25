import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { router } from 'expo-router';
import { api } from '../lib/api';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export function usePushNotifications() {
  useEffect(() => {
    registerForPushNotifications();

    // Handle tap on notification when app is foregrounded or opened from background
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      const deepLink = response.notification.request.content.data?.deepLink as string | undefined;
      if (deepLink) {
        // Strip the scheme: zbs://signals/123 → /signals/123
        const path = deepLink.replace('zbs:/', '');
        router.push(path as never);
      }
    });

    return () => sub.remove();
  }, []);
}

async function registerForPushNotifications(): Promise<void> {
  if (Platform.OS === 'web') return;

  // Android requires a notification channel to be created before tokens are fetched
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Default',
      importance: Notifications.AndroidImportance.MAX,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#FF231F7C',
    });
  }

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;

  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  if (finalStatus !== 'granted') return;

  try {
    // Use the native device token (FCM on Android, APNs on iOS) for the backend
    const { data: nativeToken } = await Notifications.getDevicePushTokenAsync();
    const platform = Platform.OS === 'ios' ? 'APNS' : 'FCM';
    await api.post('/api/users/me/push-token', { token: nativeToken, platform });
  } catch (e) {
    console.warn('Push token registration failed:', e);
  }
}
