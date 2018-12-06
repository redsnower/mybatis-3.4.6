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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * javaType 和 jdbcType 之间的类型转换器
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /**
   * 在通过PreparedStatement为SQL语句绑定参数时，如何把Java类型的参数转换为对应的JDBC类型
   * @param ps 当前的PreparedStatement对象
   * @param i 当前参数的位置
   * @param parameter 当前参数的Java对象
   * @param jdbcType 当前参数的数据库类型
   * @throws SQLException
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * 从ResultSet 获取数据结果集时如何把数据库类型转换为对应的Java类型
   * @param rs 数据结果集
   * @param columnName 当前字段名称
   * @return 转换后的Java对象
   * @throws SQLException
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   * 用于Mybatis在调用存储过程后把数据库类型的数据转换为对应的Java类型
   * @param cs 当前的CallableStatement执行后的CallableStatement
   * @param columnIndex
   * @return
   * @throws SQLException
   */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
