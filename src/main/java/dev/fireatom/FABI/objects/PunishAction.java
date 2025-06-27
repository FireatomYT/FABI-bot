package dev.fireatom.FABI.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum PunishAction {
	MUTE(1, "punish_action.mute", "t(\\d+)"),		// mute duration
	KICK(2, "punish_action.kick"),
	BAN(4, "punish_action.ban", "t(\\d+)"),		// ban duration
	REMOVE_ROLE(8, "punish_action.remove_role", "rr(\\d+)"),	// role ID
	ADD_ROLE(16, "punish_action.add_role", "ar(\\d+)"),		// role ID
	TEMP_ROLE(32, "punish_action.temp_role", "tr(\\d+)-(\\d+)");		// role ID & duration

	private final int type;
	private final String path;
	private final String pattern;

	PunishAction(int type, String path, String pattern) {
		this.type = type;
		this.path = path;
		this.pattern = pattern;
	}

	PunishAction(int type, String path) {
		this.type = type;
		this.path = path;
		this.pattern = "";
	}

	public int getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	public Pattern getPattern() {
		return Pattern.compile(pattern);
	}

	public static List<PunishAction> decodeActions(int data) {
		List<PunishAction> actions = new ArrayList<>();
		for (PunishAction v : values()) {
			if ((data & v.type) == v.type) actions.add(v);
		}
		return actions;
	}

	public static int encodeActions(List<PunishAction> actions) {
		int data = 0;
		for (PunishAction v : actions) {
			data += v.type;
		}
		return data;
	}

	public String getMatchedValue(String data) {
		Matcher matcher = getPattern().matcher(data);
		if (!matcher.find()) return null;
		return matcher.group(1);
	}

	public String getMatchedValue(String data, int group) {
		Matcher matcher = getPattern().matcher(data);
		if (!matcher.find()) return null;
		return matcher.group(group);
	}

}
