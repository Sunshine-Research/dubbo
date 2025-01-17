/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.container;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.COMMON_THREAD_INTERRUPTED_EXCEPTION;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_START_DUBBO_ERROR;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_STOP_DUBBO_ERROR;

/**
 * Dubbo容器的主启动程序
 */
public class Main {

    public static final String CONTAINER_KEY = "dubbo.container";

    public static final String SHUTDOWN_HOOK_KEY = "dubbo.shutdown.hook";

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(Main.class);

    private static final ExtensionLoader<Container> LOADER = ExtensionLoader.getExtensionLoader(Container.class);

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) {
        try {
			// 如果没有传入配置，则使用默认的配置
            if (ArrayUtils.isEmpty(args)) {
                String config = System.getProperty(CONTAINER_KEY, LOADER.getDefaultExtensionName());
                args = COMMA_SPLIT_PATTERN.split(config);
            }

            final List<Container> containers = new ArrayList<Container>();
            for (int i = 0; i < args.length; i++) {
                containers.add(LOADER.getExtension(args[i]));
            }
            logger.info("Use container type(" + Arrays.toString(args) + ") to run dubbo serivce.");
			// 如果使用JVM关闭的hook方法
            if ("true".equals(System.getProperty(SHUTDOWN_HOOK_KEY))) {
				// 则添加Dubbo的服务停止hook方法
                Runtime.getRuntime().addShutdownHook(new Thread("dubbo-container-shutdown-hook") {
                    @Override
                    public void run() {
						// 遍历容器，进行关闭
                        for (Container container : containers) {
                            try {
                                container.stop();
                                logger.info("Dubbo " + container.getClass().getSimpleName() + " stopped!");
                            } catch (Throwable t) {
                                logger.error(CONFIG_STOP_DUBBO_ERROR, "", "", t.getMessage(), t);
                            }
                            try {
                                LOCK.lock();
                                STOP.signal();
                            } finally {
                                LOCK.unlock();
                            }
                        }
                    }
                });
            }
			// 遍历容器，进行启动
            for (Container container : containers) {
                container.start();
                logger.info("Dubbo " + container.getClass().getSimpleName() + " started!");
            }
            System.out.println(new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]").format(new Date())
                    + " Dubbo service server started!");
        } catch (RuntimeException e) {
            logger.error(CONFIG_START_DUBBO_ERROR, "", "", e.getMessage(), e);
            System.exit(1);
        }
        try {
            LOCK.lock();
            STOP.await();
        } catch (InterruptedException e) {
            logger.warn(
                    COMMON_THREAD_INTERRUPTED_EXCEPTION,
                    "",
                    "",
                    "Dubbo service server stopped, interrupted by other thread!",
                    e);
        } finally {
            LOCK.unlock();
        }
    }

}
