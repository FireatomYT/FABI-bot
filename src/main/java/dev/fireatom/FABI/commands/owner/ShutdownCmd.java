package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class ShutdownCmd extends CommandBase {

	public ShutdownCmd() {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		// Reply
		event.reply("Shutting down...").queue();
		// Update presence
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		// Log
		bot.getAppLogger().info("Shutting down, by '{}'", event.getUser().getName());
		// Shutdown
		bot.shutdownUtils();
		event.getJDA().shutdown();
	}
}
