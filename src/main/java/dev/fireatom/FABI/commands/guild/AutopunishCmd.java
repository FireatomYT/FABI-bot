package dev.fireatom.FABI.commands.guild;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.PunishAction;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.AutopunishManager;
import dev.fireatom.FABI.utils.exception.FormatterException;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AutopunishCmd extends CommandBase {

	public AutopunishCmd() {
		this.name = "autopunish";
		this.path = "bot.guild.autopunish";
		this.children = new SlashCommand[]{new Add(), new Remove(), new View()};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {

		public Add() {
			this.name = "add";
			this.path = "bot.guild.autopunish.add";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "strike-count", lu.getText(path+".strike-count.help"), true).setRequiredRange(1, 40),
				new OptionData(OptionType.BOOLEAN, "kick", lu.getText(path+".kick.help")),
				new OptionData(OptionType.STRING, "mute", lu.getText(path+".mute.help")),
				new OptionData(OptionType.STRING, "ban", lu.getText(path+".ban.help")),
				new OptionData(OptionType.ROLE, "remove-role", lu.getText(path+".remove-role.help")),
				new OptionData(OptionType.ROLE, "add-role", lu.getText(path+".add-role.help")),
				new OptionData(OptionType.ROLE, "temp-role", lu.getText(path+".temp-role.help")),
				new OptionData(OptionType.STRING, "temp-duration", lu.getText(path+".temp-duration.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			final int strikeCount = event.optInteger("strike-count");
			
			if ((event.hasOption("kick") ? 1 : 0) + (event.hasOption("mute") ? 1 : 0) + (event.hasOption("ban") ? 1 : 0) >= 2) {
				editError(event, path+".one_option");
				return;
			}
			if ((event.hasOption("ban") || event.hasOption("kick"))
				&& (event.hasOption("remove-role") || event.hasOption("add-role") || event.hasOption("temp-role"))) {
				editError(event, path+".ban_kick_role");
				return;
			}
			if (event.hasOption("remove-role") && (event.hasOption("add-role") || event.hasOption("temp-role"))
				&& event.optRole("remove-role").equals(Optional.ofNullable(event.optRole("add-role")).orElse(event.optRole("temp-role")))) {
				editError(event, path+".same_role");
				return;
			}

			if (bot.getDBUtil().autopunish.getAction(event.getGuild().getIdLong(), strikeCount) != null) {
				editError(event, path+".exists");
				return;
			}
			
			List<PunishAction> actions = new ArrayList<>();
			List<String> data = new ArrayList<>();
			StringBuilder builder = new StringBuilder(lu.getText(event, path+".title").formatted(strikeCount));
			if (event.optBoolean("kick", false)) {
				actions.add(PunishAction.KICK);
				builder.append(lu.getText(event, path+".vkick"));
			}
			if (event.hasOption("mute")) {
				Duration duration;
				try {
					duration = TimeUtil.stringToDuration(event.optString("mute"), false);
				} catch (FormatterException ex) {
					editError(event, ex.getPath());
					return;
				}
				if (duration.isZero()) {
					editError(event, path+".not_zero");
					return;
				}
				actions.add(PunishAction.MUTE);
				data.add("t"+duration.getSeconds());
				builder.append(lu.getText(event, path+".vmute").formatted(TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), duration)));
			}
			if (event.hasOption("ban")) {
				Duration duration;
				try {
					duration = TimeUtil.stringToDuration(event.optString("ban"), false);
				} catch (FormatterException ex) {
					editError(event, ex.getPath());
					return;
				}
				actions.add(PunishAction.BAN);
				data.add("t"+duration.getSeconds());
				builder.append(lu.getText(event, path+".vban").formatted(duration.isZero() ?
					lu.getText(event, "misc.permanently") :
					lu.getText(event, path+".for")+" "+TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), duration)
				));
			}
			if (event.hasOption("remove-role")) {
				Role role = event.optRole("remove-role");
				if (!event.getGuild().getSelfMember().canInteract(role)) {
					editError(event, path+".incorrect_role");
					return;
				}
				actions.add(PunishAction.REMOVE_ROLE);
				data.add("rr"+role.getId());
				builder.append(lu.getText(event, path+".vremove").formatted(role.getName()));
			}
			if (event.hasOption("add-role")) {
				Role role = event.optRole("add-role");
				if (!event.getGuild().getSelfMember().canInteract(role)) {
					editError(event, path+".incorrect_role");
					return;
				}
				actions.add(PunishAction.ADD_ROLE);
				data.add("ar"+role.getId());
				builder.append(lu.getText(event, path+".vadd").formatted(role.getName()));
			}
			if (event.hasOption("temp-role")) {
				Role role = event.optRole("temp-role");
				if (!event.getGuild().getSelfMember().canInteract(role)) {
					editError(event, path+".incorrect_role");
					return;
				}
				if (!event.hasOption("temp-duration")) {
					editError(event, path+".no_duration");
					return;
				}
				Duration duration;
				try {
					duration = TimeUtil.stringToDuration(event.optString("temp-duration"), false);
				} catch (FormatterException ex) {
					editError(event, ex.getPath());
					return;
				}
				if (duration.isZero()) {
					editError(event, path+".not_zero");
					return;
				}
				actions.add(PunishAction.TEMP_ROLE);
				data.add("tr"+role.getId()+"-"+duration.getSeconds());
				builder.append(lu.getText(event, path+".vtempadd").formatted(role.getName(), TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), duration)));
			}

			if (actions.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			try {
				bot.getDBUtil().autopunish.addAction(event.getGuild().getIdLong(), strikeCount, actions, String.join(";", data));
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add action");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_SUCCESS).setDescription(builder.toString()).build());
		}

	}

	private class Remove extends SlashCommand {

		public Remove() {
			this.name = "remove";
			this.path = "bot.guild.autopunish.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "strike-count", lu.getText(path+".strike-count.help"), true).setRequiredRange(1, 40)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer strikeCount = event.optInteger("strike-count");

			if (bot.getDBUtil().autopunish.getAction(event.getGuild().getIdLong(), strikeCount) == null) {
				editError(event, path+".not_found");
				return;
			}

			try {
				bot.getDBUtil().autopunish.removeAction(event.getGuild().getIdLong(), strikeCount);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove action");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(strikeCount))
				.build());
		}
		
	}

	private class View extends SlashCommand {

		public View() {
			this.name = "view";
			this.path = "bot.guild.autopunish.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			List<AutopunishManager.Autopunish> list = bot.getDBUtil().autopunish.getAllActions(event.getGuild().getIdLong());
			if (list.isEmpty()) {
				editError(event, path+".empty");
				return;
			}

			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < list.size(); i++) {
				AutopunishManager.Autopunish map = list.get(i);

				List<PunishAction> actions = map.getActions();
				if (actions.isEmpty()) continue;
				int strikeCount = map.getCount();
				String data = map.getData();

				// Check if to add '>'
				String prefix = "";
				if (i+1 >= list.size() || list.get(i+1).getCount() > strikeCount+1)
					// Last element or next element strikeCount>this+1
					prefix = ">";

				builder.append("`%3s` ".formatted(prefix+strikeCount));
				actions.forEach(action -> {
					switch (action) {
						case KICK -> builder.append(lu.getText(event, action.getPath()));
						case MUTE, BAN -> {
							Duration duration;
							try {
								duration = Duration.ofSeconds(Long.parseLong(action.getMatchedValue(data)));
							} catch (NumberFormatException ex) {
								break;
							}
							if (duration.isZero()) {
								builder.append("%s %s".formatted(lu.getText(event, action.getPath()), lu.getText(event, "misc.permanently")));
							}
							else
								builder.append("%s (%s)".formatted(lu.getText(event, action.getPath()), TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), duration)));
						}
						case REMOVE_ROLE, ADD_ROLE -> {
							long roleId;
							try {
								roleId = Long.parseLong(action.getMatchedValue(data));
							} catch (NumberFormatException ex) {
								break;
							}
							builder.append("%s (<@&%d>)".formatted(lu.getText(event, action.getPath()), roleId));
						}
						case TEMP_ROLE -> {
							long roleId;
							try {
								roleId = Long.parseLong(action.getMatchedValue(data, 1));
							} catch (NumberFormatException ex) {
								break;
							}
							Duration duration;
							try {
								duration = Duration.ofSeconds(Long.parseLong(action.getMatchedValue(data, 2)));
							} catch (NumberFormatException ex) {
								break;
							}
							builder.append("%s (<@&%d> %s)".formatted(lu.getText(event, action.getPath()), roleId, TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), duration)));
						}
					}
					builder.append(" ");
				});
				builder.append("\n");
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setDescription(builder.toString())
				.build());
		}
		
	}
	
}
