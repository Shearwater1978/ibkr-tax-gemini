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
  const path = require('path');
  
  // Путь к самому скрипту бэкенда
  const scriptPath = path.join(__dirname, 'backend', 'api.py');
  
  // Корень проекта (поднимаемся на один уровень выше из папки gui)
  const projectRoot = path.join(__dirname, '..'); 

  console.log("Starting Python Backend from root:", projectRoot);

  // ЗАПУСК: Добавляем { cwd: projectRoot }, чтобы Python "думал", 
  // что он запущен в корне, как и main.py
  pythonProcess = spawn('python', [scriptPath], { 
    cwd: projectRoot 
  });

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