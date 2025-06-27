package dev.fireatom.FABI.commands.moderation;

import java.sql.SQLException;
import java.util.List;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ReasonCmd extends CommandBase {
	
	public ReasonCmd() {
		this.name = "reason";
		this.path = "bot.moderation.reason";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true).setMaxLength(400)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		CaseData caseData = bot.getDBUtil().cases.getInfo(event.getGuild().getIdLong(), event.optInteger("id"));
		if (caseData == null) {
			editError(event, path+".not_found");
			return;
		}
		if (!caseData.isActive()) {
			editError(event, path+".not_active");
			return;
		}

		String newReason = event.optString("reason");
		try {
			bot.getDBUtil().cases.updateReason(caseData.getRowId(), newReason);
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "update reason");
			return;
		}

		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(caseData.getLocalId(), newReason))
			.build()
		);

		switch (caseData.getType()) {
			case MUTE -> {
				// Check if inform with reason is disabled
				if (bot.getDBUtil().getGuildSettings(event.getGuild()).getInformMute().getLevel() < 2) break;
				// Retrieve user by guild and send dm
				event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(member -> {
					member.getUser().openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getModerationUtil().getReasonUpdateEmbed(event.getGuildLocale(), event.getGuild(), caseData.getTimeStart(), caseData.getType(), caseData.getReason(), newReason);
						pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
			case STRIKE_1, STRIKE_2, STRIKE_3 -> {
				// Check if inform with reason is disabled
				if (bot.getDBUtil().getGuildSettings(event.getGuild()).getInformStrike().getLevel() < 2) break;
				// Retrieve user by guild and send dm
				event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(member -> {
					member.getUser().openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getModerationUtil().getReasonUpdateEmbed(event.getGuildLocale(), event.getGuild(), caseData.getTimeStart(), caseData.getType(), caseData.getReason(), newReason);
						pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
			default -> {}
		}

		bot.getLogger().mod.onChangeReason(event.getGuild(), caseData, event.getMember(), newReason);
	}
}
