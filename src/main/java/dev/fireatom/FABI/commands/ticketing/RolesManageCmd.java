package dev.fireatom.FABI.commands.ticketing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.RoleType;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import dev.fireatom.FABI.utils.CastUtil;
import dev.fireatom.FABI.utils.database.managers.RoleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.Checks;

public class RolesManageCmd extends CommandBase {
	
	public RolesManageCmd() {
		this.name = "rolesmanage";
		this.path = "bot.ticketing.rolesmanage";
		this.children = new SlashCommand[]{new Add(), new Update(), new Remove(), new View()};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.ticketing.rolesmanage.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(List.of(
						new Choice(lu.getText(RoleType.ASSIGN.getPath()), RoleType.ASSIGN.toString()),
						new Choice(lu.getText(RoleType.TOGGLE.getPath()), RoleType.TOGGLE.toString()),
						new Choice(lu.getText(RoleType.CUSTOM.getPath()), RoleType.CUSTOM.toString())
					)),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.BOOLEAN, "timed", lu.getText(path+".timed.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			long guildId = event.getGuild().getIdLong();
			
			Role role = event.optRole("role");
			if (role == null || role.hasPermission(Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER)) {
				editError(event, path+".no_role");
				return;
			}
			if (!event.getGuild().getSelfMember().canInteract(role)) {
				editError(event, path+".cant_interact");
				return;
			}
			long roleId = role.getIdLong();
			if (bot.getDBUtil().roles.existsRole(roleId)) {
				editError(event, path+".exists");
				return;
			}
			
			String type = event.optString("type");
			if (type.equals(RoleType.ASSIGN.toString())) {
				int row = event.optInteger("row", 0);
				if (row == 0) {
					for (int i = 1; i <= 3; i++) {
						if (bot.getDBUtil().roles.getRowSize(guildId, i) < 25) {
							row = i;
							break;
						}
					}
					if (row == 0) {
						editError(event, path+".rows_max");
						return;
					}
				} else {
					if (bot.getDBUtil().roles.getRowSize(guildId, row) >= 25) {
						editError(event, path+".row_max", "Row: %s".formatted(row));
						return;
					}
				}
				boolean timed = event.optBoolean("timed", false);
				try {
					bot.getDBUtil().roles.add(guildId, roleId, event.optString("description", "NULL"), row, RoleType.ASSIGN, timed);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "add managed role");
					return;
				}
				sendSuccess(event, lu.getText(event, RoleType.ASSIGN.getPath()), role);
			} else if (type.equals(RoleType.TOGGLE.toString())) {
				if (bot.getDBUtil().roles.getToggleable(guildId).size() >= 5) {
					editError(event, path+".toggle_max");
					return;
				}
				String description = event.optString("description", role.getName());
				try {
					bot.getDBUtil().roles.add(guildId, roleId, description, null, RoleType.TOGGLE, false);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "add managed role");
					return;
				}
				sendSuccess(event, lu.getText(event, RoleType.ASSIGN.getPath()), role);
			} else if (type.equals(RoleType.CUSTOM.toString())) {
				if (bot.getDBUtil().roles.getCustom(guildId).size() >= 25) {
					editError(event, path+".custom_max");
					return;
				}
				try {
					bot.getDBUtil().roles.add(guildId, roleId, event.optString("description", null), null, RoleType.CUSTOM, false);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "add managed role");
					return;
				}
				sendSuccess(event, lu.getText(event, RoleType.ASSIGN.getPath()), role);
			} else {
				editError(event, path+".no_type");
			}
		}

		private void sendSuccess(SlashCommandEvent event, String type, Role role) {
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention(), type))
				.build());
		}
	}

	private class Update extends SlashCommand {
		public Update() {
			this.name = "update";
			this.path = "bot.ticketing.rolesmanage.update";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.BOOLEAN, "timed", lu.getText(path+".timed.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			final Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			final long roleId = role.getIdLong();
			if (!bot.getDBUtil().roles.existsRole(roleId)) {
				editError(event, path+".not_exists");
				return;
			}
			final RoleType roleType = bot.getDBUtil().roles.getType(roleId);

			StringBuffer response = new StringBuffer();

			if (event.hasOption("description")) {
				String description = event.optString("description");

				if (description == null || description.equalsIgnoreCase("null")) {
					if (roleType.equals(RoleType.TOGGLE))
						description = role.getName();
					else
						description = null;
					response.append(lu.getText(event, path+".default_description"));
				} else {
					response.append(lu.getText(event, path+".changed_description").formatted(description));
				}
				try {
					bot.getDBUtil().roles.setDescription(roleId, description);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "update role description");
					return;
				}
			}

			if (event.hasOption("row") && roleType.equals(RoleType.ASSIGN)) {
				final int row = event.optInteger("row");
				try {
					bot.getDBUtil().roles.setRow(roleId, row);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "update role row");
					return;
				}
				response.append(lu.getText(event, path+".changed_row").formatted(row));
			}

			if (event.hasOption("timed") && roleType.equals(RoleType.ASSIGN)) {
				final boolean timed = event.optBoolean("timed", false);
				try {
					bot.getDBUtil().roles.setTimed(roleId, timed);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "update role timed");
					return;
				}
				response.append(lu.getText(event, path+".changed_timed").formatted(timed?Constants.SUCCESS:Constants.FAILURE));
			}

			sendReply(event, response, role);
		}

		private void sendReply(SlashCommandEvent event, StringBuffer response, Role role) {
			if (response.isEmpty()) {
				editError(event, path+".no_options");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".embed_title").formatted(role.getAsMention()))
				.appendDescription(response.toString())
				.build());
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.ticketing.rolesmanage.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
					.setMaxLength(30)
			);
		}

		Pattern rolePattern = Pattern.compile("^<@&(\\d+)>$");

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			String input = event.optString("id").trim();

			Matcher matcher = rolePattern.matcher(input);
			String roleId = matcher.find() ? matcher.group(1) : input;
			try {
				Checks.isSnowflake(roleId);
			} catch (IllegalArgumentException e) {
				editError(event, path+".no_role", "ID: "+roleId);
				return;
			}
			long roleIdLong = CastUtil.castLong(roleId);

			if (!bot.getDBUtil().roles.existsRole(roleIdLong)) {
				editError(event, path+".no_role");
				return;
			}
			try {
				bot.getDBUtil().roles.remove(roleIdLong);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove managed role");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(roleId))
				.build());
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.ticketing.rolesmanage.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));
			
			for (RoleType type : RoleType.values()) {
				if (type.equals(RoleType.ASSIGN)) {
					for (int row = 1; row <= 3; row++) {
						List<RoleManager.RoleData> roles = bot.getDBUtil().roles.getAssignableByRow(guildId, row);
						String title = "%s-%s | %s".formatted(lu.getText(event, type.getPath()), row, bot.getDBUtil().getTicketSettings(guild).getRowText(row));
						if (roles.isEmpty()) {
							builder.addField(title, lu.getText(event, path+".none"), false);
						} else {
							generateField(guild, title, roles).forEach(builder::addField);
						}
					}
				} else {
					List<RoleManager.RoleData> roles = bot.getDBUtil().roles.getRolesByType(guildId, type);
					String title = lu.getText(event, type.getPath());
					if (roles.isEmpty()) {
						builder.addField(title, lu.getText(event, path+".none"), false);
					} else {
						generateField(guild, title, roles).forEach(builder::addField);
					}
				}
			}

			event.getHook().editOriginalEmbeds(builder.build()).queue();
		}

		private List<Field> generateField(final Guild guild, final String title, final List<RoleManager.RoleData> roles) {
			List<Field> fields = new ArrayList<>();
			StringBuffer buffer = new StringBuffer();
			roles.forEach(data -> {
				final Role role = guild.getRoleById(data.getIdLong());
				if (role == null) {
					try {
						bot.getDBUtil().roles.remove(data.getIdLong());
					} catch (SQLException ignored) {}
					return;
				}
				buffer.append(String.format("%s%s `%s` | %s\n",
					data.isTimed() ? "⏲️ " : "",
					role.getAsMention(),
					role.getId(),
					data.getDescription("-")
				));
				if (buffer.length() > 900) {
					fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
					buffer.setLength(0);
				}
			});
			if (!buffer.isEmpty()) {
				fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
			}
			return fields;
		}
	}

}
