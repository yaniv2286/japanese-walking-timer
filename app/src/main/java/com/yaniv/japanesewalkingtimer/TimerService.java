package com.yaniv.japanesewalkingtimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TimerService extends Service {
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    
    private int totalElapsedSeconds = 0;
    private int intervalDuration = 180; // 3 minutes
    private int sessionDuration = 1800; // 30 minutes
    
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JapaneseWalkingTimer:TimerWakelock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        
        if ("STOP".equals(action)) {
            stopTimer();
            if (wakeLock.isHeld()) wakeLock.release();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        totalElapsedSeconds = 0;
        startForegroundNotification();
        startTimer();

        return START_STICKY;
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                totalElapsedSeconds++;
                
                if (totalElapsedSeconds >= sessionDuration) {
                    onSessionComplete();
                    stopTimer();
                    return;
                }

                if (totalElapsedSeconds % intervalDuration == 0) {
                    onPhaseChange();
                }

                updateNotification();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postAtTime(timerRunnable, System.currentTimeMillis() + 1000);
        handler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    private void onPhaseChange() {
        playAlert(3);
    }

    private void onSessionComplete() {
        playAlert(5);
    }

    private void playAlert(int repeats) {
        new Thread(() -> {
            for (int i = 0; i < repeats; i++) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 300);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE), 
                        new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    vibrator.vibrate(400);
                }
                
                try { Thread.sleep(600); } catch (InterruptedException e) {}
            }
        }).start();
    }

    private void startForegroundNotification() {
        Notification notification = createNotification("Timer started");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        int minutes = (sessionDuration - totalElapsedSeconds) / 60;
        int seconds = (sessionDuration - totalElapsedSeconds) % 60;
        String timeStr = String.format("%d:%02d remaining", minutes, seconds);
        
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(timeStr));
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Japanese Walking Timer")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Timer Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            serviceChannel.enableVibration(true);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopTimer();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }
}
