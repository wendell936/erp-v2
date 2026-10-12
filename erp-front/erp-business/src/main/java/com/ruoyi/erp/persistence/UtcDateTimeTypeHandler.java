package com.ruoyi.erp.persistence;

import java.sql.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** 仅用于明确存储 UTC 的 ERP DATETIME，兼容既有 Date 接口且不依赖 JVM 默认时区。 */
public class UtcDateTimeTypeHandler extends BaseTypeHandler<Date>
{
    private Calendar utc()
    {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Date value, JdbcType jdbcType)
            throws SQLException
    {
        statement.setTimestamp(index, new Timestamp(value.getTime()), utc());
    }

    @Override
    public Date getNullableResult(ResultSet result, String column) throws SQLException
    {
        return result.getTimestamp(column, utc());
    }

    @Override
    public Date getNullableResult(ResultSet result, int column) throws SQLException
    {
        return result.getTimestamp(column, utc());
    }

    @Override
    public Date getNullableResult(CallableStatement statement, int column) throws SQLException
    {
        return statement.getTimestamp(column, utc());
    }
}
