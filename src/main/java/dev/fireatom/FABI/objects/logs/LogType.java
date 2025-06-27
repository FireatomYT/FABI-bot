package dev.fireatom.FABI.objects.logs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.fireatom.FABI.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public enum LogType {
	MODERATION("moderation"),
	GROUP("group"),
	TICKET("ticket"),
	ROLE("role"),
	GUILD("guild"),
	MESSAGE("message"),
	MEMBER("member"),
	VOICE("voice"),
	CHANNEL("channel"),
	LEVEL("level"),
	BOT("bot");

	private final String name;
	private final String path;

	private static final Map<String, LogType> BY_NAME = new HashMap<>();

	static {
		for (LogType lc : LogType.values()) {
			BY_NAME.put(lc.getName(), lc);
		}
	}

	LogType(String name) {
		this.name = name;
		this.path = "logger."+name;
	}

	public String getName() {
		return this.name;
	}

	public String getPath() {
		return this.path;
	}

	public String getNamePath() {
		return this.path+".name";
	}

	public static List<Choice> asChoices(LocaleUtil lu) {
		return Stream.of(values()).map(type -> new Choice(lu.getText(type.getNamePath()), type.getName())).toList();
	}

	public static Set<String> getAllNames() {
		return Stream.of(values()).map(LogType::getName).collect(Collectors.toSet());
	}

	public static LogType of(String name) {
		LogType result = BY_NAME.get(name);
		if (result == null) {
			throw new IllegalArgumentException("Invalid DB name: " + name);
		}
		return result;
	}
}
