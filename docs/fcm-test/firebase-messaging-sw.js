// FCM 백그라운드 수신용 서비스워커.
// index.html 의 firebaseConfig 와 동일한 값으로 채운다.
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyBkwTKC0-0JwA_-g73S_hzGXZnVpFhhDhI",
  authDomain: "thingo-8eee1.firebaseapp.com",
  projectId: "thingo-8eee1",
  storageBucket: "thingo-8eee1.firebasestorage.app",
  messagingSenderId: "650247693575",
  appId: "1:650247693575:web:fd7989e00989aea7b191a2"
});

const messaging = firebase.messaging();

// 페이지가 닫혀있거나 백그라운드일 때 수신
messaging.onBackgroundMessage((payload) => {
  const n = payload.notification || {};
  self.registration.showNotification(n.title || '알림', {
    body: n.body || '',
    data: payload.data || {}
  });
});
