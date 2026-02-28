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

    playBeep(frequency = 800, duration = 3000) {
        if (!this.audioContext) return;

        try {
            const oscillator = this.audioContext.createOscillator();
            const gainNode = this.audioContext.createGain();

            oscillator.connect(gainNode);
            gainNode.connect(this.audioContext.destination);

            oscillator.frequency.value = frequency;
            oscillator.type = 'sine';

            gainNode.gain.setValueAtTime(0.3, this.audioContext.currentTime);
            gainNode.gain.exponentialRampToValueAtTime(0.01, this.audioContext.currentTime + duration / 1000);

            oscillator.start(this.audioContext.currentTime);
            oscillator.stop(this.audioContext.currentTime + duration / 1000);
        } catch (e) {
            console.log('Error playing beep:', e);
        }
    }

    vibrate() {
        if ('vibrate' in navigator) {
            navigator.vibrate([200, 100, 200]);
        }
    }

    bindEvents() {
        this.startStopBtn.addEventListener('click', () => this.toggleTimer());
        this.resetBtn.addEventListener('click', () => this.resetTimer());

        // Prevent screen sleep on mobile
        if ('wakeLock' in navigator) {
            this.wakeLock = null;
            this.requestWakeLock();
        }
    }

    async requestWakeLock() {
        try {
            this.wakeLock = await navigator.wakeLock.request('screen');
        } catch (e) {
            console.log('Wake Lock not supported:', e);
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
        
        // Resume audio context if suspended
        if (this.audioContext && this.audioContext.state === 'suspended') {
            this.audioContext.resume();
        }

        // Start with fast phase if beginning
        if (this.currentPhase === 'ready') {
            this.currentPhase = 'fast';
            this.playBeep(1000, 3000);
            this.vibrate();
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

        // Release wake lock
        if (this.wakeLock) {
            this.wakeLock.release();
            this.wakeLock = null;
        }
    }

    resetTimer() {
        this.stopTimer();
        this.currentPhase = 'ready';
        this.currentCycle = 1;
        this.currentTime = this.intervalDuration;
        this.totalElapsed = 0;
        this.updateDisplay();
    }

    tick() {
        this.currentTime--;
        this.totalElapsed++;

        // Check for phase change
        if (this.currentTime <= 0) {
            this.switchPhase();
        }

        // Check for session completion
        if (this.totalElapsed >= this.sessionDuration) {
            this.completeSession();
            return;
        }

        this.updateDisplay();
    }

    switchPhase() {
        // Play alert sound and vibrate
        this.playBeep(1200, 3000);
        this.vibrate();

        if (this.currentPhase === 'fast') {
            this.currentPhase = 'slow';
        } else {
            this.currentPhase = 'fast';
            this.currentCycle++;
        }

        this.currentTime = this.intervalDuration;
    }

    completeSession() {
        this.stopTimer();
        
        // Play completion sound
        this.playBeep(800, 3000);
        setTimeout(() => this.playBeep(1000, 3000), 250);
        setTimeout(() => this.playBeep(1200, 3000), 500);
        this.vibrate();

        // Show completion message
        this.phaseText.textContent = 'COMPLETE!';
        this.phaseIndicator.className = 'phase-indicator complete';
        
        // Optional: Show browser notification
        if ('Notification' in navigator && Notification.permission === 'granted') {
            new Notification('Japanese Walking Timer', {
                body: 'Great job! You completed your 30-minute walking session!',
                icon: 'icon-192.png'
            });
        }
    }

    updateDisplay() {
        // Update phase indicator
        this.phaseIndicator.className = `phase-indicator ${this.currentPhase}`;
        
        if (this.currentPhase === 'ready') {
            this.phaseText.textContent = 'READY';
        } else if (this.currentPhase === 'fast') {
            this.phaseText.textContent = 'FAST';
        } else if (this.currentPhase === 'slow') {
            this.phaseText.textContent = 'SLOW';
        }

        // Update timer display
        const minutes = Math.floor(this.currentTime / 60);
        const seconds = this.currentTime % 60;
        this.timeText.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;

        // Update cycle info
        if (this.currentPhase === 'ready') {
            this.cycleInfo.textContent = 'Cycle 1/5';
        } else {
            this.cycleInfo.textContent = `Cycle ${Math.min(this.currentCycle, this.totalCycles)}/${this.totalCycles}`;
        }

        // Update progress bar
        const progress = (this.totalElapsed / this.sessionDuration) * 100;
        this.progressFill.style.width = `${progress}%`;

        // Update elapsed time
        const elapsedMinutes = Math.floor(this.totalElapsed / 60);
        const elapsedSeconds = this.totalElapsed % 60;
        this.elapsedTime.textContent = `${elapsedMinutes}:${elapsedSeconds.toString().padStart(2, '0')}`;
    }
}

// Notification permission request
if ('Notification' in navigator && Notification.permission === 'default') {
    Notification.requestPermission();
}

// Initialize timer when page loads
document.addEventListener('DOMContentLoaded', () => {
    new JapaneseWalkingTimer();
});

// PWA install prompt
let deferredPrompt;

window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    
    // Show install button or banner (optional)
    const installBtn = document.createElement('button');
    installBtn.textContent = 'Install App';
    installBtn.className = 'btn btn-secondary install-btn';
    installBtn.style.position = 'fixed';
    installBtn.style.top = '20px';
    installBtn.style.right = '20px';
    installBtn.style.zIndex = '1000';
    
    installBtn.addEventListener('click', () => {
        deferredPrompt.prompt();
        deferredPrompt.userChoice.then((choiceResult) => {
            if (choiceResult.outcome === 'accepted') {
                console.log('User accepted the install prompt');
            }
            deferredPrompt = null;
            installBtn.remove();
        });
    });
    
    document.body.appendChild(installBtn);
});
