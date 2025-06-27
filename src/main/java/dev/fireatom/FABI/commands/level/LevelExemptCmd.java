package dev.fireatom.FABI.commands.level;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.LevelManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LevelExemptCmd extends CommandBase {
	public LevelExemptCmd() {
		this.name = "level_exempt";
		this.path = "bot.level.level_exempt";
		this.children = new SlashCommand[]{
			new AddLevelExempt(), new RemoveLevelExempt(), new ClearLevelExempt(), new ViewLevelExempt()
		};
		this.category = CmdCategory.LEVELS;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class AddLevelExempt extends SlashCommand {
		public AddLevelExempt() {
			this.name = "add";
			this.path = "bot.level.level_exempt.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT, ChannelType.VOICE, ChannelType.CATEGORY, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.STAGE)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, path+".invalid_args");
				return;
			}

			LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());
			if (settings.getExemptChannels().size() >= 40) {
				editError(event, path+".limit");
				return;
			}
			if (settings.isExemptChannel(channel.getIdLong())) {
				editError(event, path+".already", channel.getAsMention());
				return;
			}

			Set<Long> channels = new HashSet<>(settings.getExemptChannels());
			channels.add(channel.getIdLong());
			String channelIds = channels.stream().map(String::valueOf).collect(Collectors.joining(";"));

			try {
				bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), channelIds);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
				.build()
			);
		}
	}

	private class RemoveLevelExempt extends SlashCommand {
		public RemoveLevelExempt() {
			this.name = "remove";
			this.path = "bot.level.level_exempt.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "channel", lu.getText(path+".channel.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			GuildChannel channel = null;
			long channelId;
			try {
				channel = event.optMentions("channel").getChannels().getFirst();
				channelId = channel.getIdLong();
			} catch (Exception ex) {
				try {
					channelId = event.optLong("channel");
				} catch (Exception ex2) {
					editError(event, path+".invalid_args");
					return;
				}
			}

			LevelManager.LevelSettings settings = bot.getDBUtil().levels.getSettings(event.getGuild());
			if (!settings.isExemptChannel(channelId)) {
				editError(event, path+".not_exempt", channel!=null ? channel.getAsMention() : String.valueOf(channelId));
				return;
			}

			Set<Long> channels = new HashSet<>(settings.getExemptChannels());
			channels.remove(channelId);
			String channelIds = channels.stream().map(String::valueOf).collect(Collectors.joining(";"));

			try {
				bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), channelIds);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel!=null ? channel.getAsMention() : channelId))
				.build()
			);
		}
	}

	private class ClearLevelExempt extends SlashCommand {
		public ClearLevelExempt() {
			this.name = "clear";
			this.path = "bot.level.level_exempt.clear";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			try {
				bot.getDBUtil().levels.setExemptChannels(event.getGuild().getIdLong(), null);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "clear level exempt channels");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class ViewLevelExempt extends SlashCommand {
		public ViewLevelExempt() {
			this.name = "view";
			this.path = "bot.level.level_exempt.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Set<Long> channelIds = bot.getDBUtil().levels.getSettings(event.getGuild()).getExemptChannels();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(path+".title"));
			if (channelIds.isEmpty()) {
				builder.setDescription(lu.getText(event, path+".empty"));
			} else {
				channelIds.forEach(id -> builder.appendDescription("<#%s> (%<s)\n".formatted(id)));
			}
			editEmbed(event, builder.build());
		}
	}
}
