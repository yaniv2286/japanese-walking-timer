package com.yaniv.japanesewalkingtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.media.AudioAttributes;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "JapaneseTimer";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ALARM RECEIVER TRIGGERED - Interval alarm fired");
        
        // Get the current interval from the intent or service
        Intent serviceIntent = new Intent(context, TimerService.class);
        serviceIntent.setAction("INCREMENT_INTERVAL");
        context.startService(serviceIntent);
        
        // Trigger vibration and sound for exactly 4000ms
        triggerAlert(context);
    }
    
    private void triggerAlert(Context context) {
        // Vibrate for 4000ms
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(4000, VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(4000);
            }
            Log.d(TAG, "VIBRATION STARTED - 4000ms duration");
        }
        
        // Play ringtone for 4000ms
        try {
            android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            mediaPlayer.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
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
}
