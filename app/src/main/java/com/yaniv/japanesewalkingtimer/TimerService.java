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
    private long lastPhaseStartTime = 0;
    private final int intervalDuration = 180; // 3 minutes in seconds
    private final int sessionDuration = 1800; // 30 minutes in seconds
    private final int totalSets = 5;
    private final int alertDurationMs = 3500; // 3.5 seconds
    
    // State machine
    private boolean isFastPhase = true; // Start with fast phase
    private int currentSet = 1;
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
            Log.d(TAG, "WakeLock acquired successfully");
        } else {
            Log.e(TAG, "Failed to acquire PowerManager");
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
                Log.d(TAG, "WakeLock released");
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_ALARM.equals(intent.getAction())) {
            Log.d(TAG, "ALARM action received - AlarmManager triggered interval alarm");
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
            
            // Initialize state machine
            sessionStartTime = System.currentTimeMillis();
            lastPhaseStartTime = sessionStartTime;
            isFastPhase = true;
            currentSet = 1;
            isSessionActive = false;
            isAlertPlaying = false;
            
            Log.d(TAG, "State machine initialized - Session start time: " + sessionStartTime);
            
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(40 * 60 * 1000L);
                Log.d(TAG, "WakeLock acquired for 40 minutes");
            }

            startForegroundNotification();
            startTimerTask();
            scheduleNextAlarm();
            
            isSessionActive = true;
            Log.d(TAG, "SESSION STARTED - Fast Phase, Set " + currentSet + ", Total duration: " + sessionDuration + "s");
        }

        return START_STICKY;
    }

    private void startTimerTask() {
        stopTimer();
        timer = new Timer();
        Log.d(TAG, "Timer task started - Beginning drift-free timing monitoring");
        
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isSessionActive) return;
                
                long currentTime = System.currentTimeMillis();
                long totalElapsed = (currentTime - sessionStartTime) / 1000;
                long phaseElapsed = (currentTime - lastPhaseStartTime) / 1000;
                
                // Log every 30 seconds for monitoring
                if (totalElapsed % 30 == 0) {
                    Log.d(TAG, "Timer update - Total: " + totalElapsed + "s, Phase: " + phaseElapsed + "s, " + 
                          (isFastPhase ? "Fast" : "Slow") + " Phase, Set " + currentSet);
                }
                
                // Update notification only every 60 seconds to prevent heartbeat beeping
                if (totalElapsed % 60 == 0) {
                    updateNotification();
                }
                
                // Check for phase boundary (every 3 minutes)
                if (phaseElapsed >= intervalDuration) {
                    Log.d(TAG, "PHASE BOUNDARY DETECTED - " + intervalDuration + "s elapsed in current phase");
                    handlePhaseTransition();
                }
                
                // Check for session completion (30 minutes)
                if (totalElapsed >= sessionDuration) {
                    Log.d(TAG, "SESSION COMPLETED - Total " + totalElapsed + "s elapsed");
                    triggerReliableAlarm(6);
                    stopTimer();
                    isSessionActive = false;
                    Log.d(TAG, "Session state set to inactive");
                }
            }
        }, 1000, 1000);
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
        long phaseElapsed = (currentTime - lastPhaseStartTime) / 1000;
        
        Log.d(TAG, "PHASE TRANSITION STARTED - Previous phase lasted: " + phaseElapsed + "s");
        
        // Update phase state
        boolean wasFastPhase = isFastPhase;
        isFastPhase = !isFastPhase;
        lastPhaseStartTime = currentTime;
        
        // Increment set after slow phase completes (every 6 minutes)
        if (!isFastPhase) {
            currentSet++;
            Log.d(TAG, "SET INCREMENTED - Now on Set " + currentSet + " of " + totalSets);
        }
        
        Log.d(TAG, "PHASE SWITCHED: " + (wasFastPhase ? "Fast" : "Slow") + " → " + 
              (isFastPhase ? "Fast" : "Slow") + " Phase, Set " + currentSet);
        
        // Trigger haptic/audio alert
        triggerReliableAlarm(4);
        
        // Schedule next alarm
        scheduleNextAlarm();
        
        Log.d(TAG, "PHASE TRANSITION COMPLETED - Next alarm scheduled");
    }

    private void scheduleNextAlarm() {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(ACTION_ALARM);
        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long triggerAt = SystemClock.elapsedRealtime() + (intervalDuration * 1000L);
            
            // Check if we can use exact alarms to avoid crash
            boolean canScheduleExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canScheduleExact = am.canScheduleExactAlarms();
                Log.d(TAG, "Android 12+ exact alarm permission check: " + canScheduleExact);
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    Log.d(TAG, "EXACT ALARM SCHEDULED - setExactAndAllowWhileIdle in " + intervalDuration + "s");
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    Log.d(TAG, "EXACT ALARM SCHEDULED - setExact in " + intervalDuration + "s");
                }
            } else {
                // Fallback to non-exact alarm if permission is missing
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                Log.w(TAG, "FALLBACK ALARM SCHEDULED - setAndAllowWhileIdle in " + intervalDuration + "s (exact alarms not available)");
            }
            
            long scheduledTime = System.currentTimeMillis() + (intervalDuration * 1000L);
            Log.d(TAG, "ALARM DETAILS - Scheduled for: " + new java.util.Date(scheduledTime) + 
                  ", Current phase: " + (isFastPhase ? "Fast" : "Slow") + ", Set: " + currentSet);
        } else {
            Log.e(TAG, "FAILED TO SCHEDULE ALARM - AlarmManager is null");
        }
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
        Log.d(TAG, "FOREGROUND NOTIFICATION STARTED - Beginning persistent notification");
        Notification n = createNotification("Session Active");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - API 34+ with SPECIAL_USE type");
        } else {
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "FOREGROUND SERVICE STARTED - Standard method");
        }
    }

    private void updateNotification() {
        if (!isSessionActive) return;
        
        long currentTime = System.currentTimeMillis();
        long totalElapsed = (currentTime - sessionStartTime) / 1000;
        long phaseElapsed = (currentTime - lastPhaseStartTime) / 1000;
        int remaining = sessionDuration - (int)totalElapsed;
        
        String phase = isFastPhase ? "Fast" : "Slow";
        String text = String.format("%s Phase - Set %d/%d | %d:%02d remaining", 
            phase, currentSet, totalSets, remaining / 60, remaining % 60);
        
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification(text));
            Log.d(TAG, "NOTIFICATION UPDATED - " + text);
        } else {
            Log.e(TAG, "NOTIFICATION UPDATE FAILED - NotificationManager is null");
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
