package com.moe365.mopi.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Utility for reflections, most used for implementing Externalizable.
 * @author mailmindlin
 */
public class ReflectionUtils {
	protected static final Field modifiersField;

	static {
		Field tmp = null;
		try {
			tmp = Field.class.getDeclaredField("modifiers");
			tmp.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		modifiersField = tmp;
	}
	@FunctionalInterface
	protected static interface FieldOperator {
		void apply(Field field) throws IllegalAccessException;
	}

	/**
	 * Set a double field on an object, even if the field is null. If the field
	 * is final, then the updated value might not always work, because of how
	 * final fields are handled by the JIT compiler.
	 * 
	 * @param obj
	 *            the object to set the field on
	 * @param fieldName
	 *            name of the field
	 * @param value
	 *            value to set the field to
	 * @throws NoSuchFieldException
	 *             if the field specified is not there
	 * @throws IllegalArgumentException
	 *             If the field specified is not a double field
	 * @throws IllegalAccessException
	 *             If the SecurityManager blocks us from setting the field
	 */
	public static void setDouble(Object obj, String fieldName, double value)
			throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		applyOnField(obj.getClass(), fieldName, field->field.setDouble(obj, value));
	}
	public static void setInt(Object obj, String fieldName, int value)
			throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		applyOnField(obj.getClass(), fieldName, field->field.setInt(obj, value));
	}
	public static void setFloat(Object obj, String fieldName, float value)
			throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		applyOnField(obj.getClass(), fieldName, field->field.setFloat(obj, value));
	}
	public static void setLong(Object obj, String fieldName, long value)
			throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		applyOnField(obj.getClass(), fieldName, field->field.setLong(obj, value));
	}
	public static void applyOnField(Class<?> clazz, String fieldName, FieldOperator action) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
		Field field;
		try {
			field = clazz.getField(fieldName);
		} catch (NoSuchFieldException e) {
			try {
				field = clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e1) {
				throw e1;
			}
		}
		final boolean wasAccessible = field.isAccessible();
		final int modifiers = field.getModifiers();

		try {
			field.setAccessible(true);
			modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
			action.apply(field);
		} finally {
			if (field.isAccessible() ^ wasAccessible)
				field.setAccessible(wasAccessible);
			if (field.getModifiers() != modifiers)
				modifiersField.setInt(field, modifiers);
		}
	}
}
