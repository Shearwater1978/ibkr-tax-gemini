const { app, BrowserWindow } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const os = require('os');

let mainWindow;
let pythonProcess;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  mainWindow.loadFile('ui/index.html');
}

function startPythonBackend() {
  const scriptPath = path.join(__dirname, 'backend', 'api.py');
  
  // Determine python command based on OS
  // Windows usually uses 'python', Mac/Linux usually 'python3'
  const pythonCmd = os.platform() === 'win32' ? 'python' : 'python3';
  
  console.log(`🚀 Starting Python Backend using: ${pythonCmd} ${scriptPath}`);
  
  pythonProcess = spawn(pythonCmd, [scriptPath]);

  pythonProcess.stdout.on('data', (data) => {
    console.log(`🐍 Python Output: ${data}`);
  });

  pythonProcess.stderr.on('data', (data) => {
    console.error(`❌ Python Error: ${data}`);
  });
  
  pythonProcess.on('close', (code) => {
    console.log(`Python process exited with code ${code}`);
  });
}

app.whenReady().then(() => {
  startPythonBackend();
  createWindow();
});

app.on('will-quit', () => {
  if (pythonProcess) {
    pythonProcess.kill();
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
