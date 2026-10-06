@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

set "JAR=target\techo-1.0.0.jar"

if not exist "%JAR%" (
    echo.
    echo [错误] 找不到 %JAR%
    echo.
    echo 请先构建：
    echo     mvn clean package
    echo.
    pause
    exit /b 1
)

echo ============================================
echo   Techo 手账 - 正在启动
echo ============================================
echo.
echo   本机访问：  http://localhost:8080
echo   手机访问：  http://本机局域网IP:8080
echo.
echo   查看本机 IP：  ipconfig
echo   停止服务：     按 Ctrl+C
echo.
echo ============================================
echo.

rem 内存参数说明见 service\techo.xml，实测把工作集从 386 MB 压到 151 MB
java ^
  -Xms32m ^
  -Xmx192m ^
  -XX:+UseSerialGC ^
  -XX:ActiveProcessorCount=2 ^
  --enable-native-access=ALL-UNNAMED ^
  -jar "%JAR%"

echo.
echo 服务已停止。
pause
