package dev.fireatom.FABI.commands.role;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.exception.FormatterException;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;

public class TempRoleCmd extends CommandBase {

	private final int MAX_DAYS = 150;
	
	public TempRoleCmd() {
		this.name = "temprole";
		this.path = "bot.roles.temprole";
		this.children = new SlashCommand[]{new Assign(), new Cancel(), new Extend(), new View()};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Assign extends SlashCommand {
		public Assign() {
			this.name = "assign";
			this.path = "bot.roles.temprole.assign";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"), true),
				new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(role.getAsMention(), denyReason));
				return;
			}
			// Check if role whitelisted
			if (bot.getDBUtil().getGuildSettings(guild).isRoleWhitelistEnabled()) {
				if (!bot.getDBUtil().roles.existsRole(role.getIdLong())) {
					// Not whitelisted
					editError(event, path+".not_whitelisted", "Role: %s".formatted(role.getAsMention()));
					return;
				}
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			if (member.isOwner() || member.getUser().isBot()) {
				editError(event, path+".incorrect_user");
				return;
			}
			
			// Check if already added
			long roleId = role.getIdLong();
			long userId = member.getIdLong();
			if (bot.getDBUtil().tempRoles.expireAt(roleId, userId) != null) {
				editError(event, path+".already_set");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = TimeUtil.stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			if (duration.toMinutes() < 10 || duration.toDays() > MAX_DAYS) {
				editError(event, path+".time_limit", "Received: "+duration);
				return;
			}

			boolean delete = event.optBoolean("delete", false);
			if (delete && !event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
				editPermError(event, Permission.MANAGE_ROLES, false);
				return;
			}
			Instant until = Instant.now().plus(duration);

			guild.addRoleToMember(member, role).reason("Assigned temporary role | by %s".formatted(event.getMember().getEffectiveName())).queue(done -> {
				try {
					bot.getDBUtil().tempRoles.add(guild.getIdLong(), roleId, userId, delete, until);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "assign temp role");
					return;
				}
				// Log
				bot.getLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), role, duration);
				// Send reply
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
						.replace("{until}", TimeUtil.formatTime(until, true)))
					.build()
				);
			}, failure -> editErrorOther(event, failure.getMessage()));
		}
	}

	private class Cancel extends SlashCommand {
		public Cancel() {
			this.name = "cancel";
			this.path = "bot.roles.temprole.cancel";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			// Check time
			Instant time = bot.getDBUtil().tempRoles.expireAt(role.getIdLong(), member.getIdLong());
			if (time == null) {
				editError(event, path+".not_found");
				return;
			}

			event.getGuild().removeRoleFromMember(member, role).reason("Canceled temporary role | by "+event.getMember().getEffectiveName()).queue();

			try {
				bot.getDBUtil().tempRoles.remove(role.getIdLong(), member.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove temp role");
				return;
			}
			// Log
			bot.getLogger().role.onTempRoleRemoved(event.getGuild(), event.getUser(), member.getUser(), role);
			// Send reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class Extend extends SlashCommand {
		public Extend() {
			this.name = "extend";
			this.path = "bot.roles.temprole.extend";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			// Check time
			Instant previousTime = bot.getDBUtil().tempRoles.expireAt(role.getIdLong(), member.getIdLong());
			if (previousTime == null) {
				editError(event, path+".not_found");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = TimeUtil.stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			Instant until = previousTime.plus(duration);
			if (duration.toMinutes() < 10 || until.isAfter(Instant.now().plus(MAX_DAYS, ChronoUnit.DAYS))) {
				editError(event, path+".time_limit", "New duration: %s days".formatted(Duration.between(Instant.now(), until).toDays()));
				return;
			}

			try {
				bot.getDBUtil().tempRoles.updateTime(role.getIdLong(), member.getIdLong(), until);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "update temp role duration");
				return;
			}
			// Log
			bot.getLogger().role.onTempRoleUpdated(event.getGuild(), event.getUser(), member.getUser(), role, until);
			// Send reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
					.replace("{until}", TimeUtil.formatTime(until, true)))
				.build()
			);
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.roles.temprole.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();
			List<Map<String, Object>> list = bot.getDBUtil().tempRoles.getAll(guild.getIdLong());
			if (list.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty")).build());
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setTitle(lu.getText(event, path+".title"));
			StringBuffer buffer = new StringBuffer();

			list.forEach(data -> {
				String line = getLine(data);
				if (buffer.length() + line.length() > 1024) {
					builder.addField("", buffer.toString(), false);
					buffer.setLength(0);
				}
				buffer.append(line);
			});
			builder.addField("", buffer.toString(), false);

			editEmbed(event, builder.build());
		}

		private String getLine(Map<String, Object> map) {
			Instant time = Instant.ofEpochSecond((Integer) map.get("expireAfter"));
			return "<@&%s> | <@%s> | %s\n".formatted(map.get("roleId"), map.get("userId"), TimeFormat.DATE_TIME_SHORT.format(time));
		}
	}

}
