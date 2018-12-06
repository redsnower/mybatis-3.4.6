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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 *
 * 对对象的包装，抽象了对象的属性信息。定义了一系列查
 * 询对象属性信息的方法，以及更新属性的方法。
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 如果Object Wrapper 中封装的是普通的Bean 对象，则调用相应属性的相应getter 方法，
   *  如果封装的是集合类，则获取指定key 或下标对应的value 位
   * @param prop
   * @return
   */
  Object get(PropertyTokenizer prop);

  /**
   * 如果ObjectWrapper 中封装的是普通的Bean 对象， 则调用相应属性的相应setter 方法，
   * 如果封装的是集合类，则设置指定key 或下标对应的value 值
   * @param prop
   * @param value
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 查找属性表达式指定的属性，第二个参数表示是否忽略属性表达式中的下划线
   * @param name
   * @param useCamelCaseMapping
   * @return
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 查找可读属性的名称集合
   * @return
   */
  String[] getGetterNames();

  /**
   * 查找可写属性的名称集合
   * @return
   */
  String[] getSetterNames();

  Class<?> getSetterType(String name);

  Class<?> getGetterType(String name);

  boolean hasSetter(String name);

  boolean hasGetter(String name);

  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);
  
  boolean isCollection();
  
  void add(Object element);
  
  <E> void addAll(List<E> element);

}
