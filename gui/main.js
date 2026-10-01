// gui/main.js
const { app, BrowserWindow } = require('electron');
const http = require('http');
const path = require('path');
const { spawn } = require('child_process');

let mainWindow;
let pythonProcess;
const backendUrl = 'http://127.0.0.1:8000/health';

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  mainWindow.loadFile('ui/index.html');
}

function startPythonBackend() {
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

  pythonProcess.on('error', (error) => {
    console.error('Python backend failed to start:', error);
  });

  pythonProcess.on('close', (code) => {
    if (code !== 0) {
      console.error(`Python backend exited with code ${code}`);
    }
  });
}

function backendIsReady() {
  return new Promise((resolve) => {
    const request = http.get(backendUrl, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => {
        body += chunk;
      });
      response.on('end', () => {
        try {
          resolve(response.statusCode === 200 && JSON.parse(body).status === 'ready');
        } catch {
          resolve(false);
        }
      });
    });

    request.setTimeout(1000, () => request.destroy());
    request.on('error', () => resolve(false));
  });
}

app.whenReady().then(async () => {
  if (await backendIsReady()) {
    console.log('Using existing Python backend.');
  } else {
    startPythonBackend();
  }
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