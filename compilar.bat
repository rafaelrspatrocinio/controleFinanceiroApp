@echo off
setlocal
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven nao encontrado. Para recompilar, instale o Maven e tente novamente.
  pause
  exit /b 1
)
echo Compilando Meu Caixa Java...
call mvn -q clean package
if errorlevel 1 (
  echo A compilacao falhou.
  pause
  exit /b 1
)
echo.
echo Projeto compilado: target\controle-de-caixa-1.0.0.jar
pause
