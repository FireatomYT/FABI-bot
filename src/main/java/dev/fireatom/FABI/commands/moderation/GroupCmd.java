package dev.fireatom.FABI.commands.moderation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import dev.fireatom.FABI.base.command.CooldownScope;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class GroupCmd extends CommandBase {
	
	private static EventWaiter waiter;

	public GroupCmd(EventWaiter waiter) {
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{
			new Create(), new Delete(), new Remove(), new GenerateInvite(),
			new Join(), new Leave(), new Modify(), new Manage(), new View()
		};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		GroupCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help")).setRequiredLength(12, 20)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			if (bot.getDBUtil().group.getOwnedGroups(guildId).size() >= 3) {
				editError(event, path+".max_amount");
				return;
			}

			String groupName = event.optString("name");

			long appealGuildId = 0L;
			if (event.hasOption("appeal_server")) {
				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editErrorOther(event, "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}
			}

			int groupId = bot.getDBUtil().group.create(guildId, groupName, appealGuildId);
			if (groupId == 0) {
				editErrorOther(event, "Failed to create new group.");
				return;
			}
			bot.getLogger().group.onCreation(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName, groupId))
				.build()
			);
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(1)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.deleteGroup(groupId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete group");
				return;
			}
			bot.getLogger().group.onDeletion(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName, groupId))
				.build()
			);
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.moderation.group.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").formatted(groupName))
				.build();
			
			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
							StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
								.setPlaceholder("Select")
								.setMaxValues(1)
								.addOptions(options)
								.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
					)
				);
			}
			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().getFirst().getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					try {
						bot.getDBUtil().group.remove(groupId, targetId);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "remove group member");
						return;
					}
					if (targetGuild != null)
						bot.getLogger().group.onGuildRemoved(event, targetGuild, groupId, groupName);

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").formatted(Optional.ofNullable(targetGuild).map(Guild::getName).orElse("*Unknown*"), groupName))
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}
	}

	private class GenerateInvite extends SlashCommand {
		public GenerateInvite() {
			this.name = "generateinvite";
			this.path = "bot.moderation.group.generateinvite";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			int newInvite = ThreadLocalRandom.current().nextInt(100_000, 1_000_000); // 100000 - 999999

			try {
				bot.getDBUtil().group.setInvite(groupId, newInvite);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set group invite");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName, newInvite))
				.build()
			);
		}
	}

	private class Modify extends SlashCommand {
		public Modify() {
			this.name = "modify";
			this.path = "bot.moderation.group.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help")).setRequiredLength(12, 20)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			int groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			StringBuilder builder = new StringBuilder();

			if (event.hasOption("appeal_server")) {
				long appealGuildId;

				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editErrorOther(event, "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}

				try {
					bot.getDBUtil().group.setAppealGuildId(groupId, appealGuildId);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set appeal guild id");
					return;
				}

				builder.append(lu.getText(event, path+".changed_appeal").formatted(appealGuildId));
			}
			if (event.hasOption("name")) {
				String oldName = bot.getDBUtil().group.getName(groupId);
				String newName = event.optString("name");

				try {
					bot.getDBUtil().group.rename(groupId, newName);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "rename group");
					return;
				}
				bot.getLogger().group.onRenamed(event, oldName, groupId, newName);

				builder.append(lu.getText(event, path+".changed_name").formatted(oldName, newName));
			}

			if (builder.isEmpty()) {
				editError(event, path+".empty");
				return;
			}
			String groupName = bot.getDBUtil().group.getName(groupId);
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setTitle(lu.getText(path+".embed_title").formatted(groupName))
				.setDescription(builder.toString())
				.build()
			);
		}
	}

	private class Manage extends SlashCommand {
		public Manage() {
			this.name = "manage";
			this.path = "bot.moderation.group.manage";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			boolean canManage = event.optBoolean("manage", false);

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").formatted(groupName))
				.build();
			
			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
							StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
								.setPlaceholder("Select")
								.setMaxValues(1)
								.addOptions(options)
								.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
					)
				);
			}
			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().getFirst().getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					StringBuilder builder = new StringBuilder(lu.getText(event, path+".done")
						.formatted(targetGuild.getName(), groupName));

					try {
						bot.getDBUtil().group.setManage(groupId, targetId, canManage);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "set group manager");
						return;
					}
					builder.append(lu.getText(event, path+".manage_change").formatted(canManage ? Constants.SUCCESS : Constants.FAILURE));

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(builder.toString())
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}
	}

	private class Join extends SlashCommand {
		public Join() {
			this.name = "join";
			this.path = "bot.moderation.group.join";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "invite", lu.getText(path+".invite.help"), true)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 30;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer invite = event.optInteger("invite");
			Integer groupId = bot.getDBUtil().group.getGroupByInvite(invite);
			if (groupId == null) {
				editError(event, path+".no_group");
				return;
			}

			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (event.getGuild().getIdLong() == ownerId) {
				editError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.isMember(groupId, event.getGuild().getIdLong())) {
				editError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.add(groupId, event.getGuild().getIdLong(), false);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add group member");
				return;
			}
			bot.getLogger().group.onGuildJoined(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName))
				.build()
			);
		}		
	}

	private class Leave extends SlashCommand {
		public Leave() {
			this.name = "leave";
			this.path = "bot.moderation.group.leave";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer groupId = event.optInteger("group_joined");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, event.getGuild().getIdLong())) {
				editError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.remove(groupId, event.getGuild().getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove group member");
				return;
			}
			bot.getLogger().group.onGuildLeft(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName))
				.build()
			);
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.moderation.group.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), false, true).setMinValue(0),
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), false, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			long guildId = event.getGuild().getIdLong();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("group_owned");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null) {
					editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
					return;
				}
				if (event.getGuild().getIdLong() != ownerId) {
					editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<Long> memberIds = bot.getDBUtil().group.getGroupMembers(groupId);
				int groupSize = memberIds.size();
				String invite = Optional.ofNullable(bot.getDBUtil().group.getInvite(groupId)).map(o -> "||`"+o+"`||").orElse("-");

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, path+".embed_title").formatted(
						groupName, groupId
					))
					.setDescription(lu.getText(event, path+".embed_full").formatted(
						event.getGuild().getName(), event.getGuild().getId(), groupSize,
						Optional.ofNullable(bot.getDBUtil().group.getAppealGuildId(groupId)).map(String::valueOf).orElse("-")
					))
					.addField(lu.getText(event, path+".embed_invite"), invite, false);
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuilder stringBuilder = new StringBuilder();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) continue;
	
						String line = format.formatted(guild.getName(), guild.getMemberCount(), guild.getId());
						if (stringBuilder.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, stringBuilder.toString(), false);
							stringBuilder.setLength(0);
							stringBuilder.append(line).append("\n");
							fieldLabel = "";
						} else {
							stringBuilder.append(line).append("\n");
						}
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}
				editEmbed(event, builder.build());
			} else if (event.hasOption("group_joined")) {
				// View joined Group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("group_joined");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, guildId)) {
					editError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(ownerId).getName();
				int groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, "logger.groups.title").formatted(groupName, groupId))
					.setDescription(lu.getText(event, path+".embed_short").formatted(
						masterName, ownerId, groupSize
					));
				editEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Integer> ownedGroups = bot.getDBUtil().group.getOwnedGroups(guildId);
				List<Integer> joinedGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setDescription("Group name | #ID");
				
				String fieldLabel = lu.getText(event, path+".embed_owned");
				if (ownedGroups.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : ownedGroups) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				fieldLabel = lu.getText(event, path+".embed_member");
				if (joinedGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : joinedGroupIds) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				editEmbed(event, builder.build());
			}
		}

	}

}
