package dev.fireatom.FABI.utils;

import java.util.function.Function;

public class CastUtil {
	public static Long castLong(Object o) {
		return o != null ? Long.valueOf(o.toString()) : null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getOrDefault(Object obj, T defaultObj) {
		if (obj == null) return defaultObj;
		if (obj instanceof Long || defaultObj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	@SuppressWarnings("unchecked")
	public static <T> T requireNonNull(Object obj) {
		if (obj == null) throw new NullPointerException("Object is null");
		if (obj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	public static <T> T resolveOrDefault(Object obj, Function<Object, T> resolver, T defaultObj) {
		if (obj == null) return defaultObj;
		return resolver.apply(obj);
	}
}
