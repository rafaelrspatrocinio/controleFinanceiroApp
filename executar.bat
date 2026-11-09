@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java nao encontrado. Instale o Java 8 ou mais recente e tente novamente.
  pause
  exit /b 1
)
if not exist "target\controle-de-caixa-1.0.0.jar" (
  echo O aplicativo ainda nao foi compilado nesta pasta.
  if exist "compilar.bat" (
    echo Execute compilar.bat em um computador com Maven e conexao com a internet.
  ) else (
    echo Compile o projeto com Maven antes de executar.
  )
  pause
  exit /b 1
)
java -jar "target\controle-de-caixa-1.0.0.jar"
if errorlevel 1 (
  echo O aplicativo nao conseguiu iniciar. Confirme que ha um ambiente grafico disponivel.
  pause
  exit /b 1
)
