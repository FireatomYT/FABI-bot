package dev.fireatom.FABI.commands.moderation;

import java.util.List;

import dev.fireatom.FABI.base.command.CooldownScope;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;

public class ModLogsCmd extends CommandBase {

	public ModLogsCmd() {
		this.name = "modlogs";
		this.path = "bot.moderation.modlogs";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "page", lu.getText(path+".page.help")).setMinValue(1),
			new OptionData(OptionType.BOOLEAN, "only_active", lu.getText(path+".only_active.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		User tu;
		if (event.hasOption("user")) {
			tu = event.optUser("user");
			if (!tu.equals(event.getUser()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tu = event.getUser();
		}

		final long guildId = event.getGuild().getIdLong();
		final long userId = tu.getIdLong();
		final int page = event.optInteger("page", 1);
		final List<CaseData> cases = event.optBoolean("only_active", false) ?
			bot.getDBUtil().cases.getGuildUser(guildId, userId, page, true) :
			bot.getDBUtil().cases.getGuildUser(guildId, userId, page);
		if (cases.isEmpty()) {
			editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty")).build());
			return;
		}
		final int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		editEmbed(event, buildEmbed(lu, event.getUserLocale(), tu, cases, page, pages).build());
	}

	public static EmbedBuilder buildEmbed(LocaleUtil lu, DiscordLocale locale, User tu, List<CaseData> cases, int page, int pages) {
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setTitle(lu.getLocalized(locale, "bot.moderation.modlogs.title").formatted(tu.getName(), page, pages))
			.setFooter(lu.getLocalized(locale, "bot.moderation.modlogs.footer").formatted(tu.getId()));
		cases.forEach(c -> {
			final String temp = c.getLogUrl()==null ? "" : " - [Link](%s)".formatted(c.getLogUrl());
			StringBuilder stringBuilder = new StringBuilder()
				.append("> ").append(TimeFormat.DATE_TIME_SHORT.format(c.getTimeStart())).append(temp).append("\n")
				.append(lu.getLocalized(locale, "bot.moderation.modlogs.mod").formatted(c.getModTag()));
			if (!c.getDuration().isNegative())
				stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.duration").formatted(TimeUtil.formatDuration(lu, locale, c.getTimeStart(), c.getDuration())));
			stringBuilder.append(lu.getLocalized(locale, "bot.moderation.modlogs.reason").formatted(c.getReason()));

			builder.addField("%s  #`%s`| %s"
				.formatted(c.isActive() ? "🟥" : "⬛", c.getLocalId(), lu.getLocalized(locale, c.getType().getPath())),
				stringBuilder.toString(),
				false
			);
		});

		return builder;
	}

}
