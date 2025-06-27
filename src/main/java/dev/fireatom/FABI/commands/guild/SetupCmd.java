package dev.fireatom.FABI.commands.guild;

import java.awt.Color;
import java.net.URI;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.GuildSettingsManager.ModerationInformLevel;
import dev.fireatom.FABI.utils.database.managers.LevelManager;
import dev.fireatom.FABI.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class SetupCmd extends CommandBase {

	public SetupCmd() {
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new PanelColor(), new AppealLink(), new ReportChannel(),
			new VoiceCreate(), new VoiceSelect(), new VoicePanel(), new VoiceName(), new VoiceLimit(),
			new Strikes(), new InformLevel(), new RoleWhitelist(), new Levels()
		};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class PanelColor extends SlashCommand {
		public PanelColor() {
			this.name = "color";
			this.path = "bot.guild.setup.color";
			this.options = List.of(
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"), true).setRequiredLength(5, 11)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			String text = event.optString("color");

			Color color = MessageUtil.getColor(text);
			if (color == null) {
				editError(event, path+".no_color");
				return;
			}
			try {
				bot.getDBUtil().guildSettings.setColor(guildId, color.getRGB() & 0xFFFFFF);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set guild color");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(color.getRGB())
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
				.build());
		}
	}

	private class AppealLink extends SlashCommand {
		public AppealLink() {
			this.name = "appeal";
			this.path = "bot.guild.setup.appeal";
			this.options = List.of(
				new OptionData(OptionType.STRING, "link", lu.getText(path+".link.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			String text = event.optString("link");

			if (!isValidURL(text)) {
				editError(event, path+".not_valid", "Received invalid URL: `%s`".formatted(text));
				return;
			}

			try {
				bot.getDBUtil().guildSettings.setAppealLink(guildId, text);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set guild appeal link");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{link}", text))
				.build());
		}

		private boolean isValidURL(String urlString) {
			try {
				URI ignored = URI.create(urlString);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	private class ReportChannel extends SlashCommand {
		public ReportChannel() {
			this.name = "report";
			this.path = "bot.guild.setup.report";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			MessageChannel channel = event.optMessageChannel("channel");

			if (!channel.canTalk()) {
				editError(event, path+".cant_send");
				return;
			}

			try {
				bot.getDBUtil().guildSettings.setReportChannelId(guildId, channel.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set guild report channel");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}
	}

	private class VoiceCreate extends SlashCommand {
		public VoiceCreate() {
			this.name = "create";
			this.path = "bot.guild.setup.voice.create";
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();

			try {
				guild.createCategory(lu.getLocalized(event.getGuildLocale(), path+".category_name"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(getBotPermissions()), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(lu.getLocalized(event.getGuildLocale(), path+".channel_name"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											try {
												bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong());
											} catch (SQLException ex) {
												editErrorDatabase(event, ex, "setup voice");
												return;
											}
											editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
												.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
												.build());
										}
									);
							} catch (InsufficientPermissionException ex) {
								editPermError(event, ex.getPermission(), true);
							}
						}
					);
			} catch (InsufficientPermissionException ex) {
				editPermError(event, ex.getPermission(), true);
			}
		}
	}

	private class VoiceSelect extends SlashCommand {
		public VoiceSelect() {
			this.name = "select";
			this.path = "bot.guild.setup.voice.select";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.name"), true)
					.setChannelTypes(ChannelType.VOICE)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();

			VoiceChannel channel = (VoiceChannel) event.optGuildChannel("channel");
			Category category = channel.getParentCategory();
			if (category == null) {
				editError(event, path+".no_category");
				return;
			}

			try {
				category.upsertPermissionOverride(guild.getBotRole()).setAllowed(getBotPermissions()).queue(doneCategory ->
					channel.upsertPermissionOverride(guild.getPublicRole()).setDenied(Permission.VOICE_SPEAK).queue(doneChannel -> {
						try {
							bot.getDBUtil().guildVoice.setup(guildId, category.getIdLong(), channel.getIdLong());
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "setup voice");
							return;
						}
						editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
							.build());
					})
				);
			} catch (InsufficientPermissionException ex) {
				editPermError(event, ex.getPermission(), true);
			}
		}
	}

	private class VoicePanel extends SlashCommand {
		public VoicePanel() {
			this.name = "panel";
			this.path = "bot.guild.setup.voice.panel";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			MessageChannel channel = event.optMessageChannel("channel");
			if (channel == null || !channel.canTalk()) {
				editError(event, path+".no_channel", "Received: "+(channel == null ? "No channel" : channel.getAsMention()));
				return;
			}

			Button lock = Button.danger("voice:lock", lu.getLocalized(event.getGuildLocale(), path+".lock")).withEmoji(Emoji.fromUnicode("🔒"));
			Button unlock = Button.success("voice:unlock", lu.getLocalized(event.getGuildLocale(), path+".unlock")).withEmoji(Emoji.fromUnicode("🔓"));
			Button ghost = Button.danger("voice:ghost", lu.getLocalized(event.getGuildLocale(), path+".ghost")).withEmoji(Emoji.fromUnicode("👻"));
			Button unghost = Button.success("voice:unghost", lu.getLocalized(event.getGuildLocale(), path+".unghost")).withEmoji(Emoji.fromUnicode("👁️"));
			Button permit = Button.success("voice:permit", lu.getLocalized(event.getGuildLocale(), path+".permit")).withEmoji(Emoji.fromUnicode("➕"));
			Button reject = Button.danger("voice:reject", lu.getLocalized(event.getGuildLocale(), path+".reject")).withEmoji(Emoji.fromUnicode("➖"));
			Button perms = Button.secondary("voice:perms", lu.getLocalized(event.getGuildLocale(), path+".perms")).withEmoji(Emoji.fromUnicode("⚙️"));
			Button delete = Button.danger("voice:delete", lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));

			ActionRow row1 = ActionRow.of(unlock, lock);
			ActionRow row2 = ActionRow.of(unghost, ghost);
			ActionRow row4 = ActionRow.of(permit, reject, perms);
			ActionRow row5 = ActionRow.of(delete);

			Long channelId = bot.getDBUtil().getVoiceSettings(event.getGuild()).getChannelId();
			channel.sendMessageEmbeds(new EmbedBuilder()
				.setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(event.getGuildLocale(), path+".embed_title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), path+".embed_value").replace("{id}", String.valueOf(channelId)))
				.build()
			).addComponents(row1, row2, row4, row5).queue();

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
				.build());
		}
	}

	private class VoiceName extends SlashCommand {
		public VoiceName() {
			this.name = "name";
			this.path = "bot.guild.setup.voice.name";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(100)
			);
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			String filName = event.optString("name", lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name"));

			if (filName.isBlank()) {
				editError(event, path+".invalid_range");
				return;
			}

			try {
				bot.getDBUtil().guildVoice.setName(event.getGuild().getIdLong(), filName);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set default voice name");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filName))
				.build());
		}
	}

	private class VoiceLimit extends SlashCommand {
		public VoiceLimit() {
			this.name = "limit";
			this.path = "bot.guild.setup.voice.limit";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".limit.help"), true)
					.setRequiredRange(0, 99)
			);
			this.subcommandGroup = new SubcommandGroupData("voice", lu.getText("bot.guild.setup.voice.help"));
			this.module = CmdModule.VOICE;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer filLimit = event.optInteger("limit");

			try {
				bot.getDBUtil().guildVoice.setLimit(event.getGuild().getIdLong(), filLimit);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set default voice limit");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filLimit.toString()))
				.build());
		}
	}

	private class Strikes extends SlashCommand {
		public Strikes() {
			this.name = "strikes";
			this.path = "bot.guild.setup.strikes";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "expires_after", lu.getText(path+".expires_after.help"))
					.setRequiredRange(1, 30),
				new OptionData(OptionType.INTEGER, "cooldown", lu.getText(path+".cooldown.help"))
					.setRequiredRange(0, 30)
			);
			this.module = CmdModule.STRIKES;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			if (event.getOptions().size() < 2) {
				editError(event, path+".no_options");
				return;
			}

			StringBuilder builder = new StringBuilder(lu.getText(event, path+".embed_title"));
			Integer expiresAfter = event.optInteger("expires_after");
			if (expiresAfter != null) {
				try {
					bot.getDBUtil().guildSettings.setStrikeExpiresAfter(event.getGuild().getIdLong(), expiresAfter);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set guild strike expires");
					return;
				}
				builder.append(lu.getText(event, path+".expires_changed").formatted(expiresAfter));
			}
			Integer cooldown = event.optInteger("cooldown");
			if (cooldown != null) {
				try {
					bot.getDBUtil().guildSettings.setStrikeCooldown(event.getGuild().getIdLong(), cooldown);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set guild strike cooldown");
					return;
				}
				builder.append(lu.getText(event, path+".cooldown_changed").formatted(cooldown));
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(builder.toString())
				.build());
		}
	}

	private class InformLevel extends SlashCommand {
		public InformLevel() {
			this.name = "dm_inform";
			this.path = "bot.guild.setup.dm_inform";
			this.options = List.of(
					new OptionData(OptionType.STRING, "action", lu.getText(path+".action.help"), true)
							.addChoice("Ban", "ban")
							.addChoice("Kick", "kick")
							.addChoice("Mute", "mute")
							.addChoice("Strike", "strike")
							.addChoice("Delstrike", "delstrike"),
					new OptionData(OptionType.INTEGER, "level", lu.getText(path+".level.help"), true)
							.addChoices(ModerationInformLevel.asChoices(lu))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();

			String action = event.optString("action");
			ModerationInformLevel informLevel = ModerationInformLevel.byLevel(event.optInteger("level"));
			try {
				switch (action) {
					case "ban" -> bot.getDBUtil().guildSettings.setInformBanLevel(guildId, informLevel);
					case "kick" -> bot.getDBUtil().guildSettings.setInformKickLevel(guildId, informLevel);
					case "mute" -> bot.getDBUtil().guildSettings.setInformMuteLevel(guildId, informLevel);
					case "strike" -> bot.getDBUtil().guildSettings.setInformStrikeLevel(guildId, informLevel);
					case "delstrike" -> bot.getDBUtil().guildSettings.setInformDelstrikeLevel(guildId, informLevel);
					default -> {
						editError(event, path+".unknown", action);
						return;
					}
				}
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set inform level");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(action, lu.getText(event, informLevel.getPath())))
					.build());
		}
	}

	private class RoleWhitelist extends SlashCommand {
		public RoleWhitelist() {
			this.name = "role_whitelist";
			this.path = "bot.guild.setup.role_whitelist";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enable", lu.getText(path+".enable.help"), true)
			);
		}
		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			boolean enabled = event.optBoolean("enable");
			// DB
			try {
				bot.getDBUtil().guildSettings.setRoleWhitelist(event.getGuild().getIdLong(), enabled);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "enable role whitelist");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(enabled?Constants.SUCCESS:Constants.FAILURE))
				.build());
		}
	}

	private class Levels extends SlashCommand {
		public Levels() {
			this.name = "levels";
			this.path = "bot.guild.setup.levels";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enable", lu.getText(path+".enable.help")),
				new OptionData(OptionType.BOOLEAN, "voice_enable", lu.getText(path+".voice_enable.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			StringBuilder response = new StringBuilder();

			if (event.getOptions().isEmpty()) {
				event.deferReply(true).queue();
				// Return overview
				LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());

				response.append("\n> Leveling enabled: ").append(settings.isEnabled()?Constants.SUCCESS:Constants.FAILURE);
				response.append("\n> Grant xp for voice activity: ").append(settings.isVoiceEnabled()?Constants.SUCCESS:Constants.FAILURE);

				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".embed_view"))
					.appendDescription(response.toString())
					.build()
				);
			} else {
				event.deferReply().queue();
				// Edit settings
				if (event.hasOption("enable")) {
					final boolean enabled = event.optBoolean("enable");

					try {
						bot.getDBUtil().levels.setEnabled(event.getGuild().getIdLong(), enabled);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "leveling settings set enabled");
						return;
					}
					response.append(lu.getText(event, path+".changed_enabled").formatted(enabled ? Constants.SUCCESS : Constants.FAILURE));
				}
				if (event.hasOption("voice_enable")) {
					final boolean enabled = event.optBoolean("voice_enable");

					try {
						bot.getDBUtil().levels.setVoiceEnabled(event.getGuild().getIdLong(), enabled);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "leveling settings set voice enabled");
						return;
					}
					response.append(lu.getText(event, path+".changed_voice").formatted(enabled ? Constants.SUCCESS : Constants.FAILURE));
				}

				if (response.isEmpty()) {
					editErrorUnknown(event, "Response for ticket settings is empty.");
				} else {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".embed_changes"))
						.appendDescription(response.toString())
						.build()
					);
				}
			}
		}
	}

}
