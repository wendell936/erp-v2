package com.ruoyi.common.utils;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.utils.sql.SqlUtil;

/**
 * 分页工具类
 * 
 * @author ruoyi
 */
public class PageUtils extends PageHelper
{
    /**
     * 设置请求分页数据
     */
    public static void startPage()
    {
        startPageInternal(null, null);
    }

    /** 默认排序和唯一列由服务端指定，客户端排序后仍追加唯一列。 */
    public static void startPage(String defaultOrderBy, String uniqueOrderBy)
    {
        if (StringUtils.isEmpty(uniqueOrderBy))
        {
            throw new IllegalArgumentException("稳定分页必须指定服务端唯一排序列");
        }
        startPageInternal(defaultOrderBy, uniqueOrderBy);
    }

    private static void startPageInternal(String defaultOrderBy, String uniqueOrderBy)
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = pageDomain.getOrderBy();
        if (StringUtils.isNotEmpty(uniqueOrderBy))
        {
            if (StringUtils.isEmpty(orderBy))
            {
                orderBy = defaultOrderBy;
            }
            orderBy = StringUtils.isEmpty(orderBy) ? uniqueOrderBy
                    : orderBy + ", " + uniqueOrderBy;
        }
        orderBy = SqlUtil.escapeOrderBySql(orderBy);
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage()
    {
        PageHelper.clearPage();
    }
}
