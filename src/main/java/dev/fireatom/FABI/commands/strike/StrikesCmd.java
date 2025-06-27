package dev.fireatom.FABI.commands.strike;

import java.util.List;

import dev.fireatom.FABI.base.command.CooldownScope;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;
import dev.fireatom.FABI.utils.message.MessageUtil;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikesCmd extends CommandBase {
	
	public StrikesCmd() {
		this.name = "strikes";
		this.path = "bot.moderation.strikes";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		
		User tu;
		if (event.hasOption("user")) {
			tu = event.optUser("user", event.getUser());
			if (!tu.equals(event.getUser()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tu = event.getUser();
		}

		Pair<Integer, String> strikeData = bot.getDBUtil().strikes.getData(event.getGuild().getIdLong(), tu.getIdLong());
		if (strikeData == null) {
			editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".no_active")).build());
			return;
		}
		String[] strikesInfoArray = strikeData.getRight().split(";");
		if (strikesInfoArray[0].isEmpty()) {
			editErrorOther(event, "Strikes data is empty.");
			return;
		}

		StringBuilder builder = new StringBuilder();
		for (String c : strikesInfoArray) {
			String[] args = c.split("-");
			final int caseRowId = Integer.parseInt(args[0]);
			int strikeAmount = Integer.parseInt(args[1]);
			CaseData caseData = bot.getDBUtil().cases.getInfo(caseRowId);
			builder.append("`%4d` %s | %s - %s\nBy: %s\n".formatted(
				caseData.getLocalIdInt(),
				getSquares(strikeAmount, caseData.getType().getValue()-20),
				MessageUtil.limitString(caseData.getReason(), 50),
				TimeFormat.DATE_SHORT.format(caseData.getTimeStart()),
				caseData.getModTag()
			));
		}

		editEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, path+".title").formatted(strikeData.getLeft(), tu.getName(), tu.getId()))
			.setDescription(builder.toString())
			.build()
		);
	}

	private String getSquares(int active, int max) {
		return "🟥".repeat(active) + "🔲".repeat(max-active);
	}

}
