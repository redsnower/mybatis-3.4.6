/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * 在 mybatis-config.xml中<properties> 节点下配置是否开启默认值功能的对应配置
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * 配置占位符与默认值之间的默认分隔符的对应配置项
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  // 默认情况下，关闭默认值的功能
  private static final String ENABLE_DEFAULT_VALUE = "false";
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    // 创建 GenericTokenParser 对象，并指定其处理的占位符格式为"${}"
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  /**
   * 首先会按照 defaultValueSeparator 字段指定的分隔符对着整个占位符切分，得到占位符的名称和默认值，
   * 然后按照切分得到的占位符名称查找对应的值，如果在<properties>节点下未定义相应的键值对，则将切分得到的默认值作为解析结果返回
   */
  private static class VariableTokenHandler implements TokenHandler {
    private final Properties variables;
    private final boolean enableDefaultValue;
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

	  /**
	   * 1、按照defaultValueSeparator 字段指定的分隔符对整个占位符切分，得到占位符的名称和默认值
	   * 2、按照切分的占位符名称查找对应的值，如果在<properties>节点下未定义对应的键值对，则将切分得到的默认值作为解析结果返回
       *
       * eg:  ${usemame:root｝
	   * @param content
	   * @return
	   */
    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        if (enableDefaultValue) {
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex);
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}";
    }
  }

}
