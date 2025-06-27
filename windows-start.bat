@echo off
@title VOTL Windows Launcher

setlocal enabledelayedexpansion
set Looping=False

cls

:START
echo.
echo Welcome to VOTL's window launcher
echo Please select an option to begin:
echo.
echo    1. Start the bot normally
echo    2. Start the bot with automatic restarts
echo    9. Exit program
echo.

set /p in="Enter your option: "

if !in! == 1 (
    goto START_BOT
)
if !in! == 2 (
    goto RESTART_LOOP
)
if !in! == 9 (
    goto EOF
) else (
    echo.
    echo     Invalid option given
    goto START
)

:RESTART_LOOP
set Looping=True
goto START_BOT

:START_BOT
java -Dfile.encoding=UTF-8 -jar VOTL.jar --no-colors
if !Looping! == False (
    goto START
)

echo.
echo Restarting the bot in 5 second, press CTRL + C to cancel the process.
echo.

choice /d y /t 5 > nul
goto START_BOT

:EOF
exit