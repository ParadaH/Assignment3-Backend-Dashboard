const API_URL = 'http://localhost:8080';

const ctx = document.getElementById('temperatureChart').getContext('2d');
const temperatureChart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: [],
        datasets: [{
            label: 'Temperature (°C)',
            data: [],
            borderColor: 'rgba(75, 192, 192, 1)',
            borderWidth: 2,
            fill: false
        }]
    },
    options: {
        responsive: true,
        scales: {
            x: {
                type: 'time',
                time: {
                    unit: 'second'
                },
                title: {
                    display: true,
                    text: 'Time'
                }
            },
            y: {
                title: {
                    display: true,
                    text: 'Temperature (°C)'
                }
            }
        }
    }
});

async function fetchCurrentTemperature() {
    try {
        const response = await fetch(`${API_URL}/ESP32_temperature`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('currentTemperature').innerText = `${data.temperature}`;
        }
    } catch (error) {
        console.error("Error fetching current temperature:", error);
    }
}

async function fetchSamplingFrequency() {
    try {
        const response = await fetch(`${API_URL}/samplingFrequency`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('samplingFrequency').innerText = `${data.samplingFrequency}`;
        }
    } catch (error) {
        console.error("Error fetching sampling frequency:", error);
    }
}

async function fetchSystemState() {
    try {
        const response = await fetch(`${API_URL}/systemState`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('systemState').innerText = `${data.systemState}`;
        }
    } catch (error) {
        console.error("Error fetching system state:", error);
    }
}

async function fetchWindowTilt() {
    try {
        const response = await fetch(`${API_URL}/windowTilt`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('windowTilt').innerText = `${data.windowTilt}`;
        }
    } catch (error) {
        console.error("Error fetching window tilt:", error);
    }
}

async function fetchTemperatureHistory() {
    try {
        const response = await fetch(`${API_URL}/temperatureHistory`);
        if (response.ok) {
            const data = await response.json();

            // Update chart
            temperatureChart.data.labels = data.map(point => new Date(point.timestamp));
            temperatureChart.data.datasets[0].data = data.map(point => point.temperature);
            temperatureChart.update();

            // Calculate average, max, and min temperatures
            const temperatures = data.map(point => point.temperature);
            const avgTemp = (temperatures.reduce((sum, val) => sum + val, 0) / temperatures.length).toFixed(2);
            const maxTemp = Math.max(...temperatures).toFixed(2);
            const minTemp = Math.min(...temperatures).toFixed(2);

            document.getElementById('averageTemperature').innerText = avgTemp;
            document.getElementById('maxTemperature').innerText = maxTemp;
            document.getElementById('minTemperature').innerText = minTemp;
        }
    } catch (error) {
        console.error("Error fetching temperature history:", error);
    }
}

async function sendCommand(command) {
    try {
        const response = await fetch(`${API_URL}/sendCommand`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ command })
        });

        if (response.ok) {
            console.log(`${command} sent successfully`);
        } else {
            console.error(`Failed to send ${command}`);
        }
    } catch (error) {
        console.error(`Error sending ${command}:`, error);
    }
}

document.getElementById('resetAlarmButton').addEventListener('click', () => sendCommand('RESET_ALARM'));
document.getElementById('autoModeButton').addEventListener('click', () => sendCommand('AUTO_MODE'));
document.getElementById('manualModeButton').addEventListener('click', () => sendCommand('MANUAL_MODE'));
document.getElementById('cleanHistoryButton').addEventListener('click', async () => {
    temperatureChart.data.labels = [];
    temperatureChart.data.datasets[0].data = [];
    temperatureChart.update();

    try {
        const response = await fetch(`${API_URL}/clearTemperatureHistory`, { method: 'POST' });
        if (response.ok) {
            console.log('Temperature history cleared.');
        } else {
            console.error('Failed to clear history.');
        }
    } catch (error) {
        console.error('Error clearing history:', error);
    }
});

setInterval(() => {
    fetchCurrentTemperature();
    fetchSamplingFrequency();
    fetchSystemState();
    fetchWindowTilt();
    fetchTemperatureHistory();
}, 1000); //set for 1s

fetchCurrentTemperature();
fetchSamplingFrequency();
fetchSystemState();
fetchWindowTilt();
fetchTemperatureHistory();