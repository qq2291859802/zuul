/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.monitoring.MonitoringHelper;
import com.netflix.zuul.monitoring.Tracer;
import com.netflix.zuul.monitoring.TracerFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * Base abstract class for ZuulFilters. The base class defines abstract methods to define:
 * filterType() - to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
 * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
 * We also support a "static" type for static responses see  StaticResponseFilter.
 * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
 * <p/>
 * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
 * important for a filter. filterOrders do not need to be sequential.
 * <p/>
 * ZuulFilters may be disabled using Archius Properties.
 * <p/>
 * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
 *
 *
 *
 * zuul过滤器
 *
 * @author Mikey Cohen
 *         Date: 10/26/11
 *         Time: 4:29 PM
 */
public abstract class ZuulFilter implements IZuulFilter, Comparable<ZuulFilter> {

    private final AtomicReference<DynamicBooleanProperty> filterDisabledRef = new AtomicReference<>();

    /**
     *
     * 过滤器类型：支持pre,post,route,error
     *
     * to classify a filter by type. Standard types in Zuul are "pre" for pre-routing filtering,
     * "route" for routing to an origin, "post" for post-routing filters, "error" for error handling.
     * We also support a "static" type for static responses see  StaticResponseFilter.
     * Any filterType made be created or added and run by calling FilterProcessor.runFilters(type)
     *
     * @return A String representing that type
     */
    abstract public String filterType();

    /**
     *
     * 过滤器的优先级
     *
     * filterOrder() must also be defined for a filter. Filters may have the same  filterOrder if precedence is not
     * important for a filter. filterOrders do not need to be sequential.
     *
     * @return the int order of a filter
     */
    abstract public int filterOrder();

    /**
     *
     *
     *
     * By default ZuulFilters are static; they don't carry state. This may be overridden by overriding the isStaticFilter() property to false
     *
     * @return true by default
     */
    public boolean isStaticFilter() {
        return true;
    }

    /**
     *
     * 过滤器失效的属性名：zuul.[classname].[filtertype].disable
     *
     * The name of the Archaius property to disable this filter. by default it is zuul.[classname].[filtertype].disable
     *
     * @return
     */
    public String disablePropertyName() {
        return "zuul." + this.getClass().getSimpleName() + "." + filterType() + ".disable";
    }

    /**
     *
     * 判断过滤器是不是失效
     *
     *
     * If true, the filter has been disabled by archaius and will not be run
     *
     * @return
     */
    public boolean isFilterDisabled() {

        filterDisabledRef.compareAndSet(null, DynamicPropertyFactory.getInstance().getBooleanProperty(disablePropertyName(), false));
        return filterDisabledRef.get().get();
    }

    /**
     * 执行过滤器
     *
     * runFilter checks !isFilterDisabled() and shouldFilter(). The run() method is invoked if both are true.
     *
     * @return the return from ZuulFilterResult
     */
    public ZuulFilterResult runFilter() {
        ZuulFilterResult zr = new ZuulFilterResult();
        if (!isFilterDisabled()) {
            // 过滤器有效
            if (shouldFilter()) {
                // 应该执行该过滤器

                // 开始记录轨迹
                Tracer t = TracerFactory.instance().startMicroTracer("ZUUL::" + this.getClass().getSimpleName());
                try {
                    // 运行过滤器并返回结果
                    Object res = run();
                    // 封装成Zuul过滤器结果对象(成功)
                    zr = new ZuulFilterResult(res, ExecutionStatus.SUCCESS);
                } catch (Throwable e) {
                    // 设置轨迹名称
                    t.setName("ZUUL::" + this.getClass().getSimpleName() + " failed");

                    // 封装成Zuul过滤器结果对象(异常)
                    zr = new ZuulFilterResult(ExecutionStatus.FAILED);
                    zr.setException(e);
                } finally {
                    // 停止轨迹记录
                    t.stopAndLog();
                }
            } else {
                // 如果不执行就表示跳过
                zr = new ZuulFilterResult(ExecutionStatus.SKIPPED);
            }
        }
        return zr;
    }

    /**
     * 根据filterOrder排序
     * @param filter
     * @return
     */
    public int compareTo(ZuulFilter filter) {
        return Integer.compare(this.filterOrder(), filter.filterOrder());
    }

    public static class TestUnit {

        static Field field = null;
        static {
            try {
                field = ZuulFilter.class.getDeclaredField("filterDisabledRef");
                field.setAccessible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Mock
        private ZuulFilter f1;
        @Mock
        private ZuulFilter f2;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
            MonitoringHelper.initMocks();
        }

        @Test
        public void testSort() {

            when(f1.filterOrder()).thenReturn(1);
            when(f2.filterOrder()).thenReturn(10);
            when(f1.compareTo(any(ZuulFilter.class))).thenCallRealMethod();
            when(f2.compareTo(any(ZuulFilter.class))).thenCallRealMethod();

            ArrayList<ZuulFilter> list = new ArrayList<ZuulFilter>();
            list.add(f2);
            list.add(f1);

            Collections.sort(list);

            assertSame(f1, list.get(0));
        }

        @Test
        public void testShouldFilter() {
            class TestZuulFilter extends ZuulFilter {

                @Override
                public String filterType() {
                    return null;
                }

                @Override
                public int filterOrder() {
                    return 0;
                }

                public boolean shouldFilter() {
                    return false;
                }

                public Object run() {
                    return null;
                }
            }

            TestZuulFilter tf1 = spy(new TestZuulFilter());
            TestZuulFilter tf2 = spy(new TestZuulFilter());

            when(tf1.shouldFilter()).thenReturn(true);
            when(tf2.shouldFilter()).thenReturn(false);

            try {
                tf1.runFilter();
                tf2.runFilter();
                verify(tf1, times(1)).run();
                verify(tf2, times(0)).run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }

        }

        @Test
        public void testIsFilterDisabled() {
            class TestZuulFilter extends ZuulFilter {

                @Override
                public String filterType() {
                    return null;
                }

                @Override
                public int filterOrder() {
                    return 0;
                }

                public boolean isFilterDisabled() {
                    return false;
                }

                public boolean shouldFilter() {
                    return true;
                }

                public Object run() {
                    return null;
                }
            }

            TestZuulFilter tf1 = spy(new TestZuulFilter());
            TestZuulFilter tf2 = spy(new TestZuulFilter());

            when(tf1.isFilterDisabled()).thenReturn(false);
            when(tf2.isFilterDisabled()).thenReturn(true);

            try {
                tf1.runFilter();
                tf2.runFilter();
                verify(tf1, times(1)).run();
                verify(tf2, times(0)).run();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }

        }

        @Test
        public void testDisabledPropNameOnInit() throws Exception {
            class TestZuulFilter extends ZuulFilter {

                final String filterType;

                public TestZuulFilter(String filterType) {
                    this.filterType = filterType;
                }

                @Override
                public boolean shouldFilter() {
                    return false;
                }

                @Override
                public Object run() {
                    return null;
                }

                @Override
                public String filterType() {
                    return filterType;
                }

                @Override
                public int filterOrder() {
                    return 0;
                }
            }

            TestZuulFilter filter = new TestZuulFilter("pre");
            assertFalse(filter.isFilterDisabled());

            @SuppressWarnings("unchecked")
            AtomicReference<DynamicBooleanProperty> filterDisabledRef = (AtomicReference<DynamicBooleanProperty>) field.get(filter);
            String filterName = filterDisabledRef.get().getName();
            assertEquals("zuul.TestZuulFilter.pre.disable", filterName);
        }

    }
}
