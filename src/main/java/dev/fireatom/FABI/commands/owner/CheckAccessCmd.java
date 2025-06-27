package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public class CheckAccessCmd extends CommandBase {
	public CheckAccessCmd() {
		this.name = "checkaccess";
		this.path = "bot.owner.checkaccess";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.options = List.of(
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = event.getJDA().getGuildById(event.optString("server"));
		if (guild == null) {
			editError(event, path+".no_guild");
			return;
		}

		User user = event.optUser("user");
		if (user == null) {
			editErrorUnknown(event, "No user found.");
			return;
		}

		guild.retrieveMember(user).queue(member -> {
			CmdAccessLevel level = bot.getCheckUtil().getAccessLevel(member);
			editMsg(event, "%s(%s) - %s".formatted(member.getAsMention(), member.getEffectiveName(), level.getName()));
		}, failure -> {
			editError(event, failure.getMessage());
		});
	}
}