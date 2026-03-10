class JapaneseWalkingTimer {
    constructor() {
        // DOM elements
        this.phaseIndicator = document.getElementById('phaseIndicator');
        this.phaseText = document.getElementById('phaseText');
        this.timeText = document.getElementById('timeText');
        this.cycleInfo = document.getElementById('cycleInfo');
        this.startStopBtn = document.getElementById('startStopBtn');
        this.resetBtn = document.getElementById('resetBtn');
        this.progressFill = document.getElementById('progressFill');
        this.elapsedTime = document.getElementById('elapsedTime');
        this.totalTime = document.getElementById('totalTime');

        // Timer state
        this.isRunning = false;
        this.currentPhase = 'ready'; // 'ready', 'fast', 'slow'
        this.currentCycle = 1;
        this.totalCycles = 5;
        this.intervalDuration = 10; // 10 seconds for testing
        this.sessionDuration = 100; // 10 intervals × 10 seconds = 100 seconds
        this.currentTime = this.intervalDuration;
        this.totalElapsed = 0;
        this.interval = null;
        this.startTime = null; // System time when start was clicked
        this.lastTickTime = null;
        this.endTime = null; // Absolute end time for session

        // Audio disabled - backend handles all sounds

        // Event listeners
        this.bindEvents();

        // Initial display
        this.updateDisplay();
    }

    // Audio removed - backend handles all sounds

    // Audio removed - backend handles all sounds

    vibrate() {
        if ('vibrate' in navigator) {
            try {
                navigator.vibrate([400, 200, 400, 200, 400]);
            } catch (e) {
                console.log('Vibration failed:', e);
            }
        }
    }

    bindEvents() {
        this.startStopBtn.addEventListener('click', () => this.toggleTimer());
        this.resetBtn.addEventListener('click', () => this.resetTimer());

        // Handle app visibility changes (background/foreground)
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                this.handleAppResume();
            }
        });
    }

    handleAppResume() {
        if (!this.isRunning || !this.startTime) return;

        // Calculate actual elapsed time since we started
        const now = Date.now();
        const actualTotalElapsed = Math.floor((now - this.startTime) / 1000);
        
        if (actualTotalElapsed > this.totalElapsed) {
            // Catch up logic
            const previousTotalElapsed = this.totalElapsed;
            this.totalElapsed = actualTotalElapsed;
            
            // Check if we missed any phase changes while in background
            this.checkMissedPhaseChanges(previousTotalElapsed, actualTotalElapsed);
        }
        
        this.updateDisplay();
    }

    checkMissedPhaseChanges(previousTotal, currentTotal) {
        const previousPhaseIdx = Math.floor(previousTotal / this.intervalDuration);
        const currentPhaseIdx = Math.floor(currentTotal / this.intervalDuration);
        
        if (currentPhaseIdx > previousPhaseIdx) {
            // We transitioned phases while in background
            const totalPhases = currentPhaseIdx;
            this.currentPhase = (totalPhases % 2 === 0) ? 'fast' : 'slow';
            this.currentCycle = Math.floor(totalPhases / 2) + 1;
            
            // Backend handles all audio - frontend silent
            this.vibrate();
        }

        if (this.totalElapsed >= this.sessionDuration) {
            this.completeSession();
        }
    }

    toggleTimer() {
        if (this.isRunning) {
            this.stopTimer();
        } else {
            this.startTimer();
        }
    }

    startTimer() {
        this.isRunning = true;
        this.startStopBtn.textContent = 'Stop';
        this.startStopBtn.classList.add('active');
        
        // Start Android Foreground Service via bridge
        if (window.Android && window.Android.startTimerService) {
            window.Android.startTimerService();
        }

        // AudioContext removed - backend handles all sounds

        if (this.currentPhase === 'ready') {
            this.currentPhase = 'fast';
            // Backend handles all audio - frontend silent
            this.vibrate();
            
            // Absolute timing: Record end time for entire session
            this.endTime = Date.now() + (this.sessionDuration * 1000);
            this.totalElapsed = 0;
        } else {
            // Resume: recalculate endTime based on remaining time
            const remainingTime = (this.sessionDuration - this.totalElapsed) * 1000;
            this.endTime = Date.now() + remainingTime;
        }

        this.interval = setInterval(() => this.tick(), 1000);
        this.updateDisplay();
    }

    stopTimer() {
        this.isRunning = false;
        this.startStopBtn.textContent = 'Start';
        this.startStopBtn.classList.remove('active');
        
        if (this.interval) {
            clearInterval(this.interval);
            this.interval = null;
        }

        // Stop Android Foreground Service via bridge
        if (window.Android && window.Android.stopTimerService) {
            window.Android.stopTimerService();
        }
    }

    resetTimer() {
        this.stopTimer();
        this.currentPhase = 'ready';
        this.currentCycle = 1;
        this.totalElapsed = 0;
        this.currentTime = this.intervalDuration;
        this.startTime = null;
        this.endTime = null;
        this.updateDisplay();
    }

    tick() {
        if (!this.isRunning || !this.endTime) return;

        // BULLETPROOF: Check absolute time against expected end time
        const now = Date.now();
        if (now >= this.endTime) {
            // Session completed - force complete state immediately
            this.totalElapsed = this.sessionDuration;
            this.currentTime = 0;
            this.completeSession();
            return;
        }

        // Absolute timing: Calculate remaining based on end time
        const remaining = Math.max(0, this.endTime - now);
        this.totalElapsed = this.sessionDuration - Math.floor(remaining / 1000);

        // Prevent negative time math
        if (this.totalElapsed >= this.sessionDuration) {
            this.totalElapsed = this.sessionDuration;
            this.currentTime = 0;
            this.completeSession();
            return;
        }

        // Check for phase change
        const newPhaseIdx = Math.floor(this.totalElapsed / this.intervalDuration);
        const oldPhaseIdx = Math.floor((this.totalElapsed - 1) / this.intervalDuration);

        if (newPhaseIdx > oldPhaseIdx) {
            this.switchPhase();
        }

        // Update currentTime for display
        this.currentTime = this.intervalDuration - (this.totalElapsed % this.intervalDuration);

        this.updateDisplay();
    }

    switchPhase() {
        // Backend handles all audio - frontend silent
        this.vibrate();

        const totalPhases = Math.floor(this.totalElapsed / this.intervalDuration);
        this.currentPhase = (totalPhases % 2 === 0) ? 'fast' : 'slow';
        this.currentCycle = Math.floor(totalPhases / 2) + 1;
    }

    completeSession() {
        this.totalElapsed = this.sessionDuration;
        this.stopTimer();
        // Backend handles all audio - frontend silent
        this.vibrate();
        this.phaseText.textContent = 'COMPLETE!';
        this.phaseIndicator.className = 'phase-indicator complete';
    }

    updateDisplay() {
        this.phaseIndicator.className = `phase-indicator ${this.currentPhase}`;
        
        if (this.currentPhase === 'ready') {
            this.phaseText.textContent = 'READY';
        } else if (this.currentPhase === 'fast') {
            this.phaseText.textContent = 'FAST';
        } else if (this.currentPhase === 'slow') {
            this.phaseText.textContent = 'SLOW';
        }

        const minutes = Math.floor(this.currentTime / 60);
        const seconds = this.currentTime % 60;
        this.timeText.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;

        this.cycleInfo.textContent = `Cycle ${Math.min(this.currentCycle, this.totalCycles)}/${this.totalCycles}`;

        const progress = (this.totalElapsed / this.sessionDuration) * 100;
        this.progressFill.style.width = `${Math.min(progress, 100)}%`;

        const elapsedMinutes = Math.floor(this.totalElapsed / 60);
        const elapsedSeconds = this.totalElapsed % 60;
        this.elapsedTime.textContent = `${elapsedMinutes}:${elapsedSeconds.toString().padStart(2, '0')}`;

        const totalMinutes = Math.floor(this.sessionDuration / 60);
        const totalSeconds = this.sessionDuration % 60;
        this.totalTime.textContent = `${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}`;
    }

}

// Wake-up sync function for MainActivity to call
window.forceTimerSync = function() {
    if (timer && timer.isRunning && timer.endTime) {
        // Force immediate time calculation to handle cryo-sleep desync
        const now = Date.now();
        if (now >= timer.endTime) {
            // Session completed while app was asleep - force complete state
            timer.totalElapsed = timer.sessionDuration;
            timer.currentTime = 0;
            timer.completeSession();
        } else {
            // Update to current real-world time
            const remaining = Math.max(0, timer.endTime - now);
            timer.totalElapsed = timer.sessionDuration - Math.floor(remaining / 1000);
            
            // Prevent negative time math
            if (timer.totalElapsed >= timer.sessionDuration) {
                timer.totalElapsed = timer.sessionDuration;
                timer.currentTime = 0;
                timer.completeSession();
            } else {
                timer.currentTime = timer.intervalDuration - (timer.totalElapsed % timer.intervalDuration);
            }
            
            timer.updateDisplay();
        }
        console.log('FORCE SYNC: Timer updated to real-world time');
    }
};

document.addEventListener('DOMContentLoaded', () => {
    new JapaneseWalkingTimer();
});
