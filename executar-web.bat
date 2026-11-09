@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java nao encontrado. Instale o Java 8 ou mais recente e tente novamente.
  pause
  exit /b 1
)
if not exist "target\controle-de-caixa-1.0.0-web.jar" (
  echo A aplicacao web ainda nao foi compilada nesta pasta.
  echo Execute compilar-web.bat em um computador com Maven e internet.
  pause
  exit /b 1
)
java -jar "target\controle-de-caixa-1.0.0-web.jar"
if errorlevel 1 (
  echo A aplicacao web nao conseguiu iniciar.
  pause
  exit /b 1
)
