/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * 缓存了反射操作需要使用的类的元数据信息
 *    addGetMethods
 * @author Clinton Begin
 */
public class Reflector {

  private final Class<?> type;  // 对应的Class 类型
  // 可读属性的名称集合，可读属性就是存在相应getter 方法的属性，初始值为空数纽
  private final String[] readablePropertyNames;
  //可写属性的名称集合，可写属性就是存在相应setter 方法的属性，初始值为空数纽
  private final String[] writeablePropertyNames;
  // 记录了属性相应的setter 方法， key 是属性名称， value 是Invoker 对象，它是对setter 方法对应
  // Method 对象的封装
  private final Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
  // 属性相应的getter 方法集合， key 是属性名称， value 也是Invoker 对象
  private final Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
  // 记录了属性相应的setter 方法的参数值类型， key 是属性名称， value 是setter 方法的参数类型
  private final Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
  // 记录了属性相应的getter 方法的返回位类型， key 是属性名称， value 是getter 方法的返回位类型
  private final Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
  // 记录了默认构造方法
  private Constructor<?> defaultConstructor;

  //所有属性名称的集合
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

  public Reflector(Class<?> clazz) {
    // 初始化type 字段
    type = clazz;
    // 查找clazz 的默认构造方法（无参构造方法） ， 具体实现是通过反射遍历所有构造方法
    addDefaultConstructor(clazz);
    // 处理clazz 中的getter 方法，填充getMethods 集合和getTypes 集合
    addGetMethods(clazz);
    // 处理clazz 中的setter 方法，填充setMethods 集合和setTypes 集合
    addSetMethods(clazz);
    // 处理没有getter/setter 方法的字段
    addFields(clazz);
    // 根据getMethods/setMethods 集合，初始化可读／ 写属性的名称集合
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    // 初始化caseinsensitivePropertyMap 集合，其中记录了所有大写格式的属性名称
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {
        if (canAccessPrivateMethods()) {
          try {
            constructor.setAccessible(true);
          } catch (Exception e) {
            // Ignored. This is only a final precaution, nothing we can do.
          }
        }
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor;
        }
      }
    }
  }

  private void addGetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        name = PropertyNamer.methodToProperty(name);
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    resolveGetterConflicts(conflictingGetters);
  }

  // 如果子类覆盖了父类中的getter方法，在getClassMethods() 中会产生两个签名不同的方法，如
  // java.util.List#getNames  java.util.ArrayList#getNames
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          //当前最适合方法的返回值是当前方法返回值的子类，什么都不做，
        } else if (winnerType.isAssignableFrom(candidateType)) {
          //当前方法的返回值是当前最适合方法的返回值的子类，交换
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      addGetMethod(propName, winner);
    }
  }

  private void addGetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      getMethods.put(name, new MethodInvoker(method));
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.get(name);
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list);
    }
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance((Class<?>) componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (canAccessPrivateMethods()) {
        try {
          field.setAccessible(true);
        } catch (Exception e) {
          // Ignored. This is only a final precaution, nothing we can do.
        }
      }
      if (field.isAccessible()) {
        if (!setMethods.containsKey(field.getName())) {
          // issue #379 - removed the check for final because JDK 1.5 allows
          // modification of final fields through reflection (JSR-133). (JGB)
          // pr #16 - final static can only be set by the classloader
          int modifiers = field.getModifiers();
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
            addSetField(field);
          }
        }
        if (!getMethods.containsKey(field.getName())) {
          addGetField(field);
        }
      }
    }
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   * 获取当前类及其父类中定义的所有方法的唯一签名及其对应的Method对象
   * @param cls The class
   * @return An array containing all methods in this class
   *
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null && currentClass != Object.class) {
      // 获取 currentClass 类中定义的全部方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 检测是否在子类中已经添加过该方法，如果在子类中已经添加过，则表示子类覆盖了该方法，无需再添加到 uniqueMethods
        if (!uniqueMethods.containsKey(signature)) {
          if (canAccessPrivateMethods()) {
            try {
              currentMethod.setAccessible(true);
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }

          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   *
   * @param method
   * @return 返回值类型#方法名称:参数   eg:  java.lang.String#getSignature:java.lang.reflect.Method
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  private static boolean canAccessPrivateMethods() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
