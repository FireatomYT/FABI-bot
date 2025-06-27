package dev.fireatom.FABI.utils.logs;

import static dev.fireatom.FABI.utils.CastUtil.castLong;
import static dev.fireatom.FABI.utils.message.TimeUtil.formatTime;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.ExpType;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.logs.LogEvent;
import dev.fireatom.FABI.objects.logs.MessageData;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.MessageUtil;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild.ExplicitContentLevel;
import net.dv8tion.jda.api.entities.Guild.MFALevel;
import net.dv8tion.jda.api.entities.Guild.NotificationLevel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import com.jayway.jsonpath.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"FieldCanBeLocal", "UnusedReturnValue"})
public class LogEmbedUtil {

	private final @NotNull LocaleUtil lu;

	private final int GREEN_DARK = 0x277236;
	private final int GREEN_LIGHT = 0x67CB7B;
	private final int AMBER_DARK = 0xCA8B02;
	private final int AMBER_LIGHT = 0xFDBE35;
	private final int RED_DARK = 0xB31E22;
	private final int RED_LIGHT = 0xCC6666;
	private final int WHITE = 0xFFFFFF;
	private final int DEFAULT = Constants.COLOR_DEFAULT;

	public LogEmbedUtil() {
		this.lu = App.getInstance().getLocaleUtil();
	}

	private String localized(DiscordLocale locale, String pathFooter) {
		return lu.getLocalized(locale, "logger."+pathFooter);
	}

	private class LogEmbedBuilder {

		private final DiscordLocale locale;
		private final EmbedBuilder builder;

		public LogEmbedBuilder(DiscordLocale locale) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(DEFAULT).setTimestamp(Instant.now());
		}

		public LogEmbedBuilder(DiscordLocale locale, int color) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(color).setTimestamp(Instant.now());
		}

		public LogEmbedBuilder(DiscordLocale locale, Instant timestamp) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(DEFAULT).setTimestamp(timestamp);
		}

		public LogEmbedBuilder setId(Object id) {
			builder.setFooter("ID: "+id);
			return this;
		}

		public LogEmbedBuilder setHeader(String path) {
			builder.setAuthor(localized(locale, path));
			return this;
		}

		public LogEmbedBuilder setHeader(String path, Object... args) {
			builder.setAuthor(localized(locale, path).formatted(args));
			return this;
		}

		public LogEmbedBuilder setHeaderIcon(String path, String iconUrl, Object... args) {
			try {
				builder.setAuthor(localized(locale, path).formatted(args), null, iconUrl);
			} catch(IllegalArgumentException ex) {
				if (ex.getMessage() != null && ex.getMessage().contains("URL must be a valid"))
					builder.setAuthor(localized(locale, path).formatted(args));
				else
					throw ex;
			}
			return this;
		}

		public LogEmbedBuilder setHeader(LogEvent logEvent, Object... args) {
			builder.setAuthor(lu.getLocalized(locale, logEvent.getPath()).formatted(args));
			return this;
		}

		public LogEmbedBuilder setHeaderIcon(LogEvent logEvent, String iconUrl, Object... args) {
			try {
				builder.setAuthor(lu.getLocalized(locale, logEvent.getPath()).formatted(args), null, iconUrl);
			} catch(IllegalArgumentException ex) {
				if (ex.getMessage() != null && ex.getMessage().contains("URL must be a valid"))
					builder.setAuthor(lu.getLocalized(locale, logEvent.getPath()).formatted(args));
				else
					throw ex;
			}
			return this;
		}

		public LogEmbedBuilder addEmptyField(String value) {
			builder.addField("", value, false);
			return this;
		}

		public LogEmbedBuilder addField(String path, String value) {
			return addField(path, value, true);
		}

		public LogEmbedBuilder addField(String path, String value, boolean inline) {
			if (value != null)
				builder.addField(localized(locale, path), value, inline);
			return this;
		}

		public LogEmbedBuilder setUser(Long userId) {
			return addField("user", userId == null ? "-" : "<@"+userId+">");
		}

		public LogEmbedBuilder setMod(Long modId) {
			return addField("moderation.mod", modId == null ? "-" : "<@"+modId+">");
		}

		public LogEmbedBuilder setEnforcer(Long userId) {
			return setEnforcer(userId == null ? "-" : "<@"+userId+">");
		}

		public LogEmbedBuilder setEnforcer(String userMention) {
			return addField("enforcer", userMention);
		}

		public LogEmbedBuilder setReason(String reason) {
			return addField("reason", reason == null ? "-" : reason);
		}

		public LogEmbedBuilder setReasonNull(String reason) {
			return addField("reason", reason);
		}

		public LogEmbedBuilder setColor(int color) {
			builder.setColor(color);
			return this;
		}

		public LogEmbedBuilder setFooter(String text) {
			builder.setFooter(text);
			return this;
		}

		public LogEmbedBuilder setTitle(String path) {
			builder.setTitle(localized(locale, path));
			return this;
		}

		public LogEmbedBuilder setDescription(String text) {
			builder.setDescription(text);
			return this;
		}

		public LogEmbedBuilder appendDescription(String text) {
			builder.appendDescription(text);
			return this;
		}

		public LogEmbedBuilder setImage(String url) {
			builder.setImage(url);
			return this;
		}

		public LogEmbedBuilder addProof(String fileName) {
			if (fileName!=null) builder.setImage("attachment://"+fileName);
			return this;
		}

		public MessageEmbed build() {
			return builder.build();
		}
	}

	// Moderation
	@NotNull
	private LogEmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData) {
		return moderationEmbedBuilder(locale, caseData, null);
	}

	@NotNull
	private LogEmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData, String userIcon) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, caseData.getTimeStart())
			.setHeaderIcon("moderation.case", userIcon, caseData.getLocalId(), lu.getLocalized(locale, caseData.getType().getPath()), caseData.getTargetTag())
			.setUser(caseData.getTargetId())
			.setMod(caseData.getModId()>0 ? caseData.getModId() : null)
			.setReason(caseData.getReason())
			.setId(caseData.getTargetId());
		if (!caseData.getDuration().isNegative())
			builder.addField("duration", TimeUtil.formatDuration(lu, locale, caseData.getTimeStart(), caseData.getDuration()));
		return builder;
	}

	public MessageEmbed getCaseEmbed(DiscordLocale locale, CaseData caseData) {
		LogEmbedBuilder embedBuilder = moderationEmbedBuilder(locale, caseData);
		if (caseData.getLogUrl() != null)
			embedBuilder.addEmptyField(lu.getLocalized(locale, "logger.moderation.log_url").formatted(caseData.getLogUrl()));
		return embedBuilder.build();
	}

	//  Ban
	@NotNull
	public MessageEmbed banEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String proofFileName) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addProof(proofFileName)
			.build();
	}

	@NotNull
	public MessageEmbed helperBanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("moderation.ban.sync", target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("moderation.success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed userBanEmbed(DiscordLocale locale, User target, String reason, long modId) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon(LogEvent.BAN, target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.setMod(modId)
			.setId(target.getId())
			.build();
	} 

	//  Unban
	@NotNull
	public MessageEmbed unbanEmbed(DiscordLocale locale, CaseData caseData, String banReason) {
		return moderationEmbedBuilder(locale, caseData)
			.setColor(AMBER_DARK)
			.addField("moderation.unban.ban_reason", banReason)
			.build();
	}

	@NotNull
	public MessageEmbed helperUnbanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeaderIcon("moderation.unban.sync", target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("moderation.success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed autoUnbanEmbed(DiscordLocale locale, CaseData caseData) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeader("moderation.unban.title_expired", caseData.getTargetTag())
			.setUser(caseData.getTargetId())
			.addField("moderation.unban.ban_reason", caseData.getReason())
			.addField("duration", TimeUtil.durationToLocalizedString(lu, locale, caseData.getDuration()))
			.setId(caseData.getTargetId())
			.build();
	}

	@NotNull
	public MessageEmbed userUnbanEmbed(DiscordLocale locale, User target, String reason, long modId) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeaderIcon(LogEvent.UNBAN, target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.setMod(modId)
			.setId(target.getId())
			.build();
	} 

	//  Kick
	@NotNull
	public MessageEmbed kickEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String proofFileName) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addProof(proofFileName)
			.build();
	}

	@NotNull
	public MessageEmbed helperKickEmbed(DiscordLocale locale, Integer groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("moderation.kick.sync", target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("moderation.success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed userKickEmbed(DiscordLocale locale, User target, String reason, long modId) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon(LogEvent.KICK, target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.setMod(modId)
			.setId(target.getId())
			.build();
	}

	//  Mute
	@NotNull
	public MessageEmbed muteEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String proofFileName) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addProof(proofFileName)
			.build();
	}

	@NotNull
	public MessageEmbed unmuteEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String muteReason) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_DARK)
			.addField("moderation.unmute.mute_reason", muteReason)
			.build();
	}

	@NotNull
	public MessageEmbed userTimeoutUpdateEmbed(DiscordLocale locale, User target, String reason, long modId, OffsetDateTime until) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon(LogEvent.TIMEOUT, target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.setMod(modId)
			.addField("duration", lu.getLocalized(locale, "misc.temporary").formatted(formatTime(until, false)))
			.setId(target.getId())
			.build();
	}
	@NotNull
	public MessageEmbed userTimeoutRemoveEmbed(DiscordLocale locale, User target, String reason, long modId) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeaderIcon(LogEvent.REMOVE_TIMEOUT, target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.setMod(modId)
			.setId(target.getId())
			.build();
	}

	//  Strike
	public MessageEmbed strikeEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String proofFileName) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_LIGHT)
			.addProof(proofFileName)
			.build();
	}

	public MessageEmbed strikesClearedEmbed(DiscordLocale locale, String userTag, long userId, long modId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("moderation.strike.cleared", userTag)
			.setUser(userId)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	public MessageEmbed strikeDeletedEmbed(DiscordLocale locale, String userTag, long userId, long modId, int caseLocalId, int deletedAmount, int maxAmount) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("moderation.strike.deleted", userTag)
			.addField("moderation.strike.case", String.valueOf(caseLocalId))
			.addField("moderation.strike.amount", deletedAmount+"/"+maxAmount)
			.setUser(userId)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	//  Reason
	@NotNull
	public MessageEmbed reasonChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newReason) {
		return new LogEmbedBuilder(locale)
			.setHeader("moderation.change.reason", caseData.getLocalId(), caseData.getTargetTag())
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getType().getPath()), Optional.ofNullable(caseData.getReason()).orElse("None"), newReason))
			.setUser(caseData.getTargetId())
			.setMod(modId)
			.setId(caseData.getTargetId())
			.build();
	}

	//  Duration
	@NotNull
	public MessageEmbed durationChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newTime) {
		String oldTime = TimeUtil.formatDuration(lu, locale, caseData.getTimeStart(), caseData.getDuration());
		return new LogEmbedBuilder(locale)
			.setHeader("moderation.change.duration", caseData.getLocalId(), caseData.getTargetTag())
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getType().getPath()), oldTime, newTime))
			.setUser(caseData.getTargetId())
			.setMod(modId)
			.setId(caseData.getTargetId())
			.build();
	}

	//  Blacklist
	@NotNull
	public MessageEmbed blacklistAddedEmbed(DiscordLocale locale, User enforcer, User target, String groupInfo) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("moderation.blacklist.added", target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.addField("moderation.blacklist.group", groupInfo)
			.setEnforcer(enforcer.getIdLong())
			.setId(target.getId())
			.build();
	}

	@NotNull
	public MessageEmbed blacklistRemovedEmbed(DiscordLocale locale, User enforcer, User target, String groupInfo) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon("moderation.blacklist.removed", target.getEffectiveAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.addField("moderation.blacklist.group", groupInfo)
			.setEnforcer(enforcer.getIdLong())
			.setId(target.getId())
			.build();
	}

	//  Game
	@NotNull
	public MessageEmbed gameStrikeEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String proofFileName, String text) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_LIGHT)
			.addProof(proofFileName)
			.addField("moderation.game.strikes", text)
			.build();
	}

	//  Purge
	@NotNull
	public MessageEmbed messagePurge(DiscordLocale locale, User enforcer, User target, int messageCount, GuildChannel channel) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader("moderation.purge")
			.setUser(target==null?null:target.getIdLong())
			.addField("message.count", String.valueOf(messageCount))
			.addField("message.channel", channel.getAsMention())
			.setEnforcer(enforcer.getIdLong())
			.build();
	}


	// Roles
	@NotNull
	public MessageEmbed rolesApprovedEmbed(DiscordLocale locale, int ticketId, long memberId, String mentions, long modId) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeader("ticket.roles_title", "role-"+ticketId)
			.setUser(memberId)
			.addField("ticket.roles", mentions)
			.setEnforcer(modId)
			.setId(memberId)
			.build();
	}

	@NotNull
	public MessageEmbed roleAddedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, String roleMention) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("role.added", userUrl)
			.setUser(userId)
			.addField("role.role", roleMention)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed rolesAddedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, String rolesAdded) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("role.added_m", userUrl)
			.setUser(userId)
			.addField("role.role", rolesAdded)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, String roleMention) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("role.removed", userUrl)
			.setUser(userId)
			.addField("role.role", roleMention)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed rolesRemovedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, String rolesRemoved) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("role.removed_m", userUrl)
			.setUser(userId)
			.addField("role.role", rolesRemoved)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedAllEmbed(DiscordLocale locale, long modId, long roleId) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeader("role.removed_all")
			.addField("role.role", "<@&"+roleId+">")
			.setEnforcer(modId)
			.build();
	}

	@NotNull
	public MessageEmbed rolesModifiedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, String rolesModified) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("role.modified", userUrl)
			.setUser(userId)
			.addField("role.role", rolesModified)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAddedEmbed(DiscordLocale locale, User mod, User user, Role role, Duration duration) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("role.temp_added", user.getEffectiveAvatarUrl())
			.setUser(user.getIdLong())
			.addField("role.role", role.getAsMention())
			.addField("duration", TimeUtil.durationToLocalizedString(lu, locale, duration))
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAddedEmbed(DiscordLocale locale, User mod, User user, long roleId, Duration duration, boolean deleteAfter) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("role.temp_added", user.getEffectiveAvatarUrl())
			.setUser(user.getIdLong())
			.addField("role.role", "<@&"+roleId+">")
			.addField("duration", TimeUtil.durationToLocalizedString(lu, locale, duration))
			.addField("role.temp_delete", deleteAfter?Constants.SUCCESS:Constants.FAILURE)
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleRemovedEmbed(DiscordLocale locale, User mod, User user, Role role) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("role.temp_removed", user.getEffectiveAvatarUrl())
			.setUser(user.getIdLong())
			.addField("role.role", role.getAsMention())
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleUpdatedEmbed(DiscordLocale locale, User mod, User user, Role role, Instant until) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon("role.temp_updated", user.getEffectiveAvatarUrl())
			.setUser(user.getIdLong())
			.addField("role.role", role.getAsMention())
			.addField("duration", formatTime(until, false))
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAutoRemovedEmbed(DiscordLocale locale, long targetId, Role role) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader("role.temp_removed")
			.setUser(targetId)
			.addField("role.role", role.getAsMention())
			.setId(targetId)
			.build();
	}


	// Group
	@NotNull
	private LogEmbedBuilder groupLogBuilder(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String name) {
		return new LogEmbedBuilder(locale)
			.setHeaderIcon("group.title", ownerIcon, name, groupId)
			.setFooter(localized(locale, "group.master")+ownerId);
	}

	@NotNull
	public MessageEmbed groupCreatedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.created")
			.setEnforcer(adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberDeletedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.deleted")
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerDeletedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.deleted")
			.setEnforcer(adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberJoinedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.join")
			.setEnforcer(adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerJoinedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.joined")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberLeftEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.leave")
			.setEnforcer(adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerLeftEmbed(DiscordLocale locale, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.left")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRemovedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.removed")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.setEnforcer(adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberRenamedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String oldName, String newName) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle("group.renamed")
			.addField("group.oldname", oldName)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRenamedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String oldName, String newName) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle("group.renamed")
			.addField("group.oldname", oldName)
			.setEnforcer(adminMention)
			.build();
	}


	// Ticket
	@NotNull
	public MessageEmbed ticketCreatedEmbed(DiscordLocale locale, GuildChannel channel, User author) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader("ticket.created")
			.setUser(author.getIdLong())
			.addField("ticket.ticket_name", channel.getName())
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@NotNull
	public MessageEmbed ticketClosedEmbed(DiscordLocale locale, GuildChannel channel, User userClosed, Long authorId, Long claimerId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader("ticket.closed_title")
			.setDescription(localized(locale, "ticket.closed_value")
				.replace("{name}", channel.getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getAsMention).orElse("Auto"))
				.replace("{created}", User.fromId(authorId).getAsMention())
				.replace("{claimed}", Optional.ofNullable(claimerId).map("<@%s>"::formatted).orElse(localized(locale, "ticket.unclaimed")))
			)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@NotNull
	public MessageEmbed ticketClosedPmEmbed(DiscordLocale locale, GuildChannel channel, Instant timeClosed, User userClosed, String reasonClosed) {
		return new LogEmbedBuilder(locale, WHITE)
			.setDescription(localized(locale, "ticket.closed_pm")
				.replace("{guild}", channel.getGuild().getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getEffectiveName).orElse("Auto"))
				.replace("{time}", formatTime(timeClosed, false))
				.replace("{reason}", reasonClosed)
			)
			.setFooter(channel.getName())
			.build();
	}


	// Bot settings
	@NotNull
	public MessageEmbed accessAdded(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon("bot.access_added", userTarget != null ? userTarget.getEffectiveAvatarUrl() : null)
			.addField("target", targetMention)
			.addField("bot.access_level", levelName)
			.setEnforcer(mod.getIdLong())
			.setId(targetId)
			.build();
	}

	@NotNull
	public MessageEmbed accessRemoved(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("bot.access_removed", userTarget != null ? userTarget.getEffectiveAvatarUrl() : null)
			.addField("target", targetMention)
			.addField("bot.access_level", levelName)
			.setEnforcer(mod.getIdLong())
			.setId(targetId)
			.build();
	}

	@NotNull
	public MessageEmbed moduleEnabled(DiscordLocale locale, User mod, CmdModule module) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeader("bot.module_enabled")
			.addField("bot.module", lu.getLocalized(locale, module.getPath()))
			.setEnforcer(mod.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed moduleDisabled(DiscordLocale locale, User mod, CmdModule module) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeader("bot.module_disabled")
			.addField("bot.module", lu.getLocalized(locale, module.getPath()))
			.setEnforcer(mod.getIdLong())
			.build();
	}


	//  Channel
	@NotNull
	public MessageEmbed channelCreated(DiscordLocale locale, long channelId, String channelName, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader(LogEvent.CHANNEL_CREATE, channelName)
			.setDescription("<#"+channelId+">\n\n")
			.appendDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nUser ID: %s".formatted(channelId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed channelUpdate(DiscordLocale locale, long channelId, String channelName, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.CHANNEL_UPDATE, channelName)
			.setDescription("<#"+channelId+">\n\n")
			.appendDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nUser ID: %s".formatted(channelId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed channelDeleted(DiscordLocale locale, long channelId, String channelName, Collection<AuditLogChange> changes, long userId, String reason) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.CHANNEL_DELETE, channelName)
			.setDescription(changesText(locale, changes))
			.setReasonNull(reason)
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nUser ID: %s".formatted(channelId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed overrideCreate(DiscordLocale locale, long channelId, AuditLogEntry entry, long userId) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader(LogEvent.CHANNEL_OVERRIDE_CREATE)
			.setDescription("<#"+channelId+">\n\n")
			.appendDescription(permissionOverrides(locale, entry))
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nMod ID: %s".formatted(channelId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed overrideUpdate(DiscordLocale locale, long channelId, AuditLogEntry entry, long userId, String guildId) {
		String id = entry.getOption(AuditLogOption.ID).toString();
		String text;
		if (id.equals(guildId))
			text = "@everyone";
		else
			text = "%s%s>".formatted(entry.getOption(AuditLogOption.TYPE).toString().equals("0") ? "Role <@&" : "Member <@", id); 
		
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.CHANNEL_OVERRIDE_UPDATE)
			.setDescription("<#%s>\n\n> %s\n\n".formatted(channelId, text))
			.appendDescription(permissionOverrides(locale, entry))
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nMod ID: %s".formatted(channelId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed overrideDelete(DiscordLocale locale, long channelId, AuditLogEntry entry, long userId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.CHANNEL_OVERRIDE_DELETE)
			.setDescription("<#"+channelId+">\n\n")
			.appendDescription(permissionOverrides(locale, entry))
			.setEnforcer(userId)
			.setFooter("Channel ID: %s\nMod ID: %s".formatted(channelId, userId))
			.build();
	}

	//  Role
	@NotNull
	public MessageEmbed roleCreated(DiscordLocale locale, long roleId, String roleName, Collection<AuditLogChange> changes, long userId, String reason) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader(LogEvent.ROLE_CREATE, roleName)
			.setDescription("<@&"+roleId+">\n")
			.appendDescription(changesText(locale, changes))
			.setReasonNull(reason)
			.setEnforcer(userId)
			.setFooter("Role ID: %s\nUser ID: %s".formatted(roleId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed roleDeleted(DiscordLocale locale, long roleId, String roleName, Collection<AuditLogChange> changes, long userId, String reason) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.ROLE_DELETE, roleName)
			.setDescription(changesText(locale, changes))
			.setReasonNull(reason)
			.setEnforcer(userId)
			.setFooter("Role ID: %s\nUser ID: %s".formatted(roleId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed roleUpdate(DiscordLocale locale, long roleId, String roleName, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.ROLE_UPDATE, roleName)
			.setDescription("<@&"+roleId+">\n")
			.appendDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Role ID: %s\nUser ID: %s".formatted(roleId, userId))
			.build();
	}

	//  Server
	@NotNull
	public MessageEmbed guildUpdate(DiscordLocale locale, long guildId, String guildName, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.GUILD_UPDATE, guildName)
			.setDescription(changesText(locale, changes).replace("{guild}", String.valueOf(guildId)))
			.setEnforcer(userId)
			.setFooter("Server ID: %s\nUser ID: %s".formatted(guildId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed emojiCreate(DiscordLocale locale, long emojiId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader(LogEvent.EMOJI_CREATE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Emoji ID: %s\nUser ID: %s".formatted(emojiId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed emojiUpdate(DiscordLocale locale, long emojiId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.EMOJI_UPDATE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Emoji ID: %s\nUser ID: %s".formatted(emojiId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed emojiDelete(DiscordLocale locale, long emojiId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.EMOJI_DELETE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Emoji ID: %s\nUser ID: %s".formatted(emojiId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed stickerCreate(DiscordLocale locale, long stickerId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader(LogEvent.STICKER_CREATE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Sticker ID: %s\nUser ID: %s".formatted(stickerId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed stickerUpdate(DiscordLocale locale, long stickerId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.STICKER_UPDATE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Sticker ID: %s\nUser ID: %s".formatted(stickerId, userId))
			.build();
	}

	@NotNull
	public MessageEmbed stickerDelete(DiscordLocale locale, long stickerId, Collection<AuditLogChange> changes, long userId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.STICKER_DELETE)
			.setDescription(changesText(locale, changes))
			.setEnforcer(userId)
			.setFooter("Sticker ID: %s\nUser ID: %s".formatted(stickerId, userId))
			.build();
	}

	//  Member
	@NotNull
	public MessageEmbed memberNickUpdate(DiscordLocale locale, User user, String oldNick, String newNick) {
		return new LogEmbedBuilder(locale, DEFAULT)
			.setHeaderIcon(LogEvent.MEMBER_NICK_CHANGE, user.getEffectiveAvatarUrl(), user.getName())
			.setDescription("**Nickname**: "+(oldNick==null?"*none*":"||`"+oldNick+"`||")+" -> "+(newNick==null?"*none*":"`"+newNick+"`"))
			.setId(user.getId())
			.build();
	}

	@NotNull
	public MessageEmbed rolesChange(DiscordLocale locale, long userId, Collection<AuditLogChange> changes, long modId) {
		return new LogEmbedBuilder(locale, DEFAULT)
			.setHeader(LogEvent.MEMBER_ROLE_CHANGE)
			.setDescription("<@"+userId+">\n")
			.appendDescription(changesText(locale, changes))
			.setEnforcer(modId)
			.setFooter("User ID: %s\nEnforcer ID: %s".formatted(userId, modId))
			.build();
	}

	@NotNull
	public MessageEmbed memberJoin(DiscordLocale locale, Member member) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon(LogEvent.MEMBER_JOIN, member.getEffectiveAvatarUrl(), member.getUser().getName())
			.setDescription("<@%s>".formatted(member.getId()))
			.setId(member.getId())
			.build();
	}

	@NotNull
	public MessageEmbed memberLeave(DiscordLocale locale, Member cachedMember, User user, List<Role> roles) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon(LogEvent.MEMBER_LEAVE, user.getEffectiveAvatarUrl(), user.getName())
			.setDescription("<@%s>".formatted(user.getId()))
			.setId(user.getId());
		if (!roles.isEmpty()) {
			String text = roles.stream().map(Role::getName).collect(Collectors.joining(", "));
			builder.addField("member.roles", text);
		}
		if (cachedMember != null) {
			builder.addField("member.joined_at", formatTime(cachedMember.getTimeJoined(), false));
		}
		return builder.build();
	}

	//  Message
	@Nullable
	public MessageEmbed messageUpdate(DiscordLocale locale, Member member, long channelId, long messageId, @NotNull MessageData oldData, @NotNull MessageData newData, @Nullable MessageData.DiffData diff) {
		// If it had no attachment
		if ((oldData.getAttachment() == null || newData.getAttachment() != null) && diff == null) return null;

		LogEmbedBuilder builder = new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader(LogEvent.MESSAGE_UPDATE)
			.setDescription("[View Message](https://discord.com/channels/%s/%s/%s)\n".formatted(member.getGuild().getId(), channelId, messageId))
			.addField("message.author", "<@%s>".formatted(newData.getAuthorId()))
			.addField("message.channel", "<#%s>".formatted(channelId))
			.setFooter("Message ID: %s\nUser ID: %s".formatted(messageId, newData.getAuthorId()));

		if (oldData.getAttachment() != null && newData.getAttachment() == null) {
			builder.appendDescription("Removed Attachment: "+oldData.getAttachment().getFileName()+"\n\n");
		}
		if (diff != null) {
			builder.appendDescription("**"+localized(locale, "message.content")+"**: ```diff\n")
				.appendDescription(MessageUtil.limitString(diff.content(), 1400))
				.appendDescription("\n```");
		}

		return builder.build();
	}

	@NotNull
	public MessageEmbed messageDelete(DiscordLocale locale, long channelId, long messageId, MessageData data, Long modId) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader(LogEvent.MESSAGE_DELETE);
		if (data == null) {
			builder.setFooter("Message ID: %s".formatted(messageId));
		} else {
			if (data.getAttachment() != null) {
				builder.appendDescription("[Attachment: %s]\n".formatted(data.getAttachment().getFileName()))
					.setImage(data.getAttachment().getUrl());
			}
			if (!data.getContent().isBlank()) {
				builder.appendDescription("**"+localized(locale, "message.content")+"**: \n")
					.appendDescription(MessageUtil.limitString(data.getContentEscaped(), 1000));
			}
			builder.addField("message.author", "<@%s>".formatted(data.getAuthorId()))
				.setFooter("Message ID: %s\nUser ID: %s".formatted(messageId, data.getAuthorId()));
		}
		builder.addField("message.channel", "<#%s>".formatted(channelId));
		if (modId != null) {
			builder.setMod(modId);
		}
		return builder.build();
	}

	@NotNull
	public MessageEmbed messageBulkDelete(DiscordLocale locale, long channelId, String count, Long modId) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, RED_DARK)
			.setHeader(LogEvent.MESSAGE_BULK_DELETE)
			.addField("message.channel", "<#%s>".formatted(channelId))
			.addField("message.count", count)
			.setFooter("Channel ID: %s".formatted(channelId));
		if (modId != null) {
			builder.setMod(modId);
		}
		return builder.build();
	}

	//  Voice
	@NotNull
	public MessageEmbed voiceMute(DiscordLocale locale, long userId, String userName, String userIcon, boolean isMuted, Long modId) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon(LogEvent.VC_CHANGE, userIcon, userName)
			.setDescription("**"+localized(locale, "voice.mute")+"**: "+(isMuted?Constants.SUCCESS:Constants.FAILURE))
			.setFooter("User ID: %s".formatted(userId));
		if (modId != null) {
			builder.setMod(modId);
		}
		return builder.build();
	}

	@NotNull
	public MessageEmbed voiceDeafen(DiscordLocale locale, long userId, String userName, String userIcon, boolean isDeafen, Long modId) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon(LogEvent.VC_CHANGE, userIcon, userName)
			.setDescription("**"+localized(locale, "voice.deaf")+"**: "+(isDeafen?Constants.SUCCESS:Constants.FAILURE))
			.setFooter("User ID: %s".formatted(userId));
		if (modId != null) {
			builder.setMod(modId);
		}
		return builder.build();
	}

	//  Level
	@NotNull
	public MessageEmbed levelUp(DiscordLocale locale, Member member, int level, ExpType expType) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon(LogEvent.LEVEL_UP, member.getEffectiveAvatarUrl(), member.getUser().getName())
			.setDescription(lu.getLocalizedRandom(locale, "logger.level.msg_random")
				.replace("{user}", "<@!%s>".formatted(member.getIdLong()))
				.replace("{level}", String.valueOf(level))
				.replace("{type}", expType.equals(ExpType.TEXT) ? "\uD83D\uDCAC" : "\uD83C\uDF99️")
			)
			.build();
	}


	// TOOLS
	private String changesText(DiscordLocale locale, Collection<AuditLogChange> changes) {
		StringBuilder builder = new StringBuilder();
		for (AuditLogChange change : changes) {
			String key = change.getKey();
			switch (key) {
				case "$add" -> {
					String text = lu.getLocalizedNullable(locale, "logger.keys.add_roles");
					builder.append("**").append(text).append("**: ").append(formatValue(key, change.getNewValue())).append("\n");
					continue;
				}
				case "$remove" -> {
					String text = lu.getLocalizedNullable(locale, "logger.keys.remove_roles");
					builder.append("**").append(text).append("**: ").append(formatValue(key, change.getNewValue())).append("\n");
					continue;
				}
				case "permissions" -> {
					builder.append(parseRolePermissions(locale, change));
					continue;
				}
				case "permission_overwrites" -> {
					builder.append(parseChannelOverrides(change));
					continue;
				}
				default -> {}
			}
			String text = lu.getLocalizedNullable(locale, "logger.keys."+key);
			if (text == null) continue;
			Object oldValue = change.getOldValue();
			Object newValue = change.getNewValue();
			if (oldValue == null) {
				// Created
				builder.append("➕ **").append(text).append("**: ").append(formatValue(key, newValue));
			} else if (newValue == null || newValue.toString().isBlank()) {
				// Deleted
				builder.append("➖ **").append(text).append("**: ").append(formatValue(key, oldValue));
			} else {
				// Changed
				builder.append("**").append(text).append("**: ||").append(formatValue(key, oldValue)).append("|| -> ").append(formatValue(key, newValue));
			}
			builder.append("\n");
		}
		if (builder.isEmpty()) return "";
		return builder.toString();
	}

	private final String guildIconLink = "[Image](https://cdn.discordapp.com/icons/{guild}/%s.png)";
	private final String guildSplashLink = "[Image](https://cdn.discordapp.com/splashes/{guild}/%s.png)";

	private String formatValue(String key, @NotNull Object object) {
		switch (object) {
			case Boolean value -> {
				return value ? Constants.SUCCESS : Constants.FAILURE;
			}
			case String value -> {
				if (value.isEmpty()) return Constants.NONE;
				return switch (key) {
					case "afk_channel_id", "system_channel_id", "rules_channel_id", "public_updates_channel_id" ->
						"<#" + value + ">";
					case "owner_id" -> "<@" + value + ">";
					case "icon_hash" -> guildIconLink.formatted(value);
					case "splash_hash" -> guildSplashLink.formatted(value);
					case "communication_disabled_until" -> formatTime(Instant.parse(value), false);
					default -> "`" + MessageUtil.limitString(value, 1024) + "`";
				};
			}
			case Integer value -> {
				return switch (key) {
					case "type" -> formatType(ChannelType.fromId(value));
					case "color" -> "`#" + Integer.toHexString(value) + "`";
					case "explicit_content_filter" -> formatType(ExplicitContentLevel.fromKey(value));
					case "mfa_level" -> formatType(MFALevel.fromKey(value));
					case "default_message_notifications" -> formatType(NotificationLevel.fromKey(value));
					default -> String.valueOf(value);
				};
			}
			case List<?> values -> {
				if (values.isEmpty()) return "";
				if (values.getFirst() instanceof HashMap) {
					return values.stream()
						.map(v -> (String) JsonPath.read(v, "$.id"))
						.collect(Collectors.joining(">, <@&", "<@&", ">"));
				} else {
					return values.stream()
						.map(String::valueOf)
						.collect(Collectors.joining(", "));
				}
			}
			default -> {
				return String.format("`%s`", object);
			}
		}
	}

	private <T extends Enum<T>> String formatType(Enum<T> value) {
		return MessageUtil.formatKey(value.name());
	}

	private String parseChannelOverrides(AuditLogChange change) {
		List<?> values = change.getNewValue();
		if (values == null || values.isEmpty()) return "";

		StringBuilder builder = new StringBuilder();
		values.forEach(v -> {
			long permsLong = castLong(JsonPath.read(v, "$.allow"));
			if (permsLong == 0) return;
			EnumSet<Permission> perms = Permission.getPermissions(permsLong);

			String id = JsonPath.read(v, "$.id");
			int type = JsonPath.read(v, "$.type");
			//buffer.append("> <@%s%s> (%<s)\n".formatted(type==0?"&":"", id))
			builder.append("> %s%s>\n".formatted(type == 0 ? "Role <@&" : "Member <@", id))
				.append("Permissions: `")
				.append(perms.stream().map(Permission::getName).collect(Collectors.joining(", ")))
				.append("`\n");
		});
		return builder.toString();
	}

	private String parseRolePermissions(DiscordLocale locale, AuditLogChange change) {
		Pair<EnumSet<Permission>, EnumSet<Permission>> changes = getChangedPerms(change);
		if (changes == null) return "";

		StringBuffer buffer = new StringBuffer();
		if (!changes.getRight().isEmpty()) {
			buffer.append("**").append(lu.getLocalized(locale, "logger.keys.add_permissions")).append("**: ```\n");
			changes.getRight().forEach(perm -> buffer.append(perm.getName()).append("\n"));
			buffer.append("```\n");
		}
		if (!changes.getLeft().isEmpty()) {
			buffer.append("**").append(lu.getLocalized(locale, "logger.keys.remove_permissions")).append("**: ```\n");
			changes.getLeft().forEach(perm -> buffer.append(perm.getName()).append("\n"));
			buffer.append("```\n");
		}
		return buffer.append("\n").toString();
	}

	private String permissionOverrides(DiscordLocale locale, AuditLogEntry entry) {
		switch (entry.getType()) {
			case CHANNEL_OVERRIDE_CREATE -> {
				StringBuilder builder = new StringBuilder();
				String id = entry.getChangeByKey("id").getNewValue();
				int type = entry.getChangeByKey("type").getNewValue();
				builder.append("> %s%s>\n".formatted(type==0?"Role <@&":"Member <@", id));

				long permsLong = castLong(entry.getChangeByKey("allow").getNewValue());
				if (permsLong != 0) {
					EnumSet<Permission> perms = Permission.getPermissions(permsLong);
					builder.append(lu.getLocalized(locale, "logger.keys.allow")).append(": `");
					builder.append(perms.stream().map(Permission::getName).collect(Collectors.joining(", ")));
					builder.append("`\n");
				}
				permsLong = castLong(entry.getChangeByKey("deny").getNewValue());
				if (permsLong != 0) {
					EnumSet<Permission> perms = Permission.getPermissions(permsLong);
					builder.append(lu.getLocalized(locale, "logger.keys.deny")).append(": `");
					builder.append(perms.stream().map(Permission::getName).collect(Collectors.joining(", ")));
					builder.append("`\n");
				}

				return builder.toString();
			}
			case CHANNEL_OVERRIDE_DELETE -> {
				StringBuilder builder = new StringBuilder();
				String id = entry.getChangeByKey("id").getOldValue();
				int type = entry.getChangeByKey("type").getOldValue();
				builder.append("> %s%s>\n".formatted(type==0?"Role <@&":"Member <@", id));

				long permsLong = castLong(entry.getChangeByKey("allow").getOldValue());
				if (permsLong != 0) {
					EnumSet<Permission> perms = Permission.getPermissions(permsLong);
					builder.append(lu.getLocalized(locale, "logger.keys.allow")).append(": `");
					builder.append(perms.stream().map(Permission::getName).collect(Collectors.joining(", ")));
					builder.append("`\n");
				}
				permsLong = castLong(entry.getChangeByKey("deny").getOldValue());
				if (permsLong != 0) {
					EnumSet<Permission> perms = Permission.getPermissions(permsLong);
					builder.append(lu.getLocalized(locale, "logger.keys.deny")).append(": `");
					builder.append(perms.stream().map(Permission::getName).collect(Collectors.joining(", ")));
					builder.append("`\n");
				}

				return builder.toString();
			}
			case CHANNEL_OVERRIDE_UPDATE -> {
				StringBuffer buffer = new StringBuffer();
				Pair<EnumSet<Permission>, EnumSet<Permission>> changes = getChangedPerms(entry.getChangeByKey("allow"));
				if (changes != null) {
					buffer.append("**").append(lu.getLocalized(locale, "logger.keys.allow")).append("**: ```\n");
					changes.getLeft().forEach(perm -> buffer.append("➖ ").append(perm.getName()).append("\n"));
					changes.getRight().forEach(perm -> buffer.append("➕ ").append(perm.getName()).append("\n"));
					buffer.append("```\n");
				}
				
				changes = getChangedPerms(entry.getChangeByKey("deny"));
				if (changes != null) {
					buffer.append("**").append(lu.getLocalized(locale, "logger.keys.deny")).append("**: ```\n");
					changes.getLeft().forEach(perm -> buffer.append("➖ ").append(perm.getName()).append("\n"));
					changes.getRight().forEach(perm -> buffer.append("➕ ").append(perm.getName()).append("\n"));
					buffer.append("```\n");
				}

				return buffer.toString();
			}
			default -> {
				return "";
			}
		}
	}

	// removed - added
	private Pair<EnumSet<Permission>, EnumSet<Permission>> getChangedPerms(AuditLogChange change) {
		if (change == null) return null;
		if (change.getOldValue() == null | change.getNewValue() == null) return null;
		EnumSet<Permission> oldPerms = Permission.getPermissions(castLong(change.getOldValue()));
		EnumSet<Permission> newPerms = Permission.getPermissions(castLong(change.getNewValue()));

		EnumSet<Permission> addedPerms = EnumSet.copyOf(newPerms); 
		addedPerms.removeAll(oldPerms);
		EnumSet<Permission> removedPerms = EnumSet.copyOf(oldPerms);
		removedPerms.removeAll(newPerms);
		if (addedPerms.isEmpty() && removedPerms.isEmpty()) return null;
		return Pair.of(removedPerms, addedPerms);

	}

}
