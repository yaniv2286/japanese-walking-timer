package com.yaniv.japanesewalkingtimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

public class TimerService extends Service {
    private static final String TAG = "JapaneseTimer";
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // Simple variables
    private int currentInterval = 0; // Max is 10
    private PowerManager.WakeLock wakeLock;
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> scheduledTask;
    private android.media.MediaPlayer currentMediaPlayer; // Prevent overlap
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        createNotificationChannel();
        
        // Initialize WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JapaneseTimer:WakeLock");
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // No AlarmManager - immortal loop handles everything
        
        // Initial start - begin session
        startForegroundNotification();
        
        // Acquire WakeLock for 35 minutes
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(35 * 60 * 1000L); // 35 minutes
            Log.d(TAG, "WAKELOCK ACQUIRED - 35 minutes for 30-minute session");
        }
        
        // Reset counter and start immortal loop
        currentInterval = 0;
        startImmortalLoop();
        
        return START_STICKY;
    }
    
    private void startImmortalLoop() {
        Log.d(TAG, "STARTING IMMORTAL EXECUTOR LOOP - WakeLock keeps CPU alive");
        
        // Initialize executor service
        executorService = Executors.newSingleThreadScheduledExecutor();
        
        // Start the immortal loop - every 10 seconds
        scheduledTask = executorService.scheduleAtFixedRate(new TimerTask(), 10, 10, TimeUnit.SECONDS);
        
        Log.d(TAG, "IMMORTAL LOOP STARTED - Ticks every 10 seconds");
    }
    
    private class TimerTask implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "IMMORTAL LOOP TICK - Interval: " + currentInterval);
            
            // Increment counter
            currentInterval++;
            Log.d(TAG, "INTERVAL INCREMENTED - Now at: " + currentInterval + " of 10");
            
            // Trigger vibration and sound
            triggerAlert(TimerService.this);
            
            // Update notification chronometer for next interval
            long nextTriggerTime = System.currentTimeMillis() + 10000L;
            updateNotificationChronometer(nextTriggerTime);
            
            // Check if session is complete
            if (currentInterval >= 10) {
                Log.d(TAG, "SESSION COMPLETE - 10 intervals finished");
                stopSelf();
            }
        }
    }

    private void startForegroundNotification() {
        Log.d(TAG, "FOREGROUND NOTIFICATION STARTED - With chronometer");
        Notification n = createNotification("Japanese Walking Timer Active", System.currentTimeMillis() + 10000L);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - API 34+ with SPECIAL_USE type");
        } else {
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - Standard method");
        }
    }

    private Notification createNotification(String text, long triggerTime) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Japanese Walking Timer")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setWhen(triggerTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(null)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Walking Timer", NotificationManager.IMPORTANCE_LOW);
            c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            c.enableVibration(false);
            c.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(c);
                Log.d(TAG, "NOTIFICATION CHANNEL CREATED - Low importance, silent for background updates");
            } else {
                Log.e(TAG, "FAILED TO CREATE NOTIFICATION CHANNEL - NotificationManager is null");
            }
        } else {
            Log.d(TAG, "NOTIFICATION CHANNEL NOT NEEDED - API < 26");
        }
    }

    private void triggerAlert(Context context) {
        // Prevent overlap - stop any existing audio
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                currentMediaPlayer.release();
            } catch (Exception e) { /* ignore */ }
            currentMediaPlayer = null;
        }
        
        // Vibrate for exactly 4000ms with kill switch
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            // Simple 4000ms one-shot vibration
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(4000, VibrationEffect.DEFAULT_AMPLITUDE);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
                vibrator.vibrate(effect, audioAttributes);
            } else {
                vibrator.vibrate(4000);
            }
            Log.d(TAG, "VIBRATION STARTED - 4000ms duration");
            
            // Cancel vibration after 4000ms (failsafe)
            new android.os.Handler().postDelayed(() -> {
                vibrator.cancel();
                Log.d(TAG, "VIBRATION CANCELLED - 4000ms timeout");
            }, 4000);
        }
        
        // Play ringtone for exactly 4000ms with kill switch
        try {
            currentMediaPlayer = new android.media.MediaPlayer();
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // CRITICAL: Bypasses silent mode
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            currentMediaPlayer.setAudioAttributes(attributes);
            currentMediaPlayer.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            currentMediaPlayer.prepare();
            currentMediaPlayer.setVolume(1.0f, 1.0f); // Force maximum volume
            currentMediaPlayer.start();
            
            Log.d(TAG, "AUDIO STARTED - Ringtone playing for 4000ms");
            
            // CRITICAL: 4-second kill switch
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (currentMediaPlayer != null) {
                    try {
                        if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                        currentMediaPlayer.release();
                    } catch (Exception e) { /* ignore */ }
                    currentMediaPlayer = null;
                    Log.d(TAG, "AUDIO KILLED - 4000ms timeout");
                }
            }, 4000);
            
        } catch (Exception e) {
            Log.e(TAG, "AUDIO ERROR - Failed to play ringtone: " + e.getMessage());
            currentMediaPlayer = null;
        }
    }

    private void updateNotificationChronometer(long triggerTime) {
        // Dynamic text showing current progress
        String progressText = "Set " + (currentInterval + 1) + "/10";
        
        // Create updated notification with chronometer
        Notification updatedNotification = createNotification(progressText, triggerTime);
        
        // Push notification update once per interval
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, updatedNotification);
            Log.d(TAG, "NOTIFICATION UPDATED - Chronometer set for interval " + (currentInterval + 1));
        }
    }

    // No AlarmManager - immortal loop handles timing

    @Override
    public void onDestroy() {
        Log.d(TAG, "SERVICE DESTROYED - Cleaning up resources");
        
        // Stop any playing audio
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                currentMediaPlayer.release();
            } catch (Exception e) { /* ignore */ }
            currentMediaPlayer = null;
        }
        
        // Shutdown immortal loop
        if (executorService != null) {
            executorService.shutdownNow();
            Log.d(TAG, "EXECUTOR SERVICE SHUTDOWN - Immortal loop stopped");
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released during service destruction");
        }
        super.onDestroy();
        Log.d(TAG, "SERVICE DESTRUCTION COMPLETED");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
