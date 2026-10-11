package com.ruoyi.framework.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.utils.PageUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableSupportTest
{
    @AfterEach
    void clearRequest()
    {
        PageUtils.clearPage();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void defaultsAndMalformedParametersRemainBounded()
    {
        assertPage(null, null, 1, 10);
        assertPage("invalid", "invalid", 1, 10);
        assertPage("-1", "0", 1, 10);
        assertPage("0", "-1", 1, 10);
    }

    @Test
    void oversizedRequestsCannotDisablePagination()
    {
        assertPage("2", "101", 2, 100);
        assertPage("2", "2147483647", 2, 100);
        assertPage("2", "999999999999999", 2, 10);
    }

    @Test
    void stableSortPreservesDefaultAndAddsUniqueKeyToRequestedSort()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        PageUtils.startPage("create_time desc", "user_id");
        assertEquals("create_time desc, user_id", PageHelper.getLocalPage().getOrderBy());
        PageUtils.clearPage();
        request.addParameter("orderByColumn", "nickName");
        request.addParameter("isAsc", "descending");
        PageUtils.startPage("create_time desc", "user_id");
        assertEquals("nick_name desc, user_id", PageHelper.getLocalPage().getOrderBy());
    }

    @Test
    void unsafeClientSortIsRejectedBeforePageHelperReceivesIt()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("orderByColumn", "nickName;drop table sys_user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.UtilException.class,
                () -> PageUtils.startPage("", "user_id"));
    }

    @Test
    void validPageParametersArePreserved()
    {
        assertPage("3", "1", 3, 1);
        assertPage("3", "100", 3, 100);
    }

    private void assertPage(String pageNum, String pageSize, int expectedNum, int expectedSize)
    {
        PageUtils.clearPage();
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (pageNum != null)
        {
            request.addParameter("pageNum", pageNum);
        }
        if (pageSize != null)
        {
            request.addParameter("pageSize", pageSize);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        PageUtils.startPage();
        assertEquals(expectedNum, PageHelper.getLocalPage().getPageNum());
        assertEquals(expectedSize, PageHelper.getLocalPage().getPageSize());
    }
}
