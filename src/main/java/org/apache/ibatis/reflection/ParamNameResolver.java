/**
 *    Copyright 2009-2017 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 处理Mapper 接口中定义的方法的参数
 * 列表
 */
public class ParamNameResolver {

  private static final String GENERIC_NAME_PREFIX = "param";

  /**
   *
   * 记录了参数在参
   * 数列表中的位置索引与参数名称之间的对应关系，其中key 表示参数在参数列表中的索引位置，
   * value 表示参数名称，参数名称可以通过＠Param 注解指定，如果没有指定＠Param 注解，则使
   * 用参数索引作为其名称。如果参数列表中包含RowBounds 类型或ResultHandler 类型的参数，
   * 则这两种类型的参数并不会被记录到name 集合中，这就会导致参数的索引与名称不一致，
   *
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;  //  记录对应方法的参数列表中是否使用了＠Param 注解。

  public ParamNameResolver(Configuration config, Method method) {
    // 获取参数列表中每个参数的类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取参数列表上的注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    // 该集合用于记录参数索引与参数名称的对应关系
    final SortedMap<Integer, String> map = new TreeMap<Integer, String>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters  如果参数是RowBounds 类型或ResultHandler 类型，则跳过对该参数的分析
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          // @Param 注解出现过一次，就将hasParamAnnotation 初始化为true
          hasParamAnnotation = true;
          name = ((Param) annotation).value();  // 获取@Param注解指定的参数名称
          break;
        }
      }

      // 若参数没有对应的@Param注解，则根据配置决定是否使用参数实际名称作为其名称
      if (name == null) {
        // @Param was not specified.
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)  使用参数的索引作为其名称
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    if (Jdk.parameterExists) {
      return ParamNameUtil.getParamNames(method).get(paramIndex);
    }
    return null;
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.<br />
   * Multiple parameters are named using the naming rule.<br />
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      //  未使用@Param且只有一个参数
      return args[names.firstKey()];
    } else {
      //  处理使用@Param注解指定了参数名称或有多个参数的情况

      // param 这个Map 中记录了参数名称与实参之间的对应关系。ParamMap 继承了HashMap ，如果向ParamMap 中添加已经存在的key ，会报错，其他行为与HashMap 相同
      final Map<String, Object> param = new ParamMap<Object>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // 将参数名与实参对应关系记录到param中
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)  为参数创建” param＋索引”格式的默认参数名称，例如： param1 , param2 等，并添加
        //／ 到param集合中
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
