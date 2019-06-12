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

package com.netflix.zuul.monitoring;

/**
 * Abstraction layer to provide time-based monitoring.
 *
 * 提供基于时间的监控（打点的形式）
 *
 * @author mhawthorne
 */
public abstract class TracerFactory {

    private static TracerFactory INSTANCE;

    /**
     * sets a TracerFactory Implementation
     *
     * @param f a <code>TracerFactory</code> value
     */
    public static final void initialize(TracerFactory f) {
        INSTANCE = f;
    }


    /**
     * Returns the singleton TracerFactory 
     *
     * @return a <code>TracerFactory</code> value
     */
    public static final TracerFactory instance() {
        if(INSTANCE == null) throw new IllegalStateException(String.format("%s not initialized", TracerFactory.class.getSimpleName()));
        return INSTANCE;
    }

    /**
     * 核心方法：开始记录轨迹信息
     * @param name
     * @return
     */
    public abstract Tracer startMicroTracer(String name);

}
