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
        this.intervalDuration = 180; // 3 minutes in seconds
        this.sessionDuration = 1800; // 30 minutes in seconds
        this.currentTime = this.intervalDuration;
        this.totalElapsed = 0;
        this.interval = null;
        this.startTime = null; // System time when start was clicked
        this.lastTickTime = null;

        // Audio/vibration setup
        this.audioContext = null;
        this.initAudio();

        // Event listeners
        this.bindEvents();

        // Initial display
        this.updateDisplay();
    }

    initAudio() {
        // Initialize Web Audio API for beep sounds
        try {
            window.AudioContext = window.AudioContext || window.webkitAudioContext;
            this.audioContext = new AudioContext();
        } catch (e) {
            console.log('Web Audio API not supported');
        }
    }

    playBeep(frequency = 880, repeats = 3) {
        if (!this.audioContext) return;

        // Resume context if suspended (common in mobile browsers)
        if (this.audioContext.state === 'suspended') {
            this.audioContext.resume();
        }

        const chimeLength = 1.0;
        const stepTime = 1.2;

        for (let i = 0; i < repeats; i++) {
            try {
                const oscillator = this.audioContext.createOscillator();
                const gainNode = this.audioContext.createGain();

                oscillator.connect(gainNode);
                gainNode.connect(this.audioContext.destination);

                oscillator.frequency.value = frequency;
                oscillator.type = 'sine';

                const start = this.audioContext.currentTime + i * stepTime;
                gainNode.gain.setValueAtTime(0, start);
                gainNode.gain.linearRampToValueAtTime(0.7, start + 0.01);
                gainNode.gain.exponentialRampToValueAtTime(0.001, start + chimeLength);

                oscillator.start(start);
                oscillator.stop(start + chimeLength);
            } catch (e) {
                console.log('Error playing beep:', e);
            }
        }
    }

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
            
            // Play alert if we just caught up to a new phase
            this.playBeep(1200, 3);
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

        if (this.audioContext && this.audioContext.state === 'suspended') {
            this.audioContext.resume();
        }

        if (this.currentPhase === 'ready') {
            this.currentPhase = 'fast';
            this.playBeep(1000, 2);
            this.vibrate();
            this.startTime = Date.now();
            this.totalElapsed = 0;
        } else {
            // Resume: adjust startTime to account for already elapsed time
            this.startTime = Date.now() - (this.totalElapsed * 1000);
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
        this.updateDisplay();
    }

    tick() {
        // More accurate tick calculation
        const now = Date.now();
        this.totalElapsed = Math.floor((now - this.startTime) / 1000);

        if (this.totalElapsed >= this.sessionDuration) {
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
        this.playBeep(1200, 4);
        this.vibrate();

        const totalPhases = Math.floor(this.totalElapsed / this.intervalDuration);
        this.currentPhase = (totalPhases % 2 === 0) ? 'fast' : 'slow';
        this.currentCycle = Math.floor(totalPhases / 2) + 1;
    }

    completeSession() {
        this.totalElapsed = this.sessionDuration;
        this.stopTimer();
        this.playBeep(880, 5);
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
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new JapaneseWalkingTimer();
});
