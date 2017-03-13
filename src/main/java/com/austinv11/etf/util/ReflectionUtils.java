package com.austinv11.etf.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal class for reflection utilities.
 */
public class ReflectionUtils {
	
	public static sun.misc.Unsafe UNSAFE;
	
	static {
		try {
			Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
		} catch (Throwable e) {
			UNSAFE = null; //Unsafe unavailable
		}
	}
	
	public static List<PropertyManager> findProperties(Object instance, Class clazz) {
		List<PropertyManager> properties = new ArrayList<>();
		for (Field field : clazz.getFields()) {
			if (!Modifier.isTransient(field.getModifiers())) {
				properties.add(new PropertyManager(instance, field));
			}
		}
		return properties;
	}
	
	public static <T> T createInstance(Class<T> clazz) {
		if (UNSAFE != null) { //Unsafe available, use it to instantiate the class
			try {
				return (T) UNSAFE.allocateInstance(clazz);
			} catch (InstantiationException e) {}
		}
		
		//Fallback to reflection
		try {
			return clazz.getConstructor().newInstance();
		} catch (Exception e) {
			throw new ETFException(e);
		}
	}
	
	public interface IPropertyAccessor {
		
		Object get();
	}
	
	public interface IPropertyMutator {
		
		void set(Object o);
	}
	
	public static class FieldAccessorAndMutator implements IPropertyAccessor, IPropertyMutator {
		
		private final Object object;
		private final Field field;
		
		public FieldAccessorAndMutator(Object object, Field field) {
			this.object = object;
			this.field = field;
			this.field.setAccessible(true);
		}
		
		@Override
		public Object get() {
			try {
				return field.get(object);
			} catch (IllegalAccessException e) {
				throw new ETFException(e);
			}
		}
		
		@Override
		public void set(Object o) {
			try {
				field.set(object, o);
			} catch (IllegalAccessException e) {
				throw new ETFException(e);
			}
		}
	}
	
	public static class MethodAccessorAndMutator implements IPropertyAccessor, IPropertyMutator {
		
		private final Object object;
		private final Method method;
		
		public MethodAccessorAndMutator(Object object, Method method) {
			this.object = object;
			this.method = method;
			this.method.setAccessible(true);
		}
		
		@Override
		public Object get() {
			try {
				return method.invoke(object);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new ETFException(e);
			}
		}
		
		@Override
		public void set(Object o) {
			try {
				method.invoke(object, o);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new ETFException(e);
			}
		}
	}
	
	public static class NOPAccessorAndMutator implements IPropertyAccessor, IPropertyMutator {
		
		public static final NOPAccessorAndMutator INSTANCE = new NOPAccessorAndMutator();
		
		@Override
		public Object get() {
			return null;
		}
		
		@Override
		public void set(Object o) {}
	}
	
	public static class PropertyManager {
		
		private final Object instance;
		private final IPropertyMutator mutator;
		private final IPropertyAccessor accessor;
		private final String name;
		
		private static String capitalize(String s) {
			return s.substring(0, 1).toUpperCase() + (s.length() > 1 ? s.substring(1) : "");
		}
		
		public PropertyManager(Object instance, Field field) {
			this.instance = instance;
			field.setAccessible(true);
			IPropertyAccessor accessor = null;
			boolean isFinal = Modifier.isFinal(field.getModifiers());
			IPropertyMutator mutator = isFinal ? NOPAccessorAndMutator.INSTANCE : null;
			for (Method m : instance.getClass().getMethods()) {
				if (mutator != null && accessor != null)
					break;
				
				m.setAccessible(true);
				
				if (m.getName().equals(String.format("get%s", capitalize(field.getName())))) {
					accessor = new MethodAccessorAndMutator(instance, m);
					continue;
				} else if (!isFinal
						&& m.getName().equals(String.format("set%s", capitalize(field.getName())))
						&& m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(field.getType())) {
					mutator = new MethodAccessorAndMutator(instance, m);
					continue;
				} else if (m.getDeclaredAnnotation(GetterMethod.class) != null) {
					if (m.getDeclaredAnnotation(GetterMethod.class).value().equals(field.getName())) {
						accessor = new MethodAccessorAndMutator(instance, m);
						continue;
					}
				} else if (!isFinal && m.getDeclaredAnnotation(SetterMethod.class) != null
						&& m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(field.getType())) {
					if (m.getDeclaredAnnotation(SetterMethod.class).value().equals(field.getName())) {
						mutator = new MethodAccessorAndMutator(instance, m);
						continue;
					}
				}
			}
			
			if (accessor == null)
				accessor = new FieldAccessorAndMutator(instance, field);
			
			if (mutator == null)
				mutator = new FieldAccessorAndMutator(instance, field);
			
			this.accessor = accessor;
			this.mutator = mutator;
			
			this.name = field.getName();
		}
		
		public void setValue(Object value) {
			mutator.set(value);
		}
		
		public Object getValue() {
			return accessor.get();
		}
		
		public String getName() {
			return name;
		}
	}
}