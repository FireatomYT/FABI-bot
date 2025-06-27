package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.List;

public class BotBlacklist extends CommandBase {
	public BotBlacklist() {
		this.name = "bot_blacklist";
		this.path = "bot.owner.bot_blacklist";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
			new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long id = event.optLong("id");

		try {
			if (event.optBoolean("add")) {
				bot.getDBUtil().botBlacklist.add(id);
				event.reply("Added ID `%s` to blacklist.".formatted(id)).queue();
			} else {
				bot.getDBUtil().botBlacklist.remove(id);
				event.reply("Removed ID `%s` from blacklist.".formatted(id)).queue();
			}
		} catch (SQLException ex) {
			event.reply("Error: %s".formatted(ex.getMessage())).queue();
		}
	}
}
