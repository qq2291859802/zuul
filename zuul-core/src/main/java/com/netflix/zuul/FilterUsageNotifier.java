package com.netflix.zuul;

/**
 * Interface to implement for registering a callback for each time a filter
 * is used.
 *
 * 过滤器执行结果通知（监听器模式）
 *
 * User: michaels
 * Date: 5/13/14
 * Time: 9:55 PM
 */
public interface FilterUsageNotifier {
    public void notify(ZuulFilter filter, ExecutionStatus status);
}
