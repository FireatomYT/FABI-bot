package dev.fireatom.FABI.commands.guild;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import dev.fireatom.FABI.objects.constants.Constants;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;

public class PersistentRoleCmd extends CommandBase {

	public PersistentRoleCmd() {
		this.name = "persistent";
		this.path = "bot.guild.persistent";
		this.children = new SlashCommand[]{new AddRole(), new RemoveRole(), new View()};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class AddRole extends SlashCommand {
		public AddRole() {
			this.name = "add";
			this.path = "bot.guild.persistent.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
		}

		private final EnumSet<Permission> managerPerms = EnumSet.of(
			Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MESSAGE_MANAGE, Permission.BAN_MEMBERS,
			Permission.KICK_MEMBERS, Permission.MANAGE_SERVER, Permission.NICKNAME_MANAGE
		);

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			final Role role = event.optRole("role");
			// Check roles
			final Role publicRole = event.getGuild().getPublicRole();
			EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
			rolePerms.retainAll(managerPerms);
			if (role.equals(publicRole) || role.isManaged() || !event.getMember().canInteract(role) || !event.getGuild().getSelfMember().canInteract(role) || !rolePerms.isEmpty()) {
				editError(event, path+".incorrect_role", "Role: "+role.getAsMention());
				return;
			}

			final int size = bot.getDBUtil().persistent.getRoles(event.getGuild().getIdLong()).size();
			if (size >= 3) {
				editError(event, path+".limit", "Maximum 3 roles.");
				return;
			}

			try {
				bot.getDBUtil().persistent.addRole(event.getGuild().getIdLong(), role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add persistent role");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention()))
				.build());
		}
	}

	private class RemoveRole extends SlashCommand {
		public RemoveRole() {
			this.name = "remove";
			this.path = "bot.guild.persistent.remove";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Role role = event.optRole("role");

			try {
				bot.getDBUtil().persistent.removeRole(event.getGuild().getIdLong(), role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove persistent role");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention()))
				.build());
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.guild.persistent.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			final List<Long> roleIds = bot.getDBUtil().persistent.getRoles(event.getGuild().getIdLong());
			if (roleIds.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
				return;
			}

			StringBuilder sb = new StringBuilder();
			for (Long roleId : roleIds) {
				Role role = event.getGuild().getRoleById(roleId);
				if (role == null) {
					try {
						bot.getDBUtil().persistent.removeRole(event.getGuild().getIdLong(), roleId);
					} catch (SQLException ignored) {}
					continue;
				}
				sb.append(role.getAsMention()).append("\n");
			}
			if (sb.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
			} else {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setTitle(lu.getText(event, path+".title"))
					.setDescription(sb.toString())
					.build());
			}
		}
	}

}
