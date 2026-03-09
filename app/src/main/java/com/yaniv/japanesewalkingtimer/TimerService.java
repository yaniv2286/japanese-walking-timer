package com.yaniv.japanesewalkingtimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.app.AlarmManager;
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

        // Handle alarm trigger
        if (intent != null && "ALARM_TRIGGERED".equals(intent.getAction())) {
            currentInterval++;
            Log.d(TAG, "ALARM TRIGGERED - Now at: " + currentInterval + " of 10");
            
            // Trigger vibration and sound for exactly 4000ms
            triggerAlert(this);
            
            // Check if session is complete
            if (currentInterval >= 10) {
                Log.d(TAG, "SESSION COMPLETE - 10 intervals finished");
                stopSelf();
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
            stopSelf();
            return;
        }
        
        // Create Intent for Service
        Intent alarmIntent = new Intent(this, TimerService.class);
        alarmIntent.setAction("ALARM_TRIGGERED");
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, alarmIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Get AlarmManager
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "CRITICAL FAILURE - AlarmManager is null");
            stopSelf();
            return;
        }
        
        // CRITICAL LINE: Exactly 10000L (10 seconds for testing)
        long triggerTime = System.currentTimeMillis() + 10000L;
        
        // Schedule exact alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        
        Log.d(TAG, "ALARM SCHEDULED - Exactly 10000L (10 seconds for testing) from now");
        Log.d(TAG, "Next alarm will fire at: " + new java.util.Date(triggerTime));
        
        // Update notification with chronometer for new interval
        updateNotificationChronometer(triggerTime);
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
        // Vibrate with high-priority waveform pattern
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 1000, 500, 1000, 500, 1000}; // Wait 0ms, vibrate 1s, pause 0.5s, vibrate 1s, etc.
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
                vibrator.vibrate(effect, audioAttributes);
            } else {
                vibrator.vibrate(pattern, -1);
            }
            Log.d(TAG, "VIBRATION STARTED - Waveform pattern with high priority");
        }
        
        // Play ringtone for 4000ms
        try {
            android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            mediaPlayer.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            // Stop after exactly 4000ms
            mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.release();
                Log.d(TAG, "AUDIO COMPLETED - 4000ms duration finished");
            });
            
            // Failsafe stop after 4000ms
            new android.os.Handler().postDelayed(() -> {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    Log.d(TAG, "AUDIO FORCE STOPPED - 4000ms timeout");
                }
            }, 4000);
            
            Log.d(TAG, "AUDIO STARTED - Ringtone playing for 4000ms");
        } catch (Exception e) {
            Log.e(TAG, "AUDIO ERROR - Failed to play ringtone: " + e.getMessage());
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

    private void cancelAlarm() {
        Intent alarmIntent = new Intent(this, TimerService.class);
        alarmIntent.setAction("ALARM_TRIGGERED");
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, alarmIntent, 
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "ALARM CANCELLED");
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SERVICE DESTROYED - Cleaning up resources");
        cancelAlarm();
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
