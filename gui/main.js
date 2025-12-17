// gui/main.js
const { app, BrowserWindow } = require('electron');
const path = require('path');
const { spawn } = require('child_process');

let mainWindow;
let pythonProcess;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false // For simple prototype
    }
  });

  mainWindow.loadFile('ui/index.html');
}

function startPythonBackend() {
  // Запускаем python gui/backend/api.py
  // В продакшене тут будет путь к скомпилированному .exe
  const scriptPath = path.join(__dirname, 'backend', 'api.py');
  
  console.log("Starting Python Backend...");
  pythonProcess = spawn('python', [scriptPath]);

  pythonProcess.stdout.on('data', (data) => {
    console.log(`Python: ${data}`);
  });

  pythonProcess.stderr.on('data', (data) => {
    console.error(`Python Error: ${data}`);
  });
}

app.whenReady().then(() => {
  startPythonBackend();
  createWindow();
});

// Убиваем Python при закрытии окна
app.on('will-quit', () => {
  if (pythonProcess) {
    pythonProcess.kill();
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});