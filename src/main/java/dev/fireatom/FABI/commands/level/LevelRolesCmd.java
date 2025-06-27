package dev.fireatom.FABI.commands.level;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.ExpType;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.LevelRolesManager;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LevelRolesCmd extends CommandBase {
	public LevelRolesCmd() {
		this.name = "level_roles";
		this.path = "bot.level.level_roles";
		this.children = new SlashCommand[]{
			new SetLevelRoles(), new RemoveLevelRoles(), new ViewLevelRoles(),
		};
		this.category = CmdCategory.LEVELS;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class SetLevelRoles extends SlashCommand {
		public SetLevelRoles() {
			this.name = "set";
			this.path = "bot.level.level_roles.set";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
					.setRequiredRange(1, 10_000),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.INTEGER, "type", lu.getText(path+".type.help"), true)
					.addChoice("ALL", 0)
					.addChoice("Text levels", 1)
					.addChoice("Voice levels", 2)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			int level = event.optInteger("level");
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".invalid_args");
				return;
			}

			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", denyReason);
				return;
			}
			if (bot.getDBUtil().levelRoles.getLevelsCount(event.getGuild().getIdLong()) >= 40) {
				editError(event, path+".limit");
				return;
			}

			int typeValue = event.optInteger("type", 0);
			ExpType type = ExpType.values()[typeValue];
			try {
				bot.getDBUtil().levelRoles.add(event.getGuild().getIdLong(), level, role.getId(), true, type);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "level roles set");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention(), level))
				.build()
			);
		}
	}

	private class RemoveLevelRoles extends SlashCommand {
		public RemoveLevelRoles() {
			this.name = "remove";
			this.path = "bot.level.level_roles.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
					.setRequiredRange(1, 10_000)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			int level = event.optInteger("level");

			if (!bot.getDBUtil().levelRoles.getAllLevels(event.getGuild().getIdLong()).existsAtLevel(level)) {
				editError(event, path+".empty");
				return;
			}

			try {
				bot.getDBUtil().levelRoles.remove(event.getGuild().getIdLong(), level);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "level roles remove");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(level))
				.build()
			);
		}
	}

	private class ViewLevelRoles extends SlashCommand {
		public ViewLevelRoles() {
			this.name = "view";
			this.path = "bot.level.level_roles.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			LevelRolesManager.LevelRoleData data = bot.getDBUtil().levelRoles.getAllLevels(event.getGuild().getIdLong());
			if (data.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			StringBuilder response = new StringBuilder("**Text:**");
			Map<Integer, Set<Long>> allRoles = data.getAllRoles(ExpType.TEXT);
			if (allRoles.isEmpty()) {
				response.append("\n*none*");
			} else {
				allRoles.forEach((level, roles) -> {
					response.append("\n> `%5d` - ".formatted(level))
						.append(roles.stream().map("<@&%s>"::formatted).collect(Collectors.joining(", ")));
				});
			}
			response.append("\n\n**Voice:**");
			allRoles = data.getAllRoles(ExpType.VOICE);
			if (allRoles.isEmpty()) {
				response.append("\n*none*");
			} else {
				allRoles.forEach((level, roles) -> {
					response.append("\n> `%5d` - ".formatted(level))
						.append(roles.stream().map("<@&%s>"::formatted).collect(Collectors.joining(", ")));
				});
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"))
				.setDescription(response.toString())
				.build()
			);
		}
	}
}
