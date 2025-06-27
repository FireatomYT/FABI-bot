package dev.fireatom.FABI.commands.other;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.constants.CmdCategory;

public class PingCmd extends CommandBase {
	
	public PingCmd() {
		this.name = "ping";
		this.path = "bot.other.ping";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.reply(lu.getText(event, path+".loading")).queue();

		event.getJDA().getRestPing().queue(time -> {
			editMsg(event,
				lu.getText(event, "bot.other.ping.info_full")
					.replace("{websocket}", event.getJDA().getGatewayPing()+"")
					.replace("{rest}", time+"")
			);
		});	
	}
}
