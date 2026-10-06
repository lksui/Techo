@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo   安装 Techo 为 Windows 服务
echo ============================================================
echo.

rem ---- 检查管理员权限 ----
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo   [错误] 需要管理员权限。
    echo.
    echo   请关闭本窗口，右键本文件，选择「以管理员身份运行」。
    echo.
    pause
    exit /b 1
)

rem ---- 检查文件是否齐全 ----
if not exist "%~dp0techo.exe" (
    echo   [错误] 同目录下缺少 techo.exe
    pause
    exit /b 1
)
if not exist "%~dp0techo.xml" (
    echo   [错误] 同目录下缺少 techo.xml
    pause
    exit /b 1
)
if not exist "%~dp0..\target\techo-1.0.0.jar" (
    echo   [错误] 找不到应用 jar：..\target\techo-1.0.0.jar
    echo.
    echo   请先在项目根目录执行构建：
    echo       mvn clean package
    echo.
    pause
    exit /b 1
)

rem ---- 已存在就先卸载，保证可重复执行 ----
"%~dp0techo.exe" status >nul 2>&1
if %errorlevel% equ 0 (
    echo   检测到已存在的服务，先停止并移除...
    "%~dp0techo.exe" stop       >nul 2>&1
    "%~dp0techo.exe" uninstall  >nul 2>&1
    timeout /t 3 /nobreak >nul
)

rem ---- 安装并启动 ----
echo   正在注册服务...
"%~dp0techo.exe" install
if %errorlevel% neq 0 (
    echo.
    echo   [错误] 服务注册失败，请看上面的提示。
    pause
    exit /b 1
)

echo   正在启动服务...
"%~dp0techo.exe" start
timeout /t 4 /nobreak >nul

echo.
echo ============================================================
"%~dp0techo.exe" status
echo ============================================================
echo.
echo   完成。服务已设为「自动」启动，开机会自己跑起来。
echo.
echo   下一步（如果还没做）：
echo     1. 允许防火墙入站 8080 端口
echo     2. 在路由器里给本机绑定静态 IP
echo     详见 .\README.md
echo.
echo   现在可以在浏览器打开：http://localhost:8080
echo.
pause
