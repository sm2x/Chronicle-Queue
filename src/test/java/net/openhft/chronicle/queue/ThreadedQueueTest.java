/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.queue;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.core.threads.ThreadDump;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.queue.impl.single.StoreComponentReferenceHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static net.openhft.chronicle.queue.RollCycles.TEST_DAILY;
import static org.junit.Assert.*;

/**
 * @author Rob Austin.
 */
public class ThreadedQueueTest {

    public static final int REQUIRED_COUNT = 10;
    private ThreadDump threadDump;

    @Before
    public void threadDump() {
        threadDump = new ThreadDump();
        threadDump.ignore(StoreComponentReferenceHandler.THREAD_NAME);
        threadDump.ignore(SingleChronicleQueue.DISK_SPACE_CHECKER_NAME);
    }

    @After
    public void checkThreadDump() {
        threadDump.assertNoNewThreads();
    }

    @Test(timeout = 10000)
    public void testMultipleThreads() throws java.io.IOException, InterruptedException, ExecutionException, TimeoutException {

        final File path = DirectoryUtils.tempDir("testMultipleThreads");

        final AtomicInteger counter = new AtomicInteger();

        ExecutorService tailerES = Executors.newSingleThreadExecutor(/*new NamedThreadFactory("tailer", true)*/);
        Future tf = tailerES.submit(() -> {
            try {
                final ChronicleQueue rqueue = SingleChronicleQueueBuilder.binary(path)
                        .testBlockSize()
                        .build();

                final ExcerptTailer tailer = rqueue.createTailer();
                final Bytes bytes = Bytes.elasticByteBuffer();

                while (counter.get() < REQUIRED_COUNT && !Thread.interrupted()) {
                    bytes.clear();
                    if (tailer.readBytes(bytes))
                        counter.incrementAndGet();
                }

                bytes.release();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        ExecutorService appenderES = Executors.newSingleThreadExecutor(/*new NamedThreadFactory("appender", true)*/);
        Future af = appenderES.submit(() -> {
            try {
                final ChronicleQueue wqueue = SingleChronicleQueueBuilder.binary(path)
                        .testBlockSize()
                        .build();

                final ExcerptAppender appender = wqueue.acquireAppender();

                final Bytes message = Bytes.elasticByteBuffer();
                for (int i = 0; i < REQUIRED_COUNT; i++) {
                    message.clear();
                    message.append(i);
                    appender.writeBytes(message);
                }
                message.release();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        appenderES.shutdown();
        tailerES.shutdown();

        long end = System.currentTimeMillis() + 9000;
        af.get(9000, TimeUnit.MILLISECONDS);
        tf.get(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        assertEquals(REQUIRED_COUNT, counter.get());
    }

    @Test//(timeout = 5000)
    public void testTailerReadingEmptyQueue() throws java.io.IOException {

        final File path = DirectoryUtils.tempDir("testTailerReadingEmptyQueue");

        final ChronicleQueue rqueue = SingleChronicleQueueBuilder.fieldlessBinary(path)
                .testBlockSize()
                .rollCycle(TEST_DAILY)
                .build();

        final ExcerptTailer tailer = rqueue.createTailer();

        final ChronicleQueue wqueue = SingleChronicleQueueBuilder.fieldlessBinary(path)
                .testBlockSize()
                .rollCycle(TEST_DAILY)
                .build();

        Bytes bytes = Bytes.elasticByteBuffer();
        assertFalse(tailer.readBytes(bytes));

        final ExcerptAppender appender = wqueue.acquireAppender();
        appender.writeBytes(Bytes.wrapForRead("Hello World".getBytes(ISO_8859_1)));

        bytes.clear();
        assertTrue(tailer.readBytes(bytes));
        assertEquals("Hello World", bytes.toString());

        bytes.release();
    }

    @After
    public void checkRegisteredBytes() {
        BytesUtil.checkRegisteredBytes();
    }
}
