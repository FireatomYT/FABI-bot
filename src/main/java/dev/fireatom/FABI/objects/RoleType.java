package dev.fireatom.FABI.objects;

import java.util.HashMap;
import java.util.Map;

public enum RoleType {
	CUSTOM(0, "role_type.custom"),
	TOGGLE(1, "role_type.toggle"),
	ASSIGN(2, "role_type.assign");

	private final int type;
	private final String path;
	
	private static final Map<Integer, RoleType> BY_TYPE;

	static {
		BY_TYPE = new HashMap<>();
		for (RoleType rt : RoleType.values()) {
			BY_TYPE.put(rt.getType(), rt);
		}
	}

	RoleType(int type, String path) {
		this.type = type;
		this.path = path;
	}

	public int getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase();
	}
	
	public static RoleType byType(int type) {
		return BY_TYPE.get(type);
	}
}
