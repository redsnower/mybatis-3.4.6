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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 对象属性表达式的解析
 * @author Clinton Begin
 *
 * getValue
 */
public class MetaObject {

  private final Object originalObject;  // 原始Javabean 对象
  private final ObjectWrapper objectWrapper; // objectWrapper 对象，其中封装了originalObject对象
  private final ObjectFactory objectFactory; // 负责实例化originalObject 的工厂对象，
  private final ObjectWrapperFactory objectWrapperFactory; // 负责实例化 objectWrapper的工厂对象
  private final ReflectorFactory reflectorFactory; // 用于创建并缓存 reflector 对象的工厂对象

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;

    if (object instanceof ObjectWrapper) { // 若原始对象已经是ObjectWrapper 对象，则直接使用
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      // 若ObjectWrapperFactory 能够为该原始对象创建对应的ObjectWrapper 对象，则由优先使用
      //objectWrapperFactory ，而DefaultObjectWrapperFactory.hasWrapperFor （）始终
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      // 若原始对象为Map 类型， 则创建MapWrapper 对象
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      // ／／若原始对象是Collection 类型，则创建CollectionWrapper 对象
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  // MetaObject 的构造方法是private 修改的，只能通过forObject() 这个静态方法创建MetaObject 对象
  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
	return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  public Object getValue(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);   // 解析属性表达式
    if (prop.hasNext()) { // 处理子表达式
      // 根据PropertyTokenizer 解析后指定的属性，创建相应的MetaObject 对象
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());  // 递归处理子表达式
      }
    } else {
      return objectWrapper.get(prop);  // 通过objectWrapper 获取指定的属性值
    }
  }

  public void setValue(String name, Object value) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null && prop.getChildren() != null) {
          // don't instantiate child path if value is null
          return;
        } else {
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      metaValue.setValue(prop.getChildren(), value);
    } else {
      objectWrapper.set(prop, value);
    }
  }

  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
