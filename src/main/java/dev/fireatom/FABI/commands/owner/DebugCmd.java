package dev.fireatom.FABI.commands.owner;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class DebugCmd extends CommandBase {

	public DebugCmd() {
		this.name = "debug";
		this.path = "bot.owner.debug";
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "debug_logs", lu.getText(path+".debug_logs.help")),
			new OptionData(OptionType.STRING, "date", lu.getText(path+".date.help")).setRequiredLength(10,10)
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		if (event.optBoolean("debug_logs", false)) {
			event.replyFiles(FileUpload.fromData(new File("./logs/App-debug.log"))).queue();
		} else {
			String date = Optional.ofNullable(event.optString("date"))
				.map(s->"."+s)
				.orElse("");
			File file = new File("./logs/App%s.log".formatted(date));
			if (!file.exists()) {
				editErrorOther(event, "No file by ");
			} else {
				event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
			}
		}
	}

}