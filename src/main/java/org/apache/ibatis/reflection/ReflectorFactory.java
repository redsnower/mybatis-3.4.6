/**
 *    Copyright 2009-2015 the original author or authors.
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

/**
 * 实现了对Reflector 对象的创建和缓存
 *
 * 除了使用MyBatis 提供的DefaultReflectorFactory 实现，我们还可以在mybatis-config .xml
 * 中配置自定义的ReflectorFactory 实现类，从而实现功能上的扩展
 */
public interface ReflectorFactory {

  /**
   * 检测该ReflectorFactory 对象是否会缓存Reflector 对象
   * @return
   */
  boolean isClassCacheEnabled();

  /**
   * ／／设置是否缓存Reflector 对象
   * @param classCacheEnabled
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 创建指定Class 对应的Reflector 对象
   * @param type
   * @return
   */
  Reflector findForClass(Class<?> type);
}