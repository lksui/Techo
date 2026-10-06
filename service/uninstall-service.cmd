@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo   卸载 Techo 服务
echo ============================================================
echo.

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo   [错误] 需要管理员权限。
    echo   请右键本文件，选择「以管理员身份运行」。
    echo.
    pause
    exit /b 1
)

echo   正在停止服务...
"%~dp0techo.exe" stop
timeout /t 3 /nobreak >nul

echo   正在移除服务...
"%~dp0techo.exe" uninstall

echo.
echo   完成。数据不受影响：
echo     数据库  ..\data\journal.db
echo     备份    ..\backup\
echo     日志    ..\logs\
echo.
pause
