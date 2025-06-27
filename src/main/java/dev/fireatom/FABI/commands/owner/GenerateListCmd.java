package dev.fireatom.FABI.commands.owner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.fireatom.FABI.base.command.Category;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.commands.CommandBase;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.FileUpload;

import org.json.JSONArray;
import org.json.JSONObject;

public class GenerateListCmd extends CommandBase {
	
	public GenerateListCmd() {
		this.name = "generate";
		this.path = "bot.owner.generate";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		List<SlashCommand> commands = event.getClient().getSlashCommands();
		if (commands.isEmpty()) {
			editError(event, "Commands not found");
			return;
		}

		JSONArray commandArray = new JSONArray();
		for (SlashCommand cmd : commands) {
			if (cmd.isOwnerCommand()) continue;
			
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("name", cmd.getName())
				.put("description", getText(cmd.getHelpPath()))
				.put("category", getCategoryMap(cmd.getCategory()))
				.put("guildOnly", cmd.isGuildOnly())
				.put("access", cmd.getAccessLevel().getLevel());

			if (cmd.getModule() == null) {
				jsonObject.put("module", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.put("module", getText(cmd.getModule().getPath()));
			}
			
			if (cmd.getChildren().length > 0) {
				List<Map<String, Object>> values = new ArrayList<>();
				for (SlashCommand child : cmd.getChildren()) {
					values.add(Map.of("description", getText(child.getHelpPath()), "usage", getText(child.getUsagePath())));
				}
				jsonObject.put("child", values);
				jsonObject.put("usage", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.put("child", Collections.emptyList());
				jsonObject.put("usage", getText(cmd.getUsagePath()));
			}
			
			commandArray.put(jsonObject);
		}

		File file = new File(Constants.DATA_PATH + "commands.json");
		try {
			boolean ignored = file.createNewFile();
			FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8);
			writer.write(commandArray.toString());
			writer.flush();
			writer.close();
			event.getHook().editOriginalAttachments(FileUpload.fromData(file, "commands.json")).queue(hook -> {
				boolean ignored2 = file.delete();
			});
		} catch (IOException | UncheckedIOException ex) {
			editError(event, path+".error", ex.getMessage());
		}
	}

	private Map<String, Object> getCategoryMap(Category category) {
		if (category == null) {
			return Map.of("name", "", "en-GB", "", "ru", "");
		}
		Map<String, Object> map = new HashMap<>();
		map.put("name", category.name());
		map.putAll(getText("bot.help.command_menu.categories."+category.name()));
		return map;
	}

	private Map<String, Object> getText(String path) {
		Map<String, Object> map = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			map.put(locale.getLocale(), lu.getLocalized(locale, path));
		}
		return map;
	}

}
