package dev.fireatom.FABI.listeners;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.logs.LogType;
import dev.fireatom.FABI.objects.logs.MessageData;
import dev.fireatom.FABI.utils.CastUtil;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class MessageListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(MessageListener.class);

	// Cache
	private final Cache<Long, MessageData> cache = Caffeine.newBuilder()
		.expireAfterWrite(5, TimeUnit.DAYS)
		.maximumSize(5000)
		.build();

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return; //ignore bots and Private messages
		
		// cache message if not exception channel
		final long guildId = event.getGuild().getIdLong();
		if (bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) {
			// check channel
			if (!bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) {
				// check category
				long categoryId = switch (event.getChannelType()) {
					case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
					case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
						.asStandardGuildChannel().getParentCategoryIdLong();
					default -> 0;
				};
				if (categoryId == 0 || !bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
					cache.put(event.getMessageIdLong(), new MessageData(event.getMessage()));
				}
			}
		}

		// reward player
		bot.getLevelUtil().rewardMessagePlayer(event);
	}

	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
			return;
		}
		
		final long messageId = event.getMessageIdLong();
		MessageData oldData = cache.getIfPresent(messageId);
		MessageData newData = new MessageData(event.getMessage());
		cache.put(event.getMessageIdLong(), newData);

		bot.getLogger().message.onMessageUpdate(event.getMember(), event.getGuildChannel(), messageId, oldData, newData);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long messageId = event.getMessageIdLong();

		MessageData data = cache.getIfPresent(messageId);
		if (data != null) cache.invalidate(messageId);

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
			return;
		}

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_DELETE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty() && data != null) {
					AuditLogEntry entry = list.getFirst();
					if (entry.getTargetIdLong() == data.getAuthorId() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(10))) {
						bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, entry.getUserIdLong());
						return;
					}
				}
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			},
			failure -> {
				log.warn("Failed to queue audit log for message deletion.", failure);
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			});
	}

	@Override
	public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final List<Long> messageIds = event.getMessageIds().stream().map(CastUtil::castLong).toList();
		if (messageIds.isEmpty()) return;

		List<MessageData> messages = new ArrayList<>();
		cache.getAllPresent(messageIds).forEach((k, v) -> {
			messages.add(v);
			cache.invalidate(k);
		});
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_BULK_DELETE)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) {
					bot.getLogger().message.onMessageBulkDelete(event.getChannel(), String.valueOf(messageIds.size()), messages, null);
				} else {
					AuditLogEntry entry = list.getFirst();
					String count = entry.getOption(AuditLogOption.COUNT);
					if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(10)))
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, entry.getUserIdLong());
					else
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, null);
				}
			});
	}

}
