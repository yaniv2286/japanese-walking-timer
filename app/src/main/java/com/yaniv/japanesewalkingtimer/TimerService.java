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
    private Thread backgroundThread;
    private volatile boolean serviceRunning = true;
    private volatile boolean isRunning = true; // Kill runaway loop
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
        
        // Acquire aggressive WakeLock (no timeout)
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(); // NO TIMEOUT - manual release only
            Log.d(TAG, "AGGRESSIVE WAKELOCK ACQUIRED - No timeout, manual release only");
        }
        
        // Reset counter and start immortal loop
        currentInterval = 0;
        startImmortalLoop();
        
        return START_STICKY;
    }
    
    private void startImmortalLoop() {
        Log.d(TAG, "STARTING NUCLEAR BACKGROUND THREAD - Aggressive WakeLock active");
        
        serviceRunning = true;
        
        backgroundThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            Log.d(TAG, "NUCLEAR THREAD STARTED - URGENT_AUDIO priority, WakeLock active");
            
            while (currentInterval < 10 && serviceRunning && isRunning) {
                try {
                    Thread.sleep(10000); // 10-second test interval
                    
                    if (!serviceRunning || !isRunning) break; // Check if service was stopped
                    
                    currentInterval++;
                    Log.d(TAG, "NUCLEAR THREAD TICK - Interval: " + currentInterval + " of 10");
                    
                    // HARD EXIT: Stop after exactly 10 intervals
                    if (currentInterval >= 10) {
                        isRunning = false;
                        stopSelf();
                        break;
                    }
                    
                    // Fire UI update and Sound/Vibration on main thread
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        triggerAlert(TimerService.this);
                        
                        // Update notification chronometer for next interval
                        long nextTriggerTime = System.currentTimeMillis() + 10000L;
                        updateNotificationChronometer(nextTriggerTime);
                    });
                    
                } catch (InterruptedException e) {
                    Log.d(TAG, "NUCLEAR THREAD INTERRUPTED - Breaking loop");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "NUCLEAR THREAD ERROR: " + e.getMessage());
                }
            }
            
            Log.d(TAG, "NUCLEAR THREAD FINISHED - Stopping service");
            stopSelf();
        });
        
        backgroundThread.start();
        Log.d(TAG, "NUCLEAR THREAD LAUNCHED - Raw background thread with WakeLock");
    }

    private void startForegroundNotification() {
        Log.d(TAG, "FOREGROUND NOTIFICATION STARTED - With chronometer");
        Notification n = createNotification("Japanese Walking Timer", "Cycle 1/5", System.currentTimeMillis() + 10000L);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - API 34+ with SPECIAL_USE type");
        } else {
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - Standard method");
        }
    }

    private Notification createNotification(String title, String content, long triggerTime) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
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
        
        // Vibrate for exactly 3000ms (self-killing)
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(3000);
            }
            Log.d(TAG, "VIBRATION STARTED - 3000ms self-killing");
        }
        
        // Play notification sound (self-killing, no continuous bug)
        try {
            currentMediaPlayer = new android.media.MediaPlayer();
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // CRITICAL: Bypasses silent mode
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            currentMediaPlayer.setAudioAttributes(attributes);
            currentMediaPlayer.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            currentMediaPlayer.prepare();
            currentMediaPlayer.setVolume(0.5f, 0.5f); // Gentle 50% volume
            currentMediaPlayer.start();
            
            Log.d(TAG, "AUDIO STARTED - Notification sound playing");
            
            // Self-killing after 3000ms
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (currentMediaPlayer != null) {
                    try {
                        if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                        currentMediaPlayer.release();
                    } catch (Exception e) { /* ignore */ }
                    currentMediaPlayer = null;
                    Log.d(TAG, "AUDIO KILLED - 3000ms timeout");
                }
            }, 3000);
            
        } catch (Exception e) {
            Log.e(TAG, "AUDIO ERROR - Failed to play notification sound: " + e.getMessage());
            currentMediaPlayer = null;
        }
    }

    private void updateNotificationChronometer(long triggerTime) {
        // Cycle header: Show current cycle (1-5)
        int currentCycle = (currentInterval / 2) + 1;
        String cycleTitle = "Cycle " + currentCycle + "/5";
        
        // Dynamic text showing current set
        String progressText = "Set " + (currentInterval + 1) + "/10";
        
        // Create updated notification with live countdown
        Notification updatedNotification = createNotification(cycleTitle, progressText, triggerTime);
        
        // Push notification update once per interval
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, updatedNotification);
            Log.d(TAG, "NOTIFICATION UPDATED - " + cycleTitle + " - " + progressText + " - Live countdown active");
        }
    }

    // No AlarmManager - immortal loop handles timing

    @Override
    public void onDestroy() {
        Log.d(TAG, "SERVICE DESTROYED - Nuclear cleanup");
        
        // Kill runaway loop
        isRunning = false;
        serviceRunning = false;
        
        // Interrupt nuclear thread
        if (backgroundThread != null) {
            backgroundThread.interrupt();
            Log.d(TAG, "NUCLEAR THREAD INTERRUPTED");
        }
        
        // Stop any playing audio
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
                currentMediaPlayer.release();
            } catch (Exception e) { /* ignore */ }
            currentMediaPlayer = null;
        }
        
        // Release aggressive WakeLock so phone can sleep
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "AGGRESSIVE WAKELOCK RELEASED - Phone can sleep now");
        }
        
        super.onDestroy();
        Log.d(TAG, "NUCLEAR SERVICE DESTRUCTION COMPLETED - All resources cleaned");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
