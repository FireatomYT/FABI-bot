# FABI
 [![Build status](https://github.com/FireatomYT/FABI-1/blob/main/README.md)](https://github.com/FireatomYT/FABI-1/blob/main/README.md)  
 FABI - discord bot written in Java using JDA library.  
 Functions: server moderation and sync blacklists, custom voice channels and verification, ticketing.  

Visit soon to learn more about available commands and to view documentation.

## Download or building
 Stable JAR file can be downloaded from latest Release [here](https://github.com/FireatomYT/FABI-1?tab=readme-ov-file).  
 Additional Snapshot builds can be accessed [here](https://github.com/FireatomYT/FABI-1?tab=readme-ov-file).
 
 Build from source using `.\gradlew shadowJar`.

## Config file
 data/config.json:
 ```json
 {
	"bot-token": "",
	"owner-id": "owner's ID",
	"dev-servers": [
		"dev server's IDs"
	],
	"webhook": "link to webhook, if you want to receive ERROR level logs"
 }
 ```

## Inspiration/Credits
 Thanks to Chew (JDA-Chewtils and Chewbotcca bot) and jagrosh (JDA-Utilities)  
 [PurrBot](https://github.com/purrbot-site/PurrBot) by Andre_601 (purrbot.site)  
 [AvaIre](https://github.com/avaire/avaire) by Senither  
 Ryzeon & Inkception for [Discord (JDA) HTML Transcripts](https://github.com/Ryzeon/discord-html-transcripts)
