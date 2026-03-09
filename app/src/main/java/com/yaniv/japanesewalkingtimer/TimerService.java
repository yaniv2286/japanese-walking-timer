package com.yaniv.japanesewalkingtimer;

import android.app.AlarmManager;
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
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.yaniv.japanesewalkingtimer.R;
import java.util.Timer;
import java.util.TimerTask;

public class TimerService extends Service {
    private static final String TAG = "JapaneseTimer";
    private static final String CHANNEL_ID = "TimerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_ALARM = "com.yaniv.japanesewalkingtimer.ALARM";

    private Timer timer;
    private MediaPlayer currentMediaPlayer;
    private long sessionStartTime = 0;
    private final long INTERVAL_MS = 180000; // EXACT 3 minutes in milliseconds - NO MODIFICATION
    private final long SESSION_MS = 1800000; // EXACT 30 minutes in milliseconds
    private final long ALERT_DURATION_MS = 4000; // EXACT 4 seconds for alarm/vibration
    
    // State machine
    private int currentInterval = 0; // Start at 0, will increment to 1 on first alarm
    private boolean isSessionActive = false;
    private boolean isAlertPlaying = false;
    
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created - Japanese Walking Timer initializing");
        createNotificationChannel();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JapaneseWalkingTimer:TimerWakelock");
            Log.d(TAG, "WakeLock initialized successfully - will acquire when timer starts");
        } else {
            Log.e(TAG, "Failed to initialize PowerManager");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called - Action: " + (intent != null ? intent.getAction() : "null"));
        
        if (intent != null && "STOP".equals(intent.getAction())) {
            Log.d(TAG, "STOP action received - terminating session");
            cancelAlarm();
            stopTimer();
            stopAlert();
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "CPU WAKELOCK RELEASED - User stopped timer, CPU can now sleep");
            } else if (wakeLock != null) {
                Log.d(TAG, "CPU WAKELOCK NOT HELD - No wakeLock to release");
            } else {
                Log.w(TAG, "CPU WAKELOCK NULL - WakeLock was never initialized");
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_ALARM.equals(intent.getAction())) {
            Log.d(TAG, "ALARM action received - AlarmManager triggered interval alarm");
            
            // Verify WakeLock is still held
            if (wakeLock != null && wakeLock.isHeld()) {
                Log.d(TAG, "WAKELOCK VERIFIED - CPU wake lock still active during alarm trigger");
            } else if (wakeLock != null) {
                Log.e(TAG, "WAKELOCK LOST - WakeLock not held during alarm trigger, reacquiring");
                wakeLock.acquire(40 * 60 * 1000L);
            } else {
                Log.e(TAG, "WAKELOCK NULL - WakeLock is null during alarm trigger");
            }
            
            if (isSessionActive) {
                handlePhaseTransition();
            } else {
                Log.w(TAG, "Alarm triggered but session is not active");
            }
            return START_STICKY;
        }

        // Check if this is a fresh start (not from the alarm action)
        if (intent != null && intent.getAction() == null) {
            Log.d(TAG, "FRESH START - Initializing new 30-minute walking session");
            
            // Initialize absolute timing state machine
            sessionStartTime = System.currentTimeMillis();
            currentInterval = 0; // Start at 0, will increment to 1 on first alarm
            isSessionActive = false;
            isAlertPlaying = false;
            
            Log.d(TAG, "ABSOLUTE TIMING INITIALIZED - Session start: " + sessionStartTime + 
                  ", Next alarm target: " + (sessionStartTime + INTERVAL_MS));
            
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(40 * 60 * 1000L);
                Log.d(TAG, "CPU WAKELOCK ACQUIRED - Partial wake lock for 40 minutes, keeping CPU awake during timer session");
            } else if (wakeLock != null && wakeLock.isHeld()) {
                Log.d(TAG, "CPU WAKELOCK ALREADY HELD - WakeLock already active");
            } else {
                Log.e(TAG, "CPU WAKELOCK FAILED - WakeLock is null, timer may not work in deep sleep");
            }

            startForegroundNotification();
            startTimerTask();
            scheduleNextAlarm();
            
            isSessionActive = true;
            Log.d(TAG, "SESSION STARTED - 10 intervals of 3 minutes each (30 minutes total)");
        }

        return START_STICKY;
    }

    private void startTimerTask() {
        // No per-second timer needed - rely solely on AlarmManager for precision
        Log.d(TAG, "Timer task simplified - Using AlarmManager only for 3-minute intervals");
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
            Log.d(TAG, "Timer task stopped");
        }
    }

    private void handlePhaseTransition() {
        long currentTime = System.currentTimeMillis();
        long elapsedFromStart = currentTime - sessionStartTime;
        long expectedIntervalTime = currentInterval * INTERVAL_MS;
        
        Log.d(TAG, "ABSOLUTE INTERVAL TRIGGERED - Elapsed: " + (elapsedFromStart / 1000) + "s, Expected: " + (expectedIntervalTime / 1000) + "s");
        
        // Increment interval counter
        currentInterval++;
        
        Log.d(TAG, "INTERVAL COUNTER - Now at " + currentInterval + " (10 intervals = 30 minutes total)");
        
        // Check for session completion (10 intervals = 30 minutes)
        if (currentInterval >= 10) {
            Log.d(TAG, "SESSION COMPLETION - Interval " + currentInterval + " of 10 reached, total elapsed: " + (elapsedFromStart / 1000) + "s");
            triggerReliableAlarm(6); // Final unique sound
            
            // Stop service after EXACT 4-second alert duration
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Release WakeLock before stopping service
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                    Log.d(TAG, "WAKELOCK RELEASED - 30-minute session completed");
                }
                stopForeground(true);
                stopSelf();
                Log.d(TAG, "SERVICE STOPPED - 30-minute session completed at interval " + currentInterval);
            }, ALERT_DURATION_MS); // EXACT 4 seconds
            return;
        }
        
        // Trigger standard 3-minute alert
        Log.d(TAG, "ALERT TRIGGERED - Interval " + currentInterval + " alert sequence starting");
        triggerReliableAlarm(4);
        
        // Schedule next alarm with absolute timing
        Log.d(TAG, "SCHEDULING NEXT - Interval " + (currentInterval + 1));
        scheduleNextAlarm();
        
        Log.d(TAG, "INTERVAL " + currentInterval + " COMPLETE - Next alarm scheduled with absolute timing");
    }

    private void scheduleNextAlarm() {
        // FAIL FAST - Ensure AlarmManager is available
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "CRITICAL FAILURE - AlarmManager is null, cannot schedule alarm");
            stopForeground(true);
            stopSelf();
            return;
        }
        
        // Clear any previous ghost alarms
        Intent cancelIntent = new Intent(this, TimerService.class);
        cancelIntent.setAction(ACTION_ALARM);
        PendingIntent cancelPi = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(cancelPi);
        Log.d(TAG, "GHOST ALARMS CLEARED - Previous PendingIntent cancelled");
        
        // Calculate ABSOLUTE next alarm time
        long nextInterval = currentInterval + 1;
        long absoluteTargetTime = sessionStartTime + (nextInterval * INTERVAL_MS);
        long currentTime = System.currentTimeMillis();
        long timeFromNow = absoluteTargetTime - currentTime;
        
        // Create new PendingIntent for this specific alarm
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(ACTION_ALARM);
        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Schedule with absolute timing
        long triggerAt = SystemClock.elapsedRealtime() + timeFromNow;
        
        // Check exact alarm permission
        boolean canScheduleExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = am.canScheduleExactAlarms();
            Log.d(TAG, "Android 12+ exact alarm permission: " + canScheduleExact);
        }

        if (canScheduleExact) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                Log.d(TAG, "ABSOLUTE ALARM SCHEDULED - setExactAndAllowWhileIdle");
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                Log.d(TAG, "ABSOLUTE ALARM SCHEDULED - setExact");
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            Log.w(TAG, "FALLBACK ALARM - setAndAllowWhileIdle (may be less precise)");
        }
        
        Log.d(TAG, "NEXT ALARM SET FOR: " + timeFromNow + "ms from now");
        Log.d(TAG, "ABSOLUTE TARGET - Interval " + nextInterval + " at: " + new java.util.Date(absoluteTargetTime) + 
              " (Session start: " + new java.util.Date(sessionStartTime) + ")");
    }

    private void cancelAlarm() {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(ACTION_ALARM);
        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pi);
            Log.d(TAG, "ALARM CANCELLED - All pending alarms cleared");
        } else {
            Log.e(TAG, "FAILED TO CANCEL ALARM - AlarmManager is null");
        }
    }

    private void playAlertSequence(int repeats) {
        triggerReliableAlarm(repeats);
    }

    private void triggerReliableAlarm(int count) {
        if (isAlertPlaying) {
            Log.w(TAG, "ALERT ALREADY PLAYING - Skipping new alert request");
            return;
        }
        
        Log.d(TAG, "ALERT STARTED - " + count + " repetitions, duration: " + alertDurationMs + "ms");
        isAlertPlaying = true;
        
        new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    Log.d(TAG, "ALERT REPETITION " + (i + 1) + " of " + count);
                    triggerSingleFeedback();
                    if (i < count - 1) {
                        Thread.sleep(1500);
                    }
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "ALERT INTERRUPTED - " + e.getMessage());
            } finally {
                // Auto-stop after alert duration
                try {
                    Thread.sleep(alertDurationMs);
                } catch (InterruptedException e) {
                    // Ignore
                }
                stopAlert();
                Log.d(TAG, "ALERT COMPLETED - Auto-stopped after " + alertDurationMs + "ms");
            }
        }).start();
    }

    private void stopAlert() {
        if (currentMediaPlayer != null) {
            try {
                currentMediaPlayer.stop();
                currentMediaPlayer.release();
                Log.d(TAG, "ALERT AUDIO STOPPED - MediaPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer: " + e.getMessage());
            } finally {
                currentMediaPlayer = null;
            }
        }
        isAlertPlaying = false;
    }

    private void triggerSingleFeedback() {
        Log.d(TAG, "SINGLE FEEDBACK TRIGGERED - Starting vibration and audio");
        
        // Set maximum alarm volume
        try {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
            Log.d(TAG, "ALARM VOLUME SET - Maximum volume: " + maxVolume);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set alarm volume: " + e.getMessage());
        }

        // Trigger vibration
        if (vibrator != null) {
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE), aa);
                Log.d(TAG, "VIBRATION STARTED - 800ms with API 26+ VibrationEffect");
            } else {
                vibrator.vibrate(800);
                Log.d(TAG, "VIBRATION STARTED - 800ms with legacy API");
            }
        } else {
            Log.w(TAG, "VIBRATION FAILED - Vibrator is null");
        }

        // Play audio alert
        try {
            // Stop any existing MediaPlayer
            if (currentMediaPlayer != null) {
                currentMediaPlayer.release();
                currentMediaPlayer = null;
            }
            
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Log.d(TAG, "FALLBACK AUDIO - Using notification ringtone");
            } else {
                Log.d(TAG, "ALARM AUDIO - Using alarm ringtone");
            }
            
            currentMediaPlayer = new MediaPlayer();
            currentMediaPlayer.setDataSource(this, uri);
            currentMediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            currentMediaPlayer.prepare();
            currentMediaPlayer.start();
            
            Log.d(TAG, "ALARM AUDIO STARTED - Playing ringtone");
            
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
                .setSmallIcon(R.drawable.ic_launcher)
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
