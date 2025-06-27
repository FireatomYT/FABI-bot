package dev.fireatom.FABI.listeners;

import dev.fireatom.FABI.base.command.MessageContextMenu;
import dev.fireatom.FABI.base.command.MessageContextMenuEvent;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.command.UserContextMenu;
import dev.fireatom.FABI.base.command.UserContextMenuEvent;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class CommandListener implements dev.fireatom.FABI.base.command.CommandListener {
	
	private final Logger LOGGER = (Logger) LoggerFactory.getLogger(CommandListener.class);
	private final LocaleUtil lu;

	public CommandListener(LocaleUtil lu) {
		this.lu = lu;
	}
	
	@Override
	public void onSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onCompletedSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onCompletedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onCompletedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand Terminated @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu Terminated @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu Terminated @ {}", event.getResponseNumber());
	}

	@Override
	public void onSlashCommandException(SlashCommandEvent event, SlashCommand command, Throwable t) {
		LOGGER.error("SlashCommand Exception", t);
		if (event.isAcknowledged())
			event.getHook().sendMessageEmbeds(getErrorEmbed(event, t)).setEphemeral(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
		else
			event.replyEmbeds(getErrorEmbed(event, t)).setEphemeral(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

	private MessageEmbed getErrorEmbed(SlashCommandEvent event, Throwable t) {
		return new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(event.getUserLocale(), "errors.title"))
			.setDescription(lu.getLocalized(event.getUserLocale(), "errors.unknown"))
			.addField(lu.getLocalized(event.getUserLocale(), "errors.additional"), MessageUtil.limitString(t.getMessage(), 1024), false)
			.build();
	}
}
