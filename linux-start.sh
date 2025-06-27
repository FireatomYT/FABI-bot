#!/bin/bash

welcome() {
    echo ""
    echo "Welcome to FABI-1's Linux launcher"
    echo "Please select an option to begin:"
    echo ""
    echo "   1. Start the bot normally"
    echo "   2. Start the bot with automatic restarts"
    echo "   9. Exit program"
    echo ""

    echo "Enter your option: "

    read -r option

    if [ 1 -eq "$option" ]; then
        echo "Nornal start has been selected, starting the bot..."
        echo ""

        startApplication

    elif [ 2 -eq "$option" ]; then
        echo "Starting the bot with automatic restarts..."
        echo ""

        startLoop
    fi
}

startLoop() {
    while true; do
        startApplication false

        echo ""
        echo "Restarting the application in 5 seconds!"
        echo "If you want to cancel the process, you can use CTRL + C now"
        sleep 5
    done
}

startApplication() {
    java -jar fABI-1.jar

    if [ "$1" != "false" ]; then
        welcome
    fi
}

welcome
