package com.yaniv.japanesewalkingtimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.app.AlarmManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class TimerService extends Service {
    private static final String TAG = "JapaneseTimer";
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // Simple variables
    private int currentInterval = 0; // Max is 10
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        createNotificationChannel();
        
        // Initialize WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JapaneseTimer:WakeLock");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");
        
        // Handle increment action from AlarmReceiver
        if (intent != null && "INCREMENT_INTERVAL".equals(intent.getAction())) {
            currentInterval++;
            Log.d(TAG, "INTERVAL INCREMENTED - Now at: " + currentInterval + " of 10");
            
            // Check if session is complete
            if (currentInterval >= 10) {
                Log.d(TAG, "SESSION COMPLETE - 10 intervals finished");
                stopService();
                return START_NOT_STICKY;
            }
            
            // Schedule next alarm
            scheduleNextAlarm();
            return START_STICKY;
        }
        
        // Initial start - begin session
        startForegroundNotification();
        
        // Acquire WakeLock for 35 minutes
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(35 * 60 * 1000L); // 35 minutes
            Log.d(TAG, "WAKELOCK ACQUIRED - 35 minutes for 30-minute session");
        }
        
        // Reset counter and start
        currentInterval = 0;
        scheduleNextAlarm();
        
        return START_STICKY;
    }
    
    private void scheduleNextAlarm() {
        Log.d(TAG, "SCHEDULING NEXT ALARM - Current interval: " + currentInterval);
        
        // Check if session is complete
        if (currentInterval >= 10) {
            Log.d(TAG, "SESSION COMPLETE - Stopping service");
            stopService();
            return;
        }
        
        // Create Intent for AlarmReceiver
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Get AlarmManager
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "CRITICAL FAILURE - AlarmManager is null");
            stopService();
            return;
        }
        
        // CRITICAL LINE: Exactly 180000L (3 minutes)
        long triggerTime = System.currentTimeMillis() + 180000L;
        
        // Schedule exact alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        
        Log.d(TAG, "ALARM SCHEDULED - Exactly 180000L (3 minutes) from now");
        Log.d(TAG, "Next alarm will fire at: " + new java.util.Date(triggerTime));
    }
    
            currentMediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "ALARM AUDIO COMPLETED - MediaPlayer finished naturally");
                mp.release();
                currentMediaPlayer = null;
            });
            
        } catch (Exception e) {
            Log.e(TAG, "ALARM AUDIO FAILED - " + e.getMessage(), e);
            currentMediaPlayer = null;
        }
    }

    private void startForegroundNotification() {
        Log.d(TAG, "FOREGROUND NOTIFICATION STARTED - Static notification");
        Notification n = createNotification("Japanese Walking Timer Active");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - API 34+ with SPECIAL_USE type");
        } else {
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - Standard method");
        }
    }

    
    private Notification createNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Japanese Walking Timer")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
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
            NotificationManager nm = getSystemService(NotificationManager.class);
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

    @Override
    public void onDestroy() {
        Log.d(TAG, "SERVICE DESTROYED - Cleaning up resources");
        stopTimer();
        cancelAlarm();
        stopAlert();
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
